package com.fitness.squatcounter.data.repository

import androidx.room.withTransaction
import com.fitness.squatcounter.data.CreditConfig
import com.fitness.squatcounter.data.LedgerEntryType
import com.fitness.squatcounter.data.SessionStatus
import com.fitness.squatcounter.data.TimeProvider
import com.fitness.squatcounter.data.db.LedgerEntryEntity
import com.fitness.squatcounter.data.db.RepLockDatabase
import com.fitness.squatcounter.data.db.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.TimeZone

class CreditRepository(
    private val database: RepLockDatabase,
    private val timeProvider: TimeProvider,
    val config: CreditConfig = CreditConfig(),
    private val timeZone: TimeZone = TimeZone.getDefault()
) {
    private val sessionDao = database.sessionDao()
    private val ledgerDao = database.ledgerDao()

    /**
     * Observable flow of the derived balance.
     * Emits new balance whenever the ledger table changes.
     */
    val balanceFlow: Flow<Int> = ledgerDao.getAllEntriesFlow().map { entries ->
        val effectiveNow = getEffectiveTimeSnapshot().wallClockTimeMs
        deriveBalance(entries, effectiveNow)
    }

    /**
     * Resolves the current time, guaranteeing that backward wall-clock jumps are ignored.
     */
    suspend fun getEffectiveTimeSnapshot(): TimeSnapshot {
        val rawWallClock = timeProvider.currentTimeMillis()
        val elapsed = timeProvider.elapsedRealtime()
        val bootCount = timeProvider.bootCount()

        val lastEntry = ledgerDao.getLatestEntry()
        val effectiveWallClock = if (lastEntry != null) {
            if (bootCount == lastEntry.bootCount) {
                // Within the same boot, hardware elapsedRealtime is the monotonic ground truth.
                // Raw wall clock is completely ignored to prevent both forward and backward time manipulation.
                val elapsedDelta = maxOf(0L, elapsed - lastEntry.elapsedRealtimeMs)
                lastEntry.wallClockTimeMs + elapsedDelta
            } else {
                // Across reboots, clamp to at least the last recorded entry time
                maxOf(rawWallClock, lastEntry.wallClockTimeMs)
            }
        } else {
            rawWallClock
        }

        return TimeSnapshot(
            wallClockTimeMs = effectiveWallClock,
            elapsedRealtimeMs = elapsed,
            bootCount = bootCount
        )
    }

    /**
     * Starts a new workout session in INTERRUPTED status.
     * Idempotent: does not overwrite if session already exists.
     */
    suspend fun startSession(sessionId: String, exerciseType: String = "squats") {
        val time = getEffectiveTimeSnapshot()
        sessionDao.insertSession(
            SessionEntity(
                sessionId = sessionId,
                exerciseType = exerciseType,
                startTimeMs = time.wallClockTimeMs,
                status = SessionStatus.INTERRUPTED
            )
        )
    }

    /**
     * Marks session as completed.
     */
    suspend fun completeSession(sessionId: String) {
        val time = getEffectiveTimeSnapshot()
        sessionDao.updateSessionStatus(sessionId, SessionStatus.COMPLETED, time.wallClockTimeMs)
    }

    /**
     * Records a validated rep in a single Room transaction.
     * Enforces daily earn caps and idempotency.
     * Returns the earned credits awarded (0 if cap reached or already credited).
     */
    suspend fun recordValidatedRep(
        sessionId: String,
        repIndex: Int,
        exerciseType: String = "squats"
    ): Int {
        return database.withTransaction {
            val session = sessionDao.getSession(sessionId) ?: return@withTransaction 0
            // A stale, closed, or completed session can never be credited again
            if (session.status == SessionStatus.COMPLETED || session.endTimeMs != null) {
                return@withTransaction 0
            }

            val time = getEffectiveTimeSnapshot()

            val (startOfDayMs, endOfDayMs) = getDayBounds(time.wallClockTimeMs)

            // Calculate current daily earnings
            val totalEarnedToday = ledgerDao.getEarnedSecondsBetween(startOfDayMs, endOfDayMs)
            val exerciseEarnedToday = ledgerDao.getEarnedSecondsForExerciseBetween(startOfDayMs, endOfDayMs, exerciseType)

            val remainingTotalCap = maxOf(0, config.dailyTotalEarnCapSeconds - totalEarnedToday)
            val remainingExerciseCap = maxOf(0, config.dailySquatCapSeconds - exerciseEarnedToday)

            val awardSeconds = minOf(config.rewardRateSecondsPerRep, remainingTotalCap, remainingExerciseCap)

            if (awardSeconds > 0) {
                val entry = LedgerEntryEntity(
                    sessionId = sessionId,
                    repIndex = repIndex,
                    entryType = LedgerEntryType.EARN,
                    amountSeconds = awardSeconds,
                    exerciseType = exerciseType,
                    wallClockTimeMs = time.wallClockTimeMs,
                    elapsedRealtimeMs = time.elapsedRealtimeMs,
                    bootCount = time.bootCount
                )
                try {
                    ledgerDao.insertEntry(entry)
                    sessionDao.incrementValidReps(sessionId)
                } catch (e: Exception) {
                    // Unique constraint on (sessionId, repIndex) tripped -> already credited (idempotent)
                    return@withTransaction 0
                }
            } else {
                sessionDao.incrementValidReps(sessionId)
            }

            awardSeconds
        }
    }

    /**
     * Records a rejected rep in the session for audit.
     */
    suspend fun recordRejectedRep(sessionId: String) {
        sessionDao.incrementRejectedReps(sessionId)
    }

    /**
     * Spends credits from the balance.
     * Returns true if sufficient balance was available and spent, false otherwise.
     */
    suspend fun spendCredits(amountSeconds: Int, reason: String = "usage"): Boolean {
        require(amountSeconds > 0) { "Spend amount must be positive" }

        return database.withTransaction {
            val currentBalance = getBalance()
            if (currentBalance < amountSeconds) {
                return@withTransaction false
            }

            val time = getEffectiveTimeSnapshot()
            val entry = LedgerEntryEntity(
                sessionId = "SPEND_${time.wallClockTimeMs}",
                repIndex = null,
                entryType = LedgerEntryType.SPEND,
                amountSeconds = amountSeconds,
                exerciseType = null,
                wallClockTimeMs = time.wallClockTimeMs,
                elapsedRealtimeMs = time.elapsedRealtimeMs,
                bootCount = time.bootCount
            )
            // Ensure pseudo-session exists for foreign key
            sessionDao.insertSession(
                SessionEntity(
                    sessionId = entry.sessionId,
                    exerciseType = "system_spend",
                    startTimeMs = time.wallClockTimeMs,
                    status = SessionStatus.COMPLETED
                )
            )
            ledgerDao.insertEntry(entry)
            true
        }
    }

    /**
     * Derives current balance directly from the ledger.
     */
    suspend fun getBalance(): Int {
        val entries = ledgerDao.getAllEntries()
        val effectiveNow = getEffectiveTimeSnapshot().wallClockTimeMs
        return deriveBalance(entries, effectiveNow)
    }

    /**
     * Derives balance from ledger entries with lazy expiration at end of next day.
     */
    fun deriveBalance(entries: List<LedgerEntryEntity>, currentTimeMs: Long): Int {
        // Track unspent earned credits: list of Pair(expiryTimestampMs, remainingAmount)
        val unspentCredits = mutableListOf<Pair<Long, Int>>()
        var netAdjust = 0

        for (entry in entries) {
            when (entry.entryType) {
                LedgerEntryType.EARN -> {
                    val expiryMs = getEndOfNextDayMs(entry.wallClockTimeMs)
                    unspentCredits.add(Pair(expiryMs, entry.amountSeconds))
                }
                LedgerEntryType.SPEND -> {
                    var spendRemaining = entry.amountSeconds
                    val iterator = unspentCredits.listIterator()
                    while (iterator.hasNext() && spendRemaining > 0) {
                        val (expiry, amount) = iterator.next()
                        if (amount <= spendRemaining) {
                            spendRemaining -= amount
                            iterator.remove()
                        } else {
                            iterator.set(Pair(expiry, amount - spendRemaining))
                            spendRemaining = 0
                        }
                    }
                }
                LedgerEntryType.EXPIRE -> {
                    // Handled lazily by expiry check below
                }
                LedgerEntryType.ADJUST -> {
                    netAdjust += entry.amountSeconds
                }
            }
        }

        // Filter out expired credits lazily
        val activeCredits = unspentCredits
            .filter { (expiry, _) -> expiry >= currentTimeMs }
            .sumOf { (_, amount) -> amount }

        return maxOf(0, activeCredits + netAdjust)
    }

    /**
     * Closes any stale open sessions as INTERRUPTED (e.g. on app start).
     * Ensures they can never be credited again.
     */
    suspend fun closeStaleOpenSessions(excludeSessionId: String = ""): Int {
        val time = getEffectiveTimeSnapshot()
        return sessionDao.closeStaleSessions(
            endTimeMs = time.wallClockTimeMs,
            excludeSessionId = excludeSessionId
        )
    }

    /**
     * Canonical day derivation function: computes the 00:00:00 start of day in milliseconds.
     */
    fun getStartOfDayMs(wallClockMs: Long): Long {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = wallClockMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getEndOfDayMs(wallClockMs: Long): Long {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = getStartOfDayMs(wallClockMs)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis - 1L
    }

    /**
     * Calculates the expiration timestamp for credits earned at wallClockMs:
     * "Credits expire at end of the next day" -> 23:59:59.999 of day D+1.
     * DST-safe Calendar arithmetic: start of day D + 2 calendar days - 1 ms.
     */
    fun getEndOfNextDayMs(wallClockMs: Long): Long {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = getStartOfDayMs(wallClockMs)
            add(Calendar.DAY_OF_YEAR, 2)
        }
        return cal.timeInMillis - 1L
    }

    private fun getDayBounds(wallClockMs: Long): Pair<Long, Long> {
        val startOfDay = getStartOfDayMs(wallClockMs)
        val endOfDay = getEndOfDayMs(wallClockMs)
        return Pair(startOfDay, endOfDay)
    }
}

data class TimeSnapshot(
    val wallClockTimeMs: Long,
    val elapsedRealtimeMs: Long,
    val bootCount: Int
)
