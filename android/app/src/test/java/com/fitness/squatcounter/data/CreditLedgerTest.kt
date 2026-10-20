package com.fitness.squatcounter.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fitness.squatcounter.data.db.LedgerEntryEntity
import com.fitness.squatcounter.data.db.RepLockDatabase
import com.fitness.squatcounter.data.repository.CreditRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class CreditLedgerTest {

    private lateinit var database: RepLockDatabase
    private lateinit var repository: CreditRepository
    private lateinit var testTimeZone: TimeZone
    private lateinit var simulatedTime: MutableTimeProvider

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
        val context = ApplicationProvider.getApplicationContext<Context>()
        testTimeZone = TimeZone.getTimeZone("UTC")

        // Real Room in-memory database
        database = Room.inMemoryDatabaseBuilder(context, RepLockDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        simulatedTime = MutableTimeProvider(
            wallClock = 1_700_000_000_000L,
            elapsed = 100_000L,
            boot = 1
        )

        repository = CreditRepository(
            database = database,
            timeProvider = simulatedTime,
            config = CreditConfig(
                rewardRateSecondsPerRep = 30,
                dailyTotalEarnCapSeconds = 1800,
                dailySquatCapSeconds = 1800
            ),
            timeZone = testTimeZone
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testCapsEnforcedDaily() = runBlocking {
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
    fun testDuplicateSessionRepIdempotency() = runBlocking {
        val sessionId = "session_real_idempotency"
        repository.startSession(sessionId, "squats")

        // First attempt for rep 1 awards 30s
        val firstAward = repository.recordValidatedRep(sessionId, 1, "squats")
        assertEquals(30, firstAward)
        assertEquals(30, repository.getBalance())

        // Duplicate attempt for the same rep 1 in the same session hits real Room unique index
        val duplicateAward = repository.recordValidatedRep(sessionId, 1, "squats")
        assertEquals(0, duplicateAward)
        assertEquals(30, repository.getBalance()) // Balance strictly unchanged
    }

    @Test
    fun testCrashMidSessionPreservesCommittedReps() = runBlocking {
        val sessionId = "session_crash_real_db"
        repository.startSession(sessionId, "squats")

        // 3 reps committed in individual transactions
        repository.recordValidatedRep(sessionId, 1, "squats")
        repository.recordValidatedRep(sessionId, 2, "squats")
        repository.recordValidatedRep(sessionId, 3, "squats")

        // Simulate app kill/crash: session was left in INTERRUPTED status
        val session = database.sessionDao().getSession(sessionId)
        assertNotNull(session)
        assertEquals(SessionStatus.INTERRUPTED, session!!.status)
        assertEquals(3, session.validReps)

        // On next launch: derived balance in real Room DB is strictly 3 * 30 = 90s
        assertEquals(90, repository.getBalance())
    }

    @Test
    fun testClockGoingBackwardsIgnored() = runBlocking {
        val sessionId = "session_time_travel_real_db"
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

    @Test
    fun testSessionSpanningMidnight() = runBlocking {
        val sessionId = "session_midnight"
        repository.startSession(sessionId, "squats")

        // Day 1 at 23:55 (5 minutes before midnight)
        val cal = Calendar.getInstance(testTimeZone).apply {
            set(2026, Calendar.OCTOBER, 1, 23, 55, 0)
            set(Calendar.MILLISECOND, 0)
        }
        simulatedTime.wallClock = cal.timeInMillis
        simulatedTime.elapsed = 10_000L

        // Earn 60 reps on Day 1 (hits Day 1 cap: 1800s)
        for (i in 1..60) {
            val awarded = repository.recordValidatedRep(sessionId, i, "squats")
            assertEquals(30, awarded)
        }
        assertEquals(1800, repository.getBalance())

        // 61st rep on Day 1 awards 0s (cap reached on Day 1)
        val capCheckDay1 = repository.recordValidatedRep(sessionId, 61, "squats")
        assertEquals(0, capCheckDay1)

        // Advance time to Day 2 at 00:05 (10 minutes later, crossing midnight into Day 2)
        cal.add(Calendar.MINUTE, 10)
        simulatedTime.wallClock = cal.timeInMillis
        simulatedTime.elapsed = 610_000L // 10 minutes elapsed

        // Now on Day 2, within the SAME session, new reps can earn up to Day 2's cap!
        val rep62Award = repository.recordValidatedRep(sessionId, 62, "squats")
        assertEquals(30, rep62Award)
        // Total balance is now Day 1 (1800s) + Day 2 (30s) = 1830s
        assertEquals(1830, repository.getBalance())
    }

    @Test
    fun testWithinSameBootWallClockIsIgnoredAndElapsedRealtimeUsed() = runBlocking {
        val sessionId = "session_boot_monotonic"
        repository.startSession(sessionId, "squats")

        // Entry 1 recorded at wallClock = 1_000_000L, elapsed = 10_000L, boot = 1
        simulatedTime.wallClock = 1_000_000L
        simulatedTime.elapsed = 10_000L
        repository.recordValidatedRep(sessionId, 1, "squats")

        // 1. User rolls wall-clock backwards to 500_000L, elapsed moves forward by 2_000L to 12_000L
        simulatedTime.wallClock = 500_000L
        simulatedTime.elapsed = 12_000L
        val backwardSnapshot = repository.getEffectiveTimeSnapshot()
        // Backward wall clock completely ignored! Effective time = 1_000_000 + 2_000 = 1_002_000L
        assertEquals(1_002_000L, backwardSnapshot.wallClockTimeMs)

        // 2. User jumps wall-clock FORWARD by 5 hours to 18_000_000L, elapsed moves forward by 1_000L to 13_000L
        simulatedTime.wallClock = 18_000_000L
        simulatedTime.elapsed = 13_000L
        val forwardSnapshot = repository.getEffectiveTimeSnapshot()
        // Forward wall clock also completely ignored! Effective time = 1_000_000 + 3_000 = 1_003_000L
        assertEquals(1_003_000L, forwardSnapshot.wallClockTimeMs)
    }

    @Test
    fun testDayBoundariesDstSafe() {
        val nyTimeZone = TimeZone.getTimeZone("America/New_York")
        val dstRepo = CreditRepository(
            database = database,
            timeProvider = simulatedTime,
            config = CreditConfig(),
            timeZone = nyTimeZone
        )

        // Spring forward 2026: March 8, 2026 (23-hour day in New York)
        val cal = Calendar.getInstance(nyTimeZone).apply {
            set(2026, Calendar.MARCH, 8, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val march8Noon = cal.timeInMillis
        val march8End = dstRepo.getEndOfDayMs(march8Noon)

        // End of day is 23:59:59.999
        cal.timeInMillis = march8End
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, cal.get(Calendar.MINUTE))
        assertEquals(59, cal.get(Calendar.SECOND))

        // Expiration end of next day is March 9, 23:59:59.999
        val endOfNextDay = dstRepo.getEndOfNextDayMs(march8Noon)
        cal.timeInMillis = endOfNextDay
        assertEquals(Calendar.MARCH, cal.get(Calendar.MONTH))
        assertEquals(9, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun testStaleInterruptedSessionCannotBeCreditedAgain() = runBlocking {
        val sessionId = "stale_session_1"
        repository.startSession(sessionId, "squats")
        repository.recordValidatedRep(sessionId, 1, "squats")

        // App closes stale session on restart
        repository.closeStaleOpenSessions()

        val closedSession = database.sessionDao().getSession(sessionId)
        assertNotNull(closedSession!!.endTimeMs)
        assertEquals(SessionStatus.INTERRUPTED, closedSession.status)

        // Attempting to credit a rep on this stale closed session must award 0
        val award = repository.recordValidatedRep(sessionId, 2, "squats")
        assertEquals(0, award)
        // Balance remains 30s from rep 1
        assertEquals(30, repository.getBalance())
    }
}
