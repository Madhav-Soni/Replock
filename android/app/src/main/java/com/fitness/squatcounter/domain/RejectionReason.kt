package com.fitness.squatcounter.domain

/**
 * Reasons why a squat rep or movement was rejected.
 */
enum class RejectionReason(val displayName: String) {
    NOT_DEEP_ENOUGH("Half rep: hips did not reach knee level"),
    TRACKING_LOST_DURING_REP("Rep invalid: person lost from camera"),
    NONE("None")
}
