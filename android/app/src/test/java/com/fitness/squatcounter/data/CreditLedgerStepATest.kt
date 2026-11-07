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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

/**
 * Step A tests: expiry-aware spending and per-exercise rewards/caps.
 * Uses UTC and a fixed clock so results never depend on the machine running the tests.
 */
@RunWith(RobolectricTestRunner::class)
class CreditLedgerStepATest {

    private class FixedTimeProvider(
        var wallMs: Long,
        var elapsedMs: Long = 0L,
        var boot: Int = 1
    ) : TimeProvider {
        override fun currentTimeMillis(): Long = wallMs
        override fun elapsedRealtime(): Long = elapsedMs
        override fun bootCount(): Int = boot
    }

    private val hour = 3_600_000L
    private val day = 24 * hour

    // Epoch day 20000, which is exactly 00:00 UTC.
    private val t0 = 20_000L * day

    private lateinit var db: RepLockDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RepLockDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun repo(config: CreditConfig = CreditConfig()): CreditRepository =
        CreditRepository(
            database = db,
            timeProvider = FixedTimeProvider(wallMs = t0 + hour),
            config = config,
            timeZone = TimeZone.getTimeZone("UTC")
        )

    private fun entry(
        id: Long,
        type: LedgerEntryType,
        amount: Int,
        wallMs: Long
    ) = LedgerEntryEntity(
        id = id,
        sessionId = "s$id",
        repIndex = if (type == LedgerEntryType.EARN) 1 else null,
        entryType = type,
        amountSeconds = amount,
        exerciseType = if (type == LedgerEntryType.EARN) "squats" else null,
        wallClockTimeMs = wallMs,
        elapsedRealtimeMs = 0L,
        bootCount = 1
    )

    @Test
    fun spendDoesNotConsumeAlreadyExpiredCredits() {
        // Day 0: earn 100 (expires end of day 1).
        // Day 3: earn 50, then spend 30. The expired 100 must not pay for the spend.
        val entries = listOf(
            entry(1, LedgerEntryType.EARN, 100, t0 + hour),
            entry(2, LedgerEntryType.EARN, 50, t0 + 3 * day + hour),
            entry(3, LedgerEntryType.SPEND, 30, t0 + 3 * day + 2 * hour)
        )

        val balance = repo().deriveBalance(entries, t0 + 3 * day + 3 * hour)

        assertEquals(20, balance)
    }

    @Test
    fun spendStillConsumesOldestValidCreditsFirst() {
        // Both credits are still valid at spend time. Oldest is used first.
        val entries = listOf(
            entry(1, LedgerEntryType.EARN, 100, t0 + hour),
            entry(2, LedgerEntryType.EARN, 50, t0 + day + hour),
            entry(3, LedgerEntryType.SPEND, 120, t0 + day + 2 * hour)
        )

        val balance = repo().deriveBalance(entries, t0 + day + 3 * hour)

        assertEquals(30, balance)
    }

    @Test
    fun pushUpsAreNotLimitedBySquatCap() {
        runBlocking {
            // Squat cap is only 60 s, total cap is large.
            val repo = repo(
                CreditConfig(
                    dailyTotalEarnCapSeconds = 5000,
                    dailySquatCapSeconds = 60
                )
            )
            repo.startSession("squat-session", "squats")
            repo.startSession("pushup-session", "pushups")

            assertEquals(30, repo.recordValidatedRep("squat-session", 1, "squats"))
            assertEquals(30, repo.recordValidatedRep("squat-session", 2, "squats"))
            // Squat cap reached
            assertEquals(0, repo.recordValidatedRep("squat-session", 3, "squats"))
            // Push-ups use their own rate (60 s) and their own cap
            assertEquals(60, repo.recordValidatedRep("pushup-session", 1, "pushups"))
        }
    }

    @Test
    fun unknownExerciseEarnsNothing() {
        runBlocking {
            val repo = repo()
            repo.startSession("mystery-session", "jumping_jacks")

            assertEquals(0, repo.recordValidatedRep("mystery-session", 1, "jumping_jacks"))
        }
    }

    @Test
    fun configReturnsRewardPerExercise() {
        val config = CreditConfig()

        val squats = config.rewardFor("squats")
        assertNotNull(squats)
        assertEquals(30, squats!!.secondsPerRep)
        assertEquals(1800, squats.dailyCapSeconds)

        val pushUps = config.rewardFor("pushups")
        assertNotNull(pushUps)
        assertEquals(60, pushUps!!.secondsPerRep)
        assertEquals(1800, pushUps.dailyCapSeconds)

        assertNull(config.rewardFor("burpees"))
    }
}