package com.fitness.squatcounter.domain

/**
 * States representing the cycle of a single squat rep:
 * STANDING -> DESCENDING -> BOTTOM -> ASCENDING -> STANDING
 */
enum class SquatState {
    STANDING,
    DESCENDING,
    BOTTOM,
    ASCENDING
}
