package com.fitness.squatcounter.data

import kotlin.math.roundToInt

/**
 * @param maxSetRewardSeconds what a full max-effort set earns, for any exercise (600 = 10 min)
 * @param minSecondsPerUnit lowest pay per rep (or per hold unit)
 * @param maxSecondsPerUnit highest pay per rep. Stops a low claimed max paying too much.
 * @param holdUnitSeconds a hold (plank, wall sit) pays in units of this many seconds
 */
data class RewardRules(
    val maxSetRewardSeconds: Int = 600,
    val minSecondsPerUnit: Int = 5,
    val maxSecondsPerUnit: Int = 60,
    val holdUnitSeconds: Int = 5
)

/**
 * Makes rewards fair: a max-effort set earns the same time for everyone, so each rep is worth
 * more to someone whose max is lower. Pay per rep = max-set reward divided by your max,
 * kept between the minimum and maximum per unit.
 */
object RewardCalculator {

    /**
     * @param isHold true for holds, where [baselineMax] is in seconds
     * @param baselineMax the user's max: reps, or seconds for a hold
     */
    fun secondsPerUnit(
        isHold: Boolean,
        baselineMax: Int,
        rules: RewardRules = RewardRules()
    ): Int {
        val units = if (isHold) {
            maxOf(1, baselineMax / rules.holdUnitSeconds)
        } else {
            maxOf(1, baselineMax)
        }
        val raw = (rules.maxSetRewardSeconds.toFloat() / units).roundToInt()
        return raw.coerceIn(rules.minSecondsPerUnit, rules.maxSecondsPerUnit)
    }
}

