package com.fitness.squatcounter.data

import com.fitness.squatcounter.data.db.LedgerEntryEntity
import com.fitness.squatcounter.data.db.SessionEntity
import com.fitness.squatcounter.data.repository.CreditRepository
import com.fitness.squatcounter.data.repository.TimeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CreditLedgerTest {

    private lateinit var testTimeZone: TimeZone
    private lateinit var repository: CreditRepository

    // Fake in-memory storage to simulate DB behavior without Android device context
    private val sessions = mutableMapOf<String, SessionEntity>()
    private val ledgerEntries = mutableListOf<LedgerEntryEntity>()
    private var simulatedTime = MutableTimeProvider()

    class MutableTimeProvider(
        var wallClock: Long = 1_700_000_000_000L,
        var elapsed: Long = 100_000L,
        var boot: Int = 1
    ) : TimeProvider {
        override fun currentTimeMillis(): Long = wallClock
        override fun elapsedRealtime(): Long = elapsed
        override fun bootCount(): Int = boot
    }

    @Before
    fun setUp() {
        testTimeZone = TimeZone.getTimeZone("UTC")
        sessions.clear()
        ledgerEntries.clear()
        simulatedTime = MutableTimeProvider(
            wallClock = 1_700_000_000_000L,
            elapsed = 100_000L,
            boot = 1
        )

        // Mock database operations directly for deterministic JVM testing
        repository = CreditRepository(
            database = createMockDatabase(),
            timeProvider = simulatedTime,
            config = CreditConfig(
                rewardRateSecondsPerRep = 30,
                dailyTotalEarnCapSeconds = 1800,
                dailySquatCapSeconds = 1800
            ),
            timeZone = testTimeZone
        )
    }

    private fun createMockDatabase(): com.fitness.squatcounter.data.db.RepLockDatabase {
        // Since we test repository domain algorithms, we use dynamic proxy or simple subclass
        // For pure unit tests of the algorithms:
        val mockDb = object : com.fitness.squatcounter.data.db.RepLockDatabase() {
            override fun sessionDao(): com.fitness.squatcounter.data.db.SessionDao = object : com.fitness.squatcounter.data.db.SessionDao {
                override suspend fun insertSession(session: SessionEntity): Long {
                    if (!sessions.containsKey(session.sessionId)) {
                        sessions[session.sessionId] = session
                        return 1L
                    }
                    return -1L
                }

                override suspend fun getSession(sessionId: String): SessionEntity? = sessions[sessionId]

                override suspend fun updateSessionStatus(sessionId: String, status: SessionStatus, endTimeMs: Long) {
                    sessions[sessionId]?.let {
                        sessions[sessionId] = it.copy(status = status, endTimeMs = endTimeMs)
                    }
                }

                override suspend fun incrementValidReps(sessionId: String) {
                    sessions[sessionId]?.let {
                        sessions[sessionId] = it.copy(validReps = it.validReps + 1)
                    }
                }

                override suspend fun incrementRejectedReps(sessionId: String) {
                    sessions[sessionId]?.let {
                        sessions[sessionId] = it.copy(rejectedReps = it.rejectedReps + 1)
                    }
                }

                override suspend fun getAllSessions(): List<SessionEntity> = sessions.values.toList()
            }

            override fun ledgerDao(): com.fitness.squatcounter.data.db.LedgerDao = object : com.fitness.squatcounter.data.db.LedgerDao {
                override suspend fun insertEntry(entry: LedgerEntryEntity): Long {
                    // Enforce unique (sessionId, repIndex)
                    if (entry.repIndex != null && ledgerEntries.any { it.sessionId == entry.sessionId && it.repIndex == entry.repIndex }) {
                        throw Exception("SQLiteConstraintException: UNIQUE constraint failed: ledger_entries.sessionId, ledger_entries.repIndex")
                    }
                    val withId = entry.copy(id = (ledgerEntries.size + 1).toLong())
                    ledgerEntries.add(withId)
                    return withId.id
                }

                override suspend fun getAllEntries(): List<LedgerEntryEntity> = ledgerEntries.toList()

                override fun getAllEntriesFlow(): kotlinx.coroutines.flow.Flow<List<LedgerEntryEntity>> =
                    kotlinx.coroutines.flow.flowOf(ledgerEntries.toList())

                override suspend fun getLatestEntry(): LedgerEntryEntity? = ledgerEntries.lastOrNull()

                override suspend fun getEarnedSecondsBetween(startOfDayMs: Long, endOfDayMs: Long): Int {
                    return ledgerEntries
                        .filter { it.entryType == LedgerEntryType.EARN && it.wallClockTimeMs in startOfDayMs..endOfDayMs }
                        .sumOf { it.amountSeconds }
                }

                override suspend fun getEarnedSecondsForExerciseBetween(startOfDayMs: Long, endOfDayMs: Long, exerciseType: String): Int {
                    return ledgerEntries
                        .filter { it.entryType == LedgerEntryType.EARN && it.exerciseType == exerciseType && it.wallClockTimeMs in startOfDayMs..endOfDayMs }
                        .sumOf { it.amountSeconds }
                }

                override suspend fun getEntriesForSession(sessionId: String): List<LedgerEntryEntity> {
                    return ledgerEntries.filter { it.sessionId == sessionId }
                }
            }

            override fun clearAllTables() {
                sessions.clear()
                ledgerEntries.clear()
            }

            override fun createInvalidationTracker(): androidx.room.InvalidationTracker {
                return androidx.room.InvalidationTracker(this, "sessions", "ledger_entries")
            }

            override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): androidx.sqlite.db.SupportSQLiteOpenHelper {
                throw UnsupportedOperationException("Not needed in unit test")
            }
        }
        return mockDb
    }

    @Test
    fun testCapsEnforcedDaily() = kotlinx.coroutines.runBlocking {
        val sessionId = "session_caps_1"
        repository.startSession(sessionId, "squats")

        // 60 valid reps * 30 seconds = 1800 seconds (exact cap)
        for (i in 1..60) {
            val awarded = repository.recordValidatedRep(sessionId, i, "squats")
            assertEquals(30, awarded)
        }

        assertEquals(1800, repository.getBalance())

        // 61st rep on the same day should award 0 seconds (cap reached)
        val rep61Award = repository.recordValidatedRep(sessionId, 61, "squats")
        assertEquals(0, rep61Award)
        assertEquals(1800, repository.getBalance())
    }

    @Test
    fun testCreditsExpireAtEndOfNextDay() {
        val cal = Calendar.getInstance(testTimeZone).apply {
            set(2026, Calendar.OCTOBER, 1, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val day1Time = cal.timeInMillis

        val earnEntry = LedgerEntryEntity(
            id = 1,
            sessionId = "session_expiry",
            repIndex = 1,
            entryType = LedgerEntryType.EARN,
            amountSeconds = 30,
            wallClockTimeMs = day1Time,
            elapsedRealtimeMs = 1000L,
            bootCount = 1
        )

        // End of next day is Oct 2, 23:59:59.999
        val endOfNextDay = repository.getEndOfNextDayMs(day1Time)

        // Check during Day 1
        val duringDay1 = day1Time + 3600_000L
        assertEquals(30, repository.deriveBalance(listOf(earnEntry), duringDay1))

        // Check during Day 2 (e.g. 5:00 PM)
        cal.set(2026, Calendar.OCTOBER, 2, 17, 0, 0)
        assertEquals(30, repository.deriveBalance(listOf(earnEntry), cal.timeInMillis))

        // Check at 23:59:59 on Day 2
        assertEquals(30, repository.deriveBalance(listOf(earnEntry), endOfNextDay))

        // Check at Day 3 00:00:00 (1 millisecond after end of next day) -> EXPIRED!
        val day3Start = endOfNextDay + 1
        assertEquals(0, repository.deriveBalance(listOf(earnEntry), day3Start))
    }

    @Test
    fun testFifoSpendingAndPartialExpiry() {
        val cal = Calendar.getInstance(testTimeZone).apply {
            set(2026, Calendar.OCTOBER, 1, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val day1Time = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val day2Time = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val day3Time = cal.timeInMillis

        val entries = listOf(
            // Day 1: Earn 100 seconds
            LedgerEntryEntity(1, "s1", 1, LedgerEntryType.EARN, 100, "squats", day1Time, 1000L, 1),
            // Day 2: Earn 50 seconds
            LedgerEntryEntity(2, "s2", 1, LedgerEntryType.EARN, 50, "squats", day2Time, 2000L, 1),
            // Day 2: Spend 60 seconds (FIFO consumes 60s from Day 1, leaving 40s on Day 1 + 50s on Day 2 = 90s)
            LedgerEntryEntity(3, "s_spend", null, LedgerEntryType.SPEND, 60, null, day2Time + 1000L, 3000L, 1)
        )

        // On Day 2, balance should be 100 + 50 - 60 = 90s
        assertEquals(90, repository.deriveBalance(entries, day2Time + 2000L))

        // On Day 3 (after Day 1 has expired): Day 1's remaining 40s expires, but Day 2's 50s is still unexpired
        assertEquals(50, repository.deriveBalance(entries, day3Time))
    }

    @Test
    fun testDuplicateSessionRepIdempotency() = kotlinx.coroutines.runBlocking {
        val sessionId = "session_idempotent"
        repository.startSession(sessionId, "squats")

        // First attempt for rep 1 awards 30s
        val firstAward = repository.recordValidatedRep(sessionId, 1, "squats")
        assertEquals(30, firstAward)
        assertEquals(30, repository.getBalance())

        // Duplicate attempt for the same rep 1 in the same session must fail safely and return 0
        val duplicateAward = repository.recordValidatedRep(sessionId, 1, "squats")
        assertEquals(0, duplicateAward)
        assertEquals(30, repository.getBalance()) // Balance unchanged
    }

    @Test
    fun testCrashMidSessionPreservesCommittedReps() = kotlinx.coroutines.runBlocking {
        val sessionId = "session_crash_test"
        repository.startSession(sessionId, "squats")

        // 3 reps committed in individual transactions
        repository.recordValidatedRep(sessionId, 1, "squats")
        repository.recordValidatedRep(sessionId, 2, "squats")
        repository.recordValidatedRep(sessionId, 3, "squats")

        // Simulate app kill/crash: session was left in INTERRUPTED status
        val session = sessions[sessionId]!!
        assertEquals(SessionStatus.INTERRUPTED, session.status)
        assertEquals(3, session.validReps)

        // On next launch: derived balance is strictly based on the 3 committed reps
        assertEquals(90, repository.getBalance())
    }

    @Test
    fun testClockGoingBackwardsIgnored() = kotlinx.coroutines.runBlocking {
        val sessionId = "session_time_travel"
        repository.startSession(sessionId, "squats")

        // Rep 1 at wallClock = 1_000_000L, elapsed = 10_000L
        simulatedTime.wallClock = 1_000_000L
        simulatedTime.elapsed = 10_000L
        repository.recordValidatedRep(sessionId, 1, "squats")

        // User rolls wall-clock backwards to 500_000L (half a million ms earlier)
        // But hardware elapsedRealtime advances by 5_000L to 15_000L
        simulatedTime.wallClock = 500_000L
        simulatedTime.elapsed = 15_000L

        val snapshot = repository.getEffectiveTimeSnapshot()

        // Effective wall clock must NOT go backwards!
        // Expected: 1_000_000 + 5_000 = 1_005_000L
        assertEquals(1_005_000L, snapshot.wallClockTimeMs)
        assertTrue(snapshot.wallClockTimeMs > 1_000_000L)
    }
}
