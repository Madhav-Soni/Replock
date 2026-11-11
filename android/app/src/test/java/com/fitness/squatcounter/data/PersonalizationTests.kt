package com.fitness.squatcounter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineStoreTest {

    private class FakeStorage(var data: BaselineData = BaselineData()) : BaselineStorage {
        override fun load(): BaselineData = data
        override fun save(data: BaselineData) {
            this.data = data
        }
    }

    private val pushups = CreditConfig.EXERCISE_PUSHUPS

    private fun store(coolingOffMs: Long = 1_000L) =
        BaselineStore(FakeStorage(), BaselineDefaults.rules, coolingOffMs)

    @Test
    fun unsetExerciseUsesTheDefault() {
        val s = store()

        assertEquals(10, s.maxFor(pushups, 0L))
        assertFalse(s.isSet(pushups, 0L))
    }

    @Test
    fun firstTimeValueAppliesImmediatelyEvenIfLowerThanDefault() {
        val s = store()

        s.setMax(pushups, 6, 0L)

        assertEquals(6, s.maxFor(pushups, 0L))
        assertTrue(s.isSet(pushups, 0L))
    }

    @Test
    fun valueBelowTheFloorIsRaisedToTheFloor() {
        val s = store()

        s.setMax(pushups, 1, 0L) // floor for push-ups is 3

        assertEquals(3, s.maxFor(pushups, 0L))
    }

    @Test
    fun valueAboveTheCeilingIsLowered() {
        val s = store()

        s.setMax(pushups, 9999, 0L) // ceiling is 300

        assertEquals(300, s.maxFor(pushups, 0L))
    }

    @Test
    fun raisingAppliesImmediately() {
        val s = store()
        s.setMax(pushups, 12, 0L)

        s.setMax(pushups, 20, 10L)

        assertEquals(20, s.maxFor(pushups, 10L))
    }

    @Test
    fun loweringWaitsForTheCoolingOff() {
        val s = store(coolingOffMs = 1_000L)
        s.setMax(pushups, 20, 0L)

        s.setMax(pushups, 8, 100L) // asked at t=100, due at t=1100

        assertEquals(20, s.maxFor(pushups, 100L))
        assertEquals(20, s.maxFor(pushups, 1_099L))
        assertEquals(8, s.maxFor(pushups, 1_100L))
    }

    @Test
    fun loweringIsImmediateWhenTheCoolingOffIsZero() {
        val s = store(coolingOffMs = 0L)
        s.setMax(pushups, 20, 0L)

        s.setMax(pushups, 8, 100L)

        assertEquals(8, s.maxFor(pushups, 100L))
    }

    @Test
    fun aVerifiedCountAboveTheMaxRaisesIt() {
        val s = store()
        s.setMax(pushups, 10, 0L)

        val raised = s.raiseFromPerformance(pushups, 15, 5L)

        assertTrue(raised)
        assertEquals(15, s.maxFor(pushups, 5L))
    }

    @Test
    fun aVerifiedCountBelowTheMaxChangesNothing() {
        val s = store()
        s.setMax(pushups, 10, 0L)

        val raised = s.raiseFromPerformance(pushups, 7, 5L)

        assertFalse(raised)
        assertEquals(10, s.maxFor(pushups, 5L))
    }

    @Test
    fun aVerifiedCountCancelsAPendingLowering() {
        val s = store(coolingOffMs = 1_000L)
        s.setMax(pushups, 20, 0L)
        s.setMax(pushups, 8, 10L)
        assertNotNull(s.pendingLowerFor(pushups, 10L))

        s.raiseFromPerformance(pushups, 25, 20L)

        assertNull(s.pendingLowerFor(pushups, 20L))
        assertEquals(25, s.maxFor(pushups, 5_000L))
    }

    @Test
    fun confirmedFlagIsRemembered() {
        val s = store()
        assertFalse(s.isConfirmed())

        s.confirm()

        assertTrue(s.isConfirmed())
    }
}

class RewardCalculatorTest {

    @Test
    fun aMaxSetEarnsTheSameForEveryone() {
        // 20 squats at 30 s each, and 40 jumping jacks at 15 s each, are both a full 10 minutes.
        assertEquals(30, RewardCalculator.secondsPerUnit(isHold = false, baselineMax = 20))
        assertEquals(15, RewardCalculator.secondsPerUnit(isHold = false, baselineMax = 40))
        assertEquals(600, 20 * 30)
        assertEquals(600, 40 * 15)
    }

