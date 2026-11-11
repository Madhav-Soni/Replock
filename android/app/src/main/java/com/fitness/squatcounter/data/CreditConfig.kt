package com.fitness.squatcounter.data

/**
 * Reward rule for one exercise: how many screen-time seconds each valid rep earns,
 * and the most that exercise can earn per day.
 */
data class ExerciseReward(
    val secondsPerRep: Int,
    val dailyCapSeconds: Int
)

/**
 * Central configuration for the local credits ledger.
 *
 * Existing field names are kept so older code and tests keep compiling.
 * The squat fields are the original ones; push-up fields are new.
 */
data class CreditConfig(
    /**
     * Screen-time seconds rewarded per validated SQUAT rep.
     */
    val rewardRateSecondsPerRep: Int = 30,

    /**
     * Daily total earn cap across all exercises (in screen-time seconds).
     * 1800 seconds = 30 minutes.
     */
    val dailyTotalEarnCapSeconds: Int = 1800,

    /**
     * Daily squat earn cap (in screen-time seconds).
     */
    val dailySquatCapSeconds: Int = 1800,

    /**
     * Screen-time seconds rewarded per validated PUSH-UP rep.
     */
    val pushUpRewardSecondsPerRep: Int = 60,

    /**
     * Daily push-up earn cap (in screen-time seconds).
     */
    val dailyPushUpCapSeconds: Int = 1800,

    /**
     * Screen-time seconds rewarded per completed PLANK unit (5 seconds of valid hold).
     * 30 per 5 s means a one-minute plank earns 6 minutes. A starting guess.
     */
    val plankRewardSecondsPerUnit: Int = 30,

    /**
     * Daily plank earn cap (in screen-time seconds).
     */
    val dailyPlankCapSeconds: Int = 1800,

    /**
     * Screen-time seconds rewarded per validated LUNGE rep (either leg counts). A starting guess.
     */
    val lungeRewardSecondsPerRep: Int = 30,

    /**
     * Daily lunge earn cap (in screen-time seconds).
     */
    val dailyLungeCapSeconds: Int = 1800,

    /**
     * Screen-time seconds rewarded per validated JUMPING JACK. They are quick, so the rate is
     * low per rep. A starting guess.
     */
    val jumpingJackRewardSecondsPerRep: Int = 10,

    /**
     * Daily jumping-jack earn cap (in screen-time seconds).
     */
    val dailyJumpingJackCapSeconds: Int = 1800
) {
    /**
     * Returns the reward rule for an exercise type, or null if the exercise is unknown.
     * Unknown exercises earn nothing (fail safe) instead of falling back to another rule.
     */
    fun rewardFor(exerciseType: String): ExerciseReward? = when (exerciseType) {
        EXERCISE_SQUATS -> ExerciseReward(rewardRateSecondsPerRep, dailySquatCapSeconds)
        EXERCISE_PUSHUPS -> ExerciseReward(pushUpRewardSecondsPerRep, dailyPushUpCapSeconds)
        EXERCISE_PLANK -> ExerciseReward(plankRewardSecondsPerUnit, dailyPlankCapSeconds)
        EXERCISE_LUNGES -> ExerciseReward(lungeRewardSecondsPerRep, dailyLungeCapSeconds)
        EXERCISE_WALL_SIT -> ExerciseReward(plankRewardSecondsPerUnit, dailyPlankCapSeconds)
        EXERCISE_JUMPING_JACKS ->
            ExerciseReward(jumpingJackRewardSecondsPerRep, dailyJumpingJackCapSeconds)
        else -> null
    }

    companion object {
        const val EXERCISE_SQUATS = "squats"
        const val EXERCISE_PUSHUPS = "pushups"
        const val EXERCISE_PLANK = "plank"
        const val EXERCISE_LUNGES = "lunges"
        const val EXERCISE_WALL_SIT = "wallsit"
        const val EXERCISE_JUMPING_JACKS = "jumpingjacks"
    }
}