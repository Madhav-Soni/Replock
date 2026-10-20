package com.fitness.squatcounter.domain

/**
 * Reasons why a squat rep or movement was rejected.
 */
enum class RejectionReason(val displayName: String) {
    NOT_DEEP_ENOUGH("Half rep: hips did not reach knee level"),
    TOO_FAST("Rep too fast: minimum duration not met"),
    TOO_SLOW("Rep too slow: maximum duration exceeded"),
    TRACKING_LOST_DURING_REP("Rep invalid: person lost from camera"),
    NONE("None")
}