    @Test
    fun aLowerMaxPaysMoreForEachRep() {
        val weak = RewardCalculator.secondsPerUnit(isHold = false, baselineMax = 12)
        val strong = RewardCalculator.secondsPerUnit(isHold = false, baselineMax = 40)

        assertTrue(weak > strong)
    }

    @Test
    fun payPerRepIsCappedSoAClaimedLowMaxCannotCheat() {
        // A claimed max of 3 would be 200 s per rep without the cap.
        assertEquals(60, RewardCalculator.secondsPerUnit(isHold = false, baselineMax = 3))
    }

    @Test
    fun payPerRepHasAFloor() {
        assertEquals(5, RewardCalculator.secondsPerUnit(isHold = false, baselineMax = 500))
    }

    @Test
    fun holdsPayPerFiveSecondUnit() {
        // A 60 s plank is 12 units, so 50 s per unit and 600 s for the full max hold.
        assertEquals(50, RewardCalculator.secondsPerUnit(isHold = true, baselineMax = 60))
        // A 30 s hold would be 100 s per unit, capped at 60.
        assertEquals(60, RewardCalculator.secondsPerUnit(isHold = true, baselineMax = 30))
    }

    @Test
    fun customRulesAreRespected() {
        val rules = RewardRules(maxSetRewardSeconds = 300, minSecondsPerUnit = 2, maxSecondsPerUnit = 100)

        assertEquals(15, RewardCalculator.secondsPerUnit(isHold = false, baselineMax = 20, rules = rules))
    }
}

class AppLimitsStoreTest {

    private class FakeStorage(var data: Map<String, AppUsage> = emptyMap()) : AppLimitsStorage {
        override fun load(): Map<String, AppUsage> = data
        override fun save(data: Map<String, AppUsage>) {
            this.data = data
        }
    }

    private val day1 = 0L
    private val day2 = 86_400_000L
    private val app = "com.example.insta"

    private fun store() = AppLimitsStore(FakeStorage())

    @Test
    fun anAppWithoutALimitUsesTheDefault() {
        assertEquals(1800, store().limitFor(app))
    }

    @Test
    fun limitIsClampedToTheAllowedRange() {
        val s = store()

        s.setLimit(app, 10)
        assertEquals(300, s.limitFor(app))

        s.setLimit(app, 999_999)
        assertEquals(14_400, s.limitFor(app))
    }

    @Test
    fun freeTimeIsUsedUpThenRunsOut() {
        val s = store()
        s.setLimit(app, 600)

        assertEquals(100, s.consumeFree(app, 100, day1))
        assertEquals(500, s.freeRemaining(app, day1))

        // Only 500 s were left, so only 500 s are covered by the free allowance.
        assertEquals(500, s.consumeFree(app, 700, day1))
        assertEquals(0, s.freeRemaining(app, day1))
        assertEquals(0, s.consumeFree(app, 50, day1))
    }

    @Test
    fun usageResetsOnANewDay() {
        val s = store()
        s.setLimit(app, 600)
        s.consumeFree(app, 600, day1)
        assertEquals(0, s.freeRemaining(app, day1))

        assertEquals(0, s.usedToday(app, day2))
        assertEquals(600, s.freeRemaining(app, day2))
        assertEquals(200, s.consumeFree(app, 200, day2))
    }

    @Test
    fun changingTheLimitKeepsTodaysUsage() {
        val s = store()
        s.setLimit(app, 600)
        s.consumeFree(app, 400, day1)

        s.setLimit(app, 900)

        assertEquals(400, s.usedToday(app, day1))
        assertEquals(500, s.freeRemaining(app, day1))
    }

    @Test
    fun eachAppHasItsOwnAllowance() {
        val s = store()
        s.setLimit("a", 600)
        s.setLimit("b", 900)

        s.consumeFree("a", 600, day1)

        assertEquals(0, s.freeRemaining("a", day1))
        assertEquals(900, s.freeRemaining("b", day1))
    }

    @Test
    fun consumingZeroOrNegativeDoesNothing() {
        val s = store()

        assertEquals(0, s.consumeFree(app, 0, day1))
        assertEquals(0, s.consumeFree(app, -5, day1))
        assertEquals(0, s.usedToday(app, day1))
    }
}

