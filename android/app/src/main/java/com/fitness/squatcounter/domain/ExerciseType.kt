package com.fitness.squatcounter.domain

import com.fitness.squatcounter.data.CreditConfig

/**
 * The exercises the app can count. Each has a name for the picker and the key the credits
 * ledger uses to apply that exercise's own reward rate and daily cap.
 */
enum class ExerciseType(
    val label: String,
    val creditKey: String,
    /** True for holds, where the baseline is in seconds and pay is per 5 s unit. */
    val isHold: Boolean = false
) {
    SQUATS("Squats", CreditConfig.EXERCISE_SQUATS),
    PUSHUPS("Push-ups", CreditConfig.EXERCISE_PUSHUPS),
    PLANK("Plank", CreditConfig.EXERCISE_PLANK, isHold = true),
    LUNGES("Lunges", CreditConfig.EXERCISE_LUNGES),
    JUMPING_JACKS("Jumping jacks", CreditConfig.EXERCISE_JUMPING_JACKS)
}