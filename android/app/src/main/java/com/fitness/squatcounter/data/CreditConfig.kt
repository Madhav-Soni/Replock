package com.fitness.squatcounter.data

/**
 * Central configuration for the local credits ledger.
 */
data class CreditConfig(
    /**
     * Screen-time seconds rewarded per validated squat rep.
     */
    val rewardRateSecondsPerRep: Int = 30,

    /**
     * Daily total earn cap across all exercises (in screen-time seconds).
     * 1800 seconds = 30 minutes.
     */
    val dailyTotalEarnCapSeconds: Int = 1800,

    /**
     * Daily per-exercise earn cap (in screen-time seconds).
     */
    val dailySquatCapSeconds: Int = 1800
)
