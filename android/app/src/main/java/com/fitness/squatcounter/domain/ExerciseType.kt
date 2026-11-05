package com.fitness.squatcounter.domain

import com.fitness.squatcounter.data.CreditConfig

/**
 * The exercises the app can count. Each has a name for the picker and the key the credits
 * ledger uses to apply that exercise's own reward rate and daily cap.
 */
enum class ExerciseType(val label: String, val creditKey: String) {
    SQUATS("Squats", CreditConfig.EXERCISE_SQUATS),
    PUSHUPS("Push-ups", CreditConfig.EXERCISE_PUSHUPS)
}