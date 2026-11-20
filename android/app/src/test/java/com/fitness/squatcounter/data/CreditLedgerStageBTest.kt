package com.fitness.squatcounter.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fitness.squatcounter.data.db.RepLockDatabase
import com.fitness.squatcounter.data.repository.CreditRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

/** Stage B: personalised pay per rep (override) keeps the caps and the safety rules. */
@RunWith(RobolectricTestRunner::class)
class CreditLedgerStageBTest {

    private class FixedTimeProvider(var wallMs: Long) : TimeProvider {
        override fun currentTimeMillis(): Long = wallMs
        override fun elapsedRealtime(): Long = 0L
        override fun bootCount(): Int = 1
    }

    private val day = 24 * 3_600_000L
    private val t0 = 20_000L * day + 3_600_000L

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

    private fun repo(config: CreditConfig = CreditConfig()) = CreditRepository(
        database = db,
        timeProvider = FixedTimeProvider(t0),
        config = config,
        timeZone = TimeZone.getTimeZone("UTC")
    )

    @Test
    fun overrideReplacesDefaultRate() = runBlocking {
        val r = repo()
        r.startSession("s", "squats")
        assertEquals(45, r.recordValidatedRep("s", 1, "squats", 45))
        assertEquals(30, r.recordValidatedRep("s", 2, "squats")) // no override: default 30
        assertEquals(75, r.getBalance())
    }

    @Test
    fun overrideIsStillLimitedByExerciseCap() = runBlocking {
        val r = repo(CreditConfig(dailySquatCapSeconds = 100))
        r.startSession("s", "squats")
        assertEquals(60, r.recordValidatedRep("s", 1, "squats", 60))
        assertEquals(40, r.recordValidatedRep("s", 2, "squats", 60)) // only 40 left of the cap
        assertEquals(0, r.recordValidatedRep("s", 3, "squats", 60))
    }

    @Test
    fun overrideIsStillLimitedByTotalCap() = runBlocking {
        val r = repo(CreditConfig(dailyTotalEarnCapSeconds = 50))
        r.startSession("s", "squats")
        assertEquals(50, r.recordValidatedRep("s", 1, "squats", 60))
        assertEquals(0, r.recordValidatedRep("s", 2, "squats", 60))
    }

    @Test
    fun overrideDoesNotMakeUnknownExerciseEarn() = runBlocking {
        val r = repo()
        r.startSession("s", "burpees")
        assertEquals(0, r.recordValidatedRep("s", 1, "burpees", 60))
        assertEquals(0, r.getBalance())
    }

    @Test
    fun overrideStaysIdempotentPerRep() = runBlocking {
        val r = repo()
        r.startSession("s", "pushups")
        assertEquals(20, r.recordValidatedRep("s", 1, "pushups", 20))
        assertEquals(0, r.recordValidatedRep("s", 1, "pushups", 20))
        assertEquals(20, r.getBalance())
    }

    @Test
    fun personalRateFromBaselineFeedsTheLedger() = runBlocking {
        // A user who reports a max of 10 push-ups earns 600 / 10 = 60 s per rep.
        val rate = RewardCalculator.secondsPerUnit(isHold = false, baselineMax = 10)
        val r = repo()
        r.startSession("s", "pushups")
        assertEquals(rate, r.recordValidatedRep("s", 1, "pushups", rate))
        assertEquals(60, rate)
    }
}