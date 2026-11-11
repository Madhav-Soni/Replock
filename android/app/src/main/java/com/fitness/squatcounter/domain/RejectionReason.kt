package com.fitness.squatcounter.domain

/**
 * Reasons why a squat rep or movement was rejected.
 */
enum class RejectionReason(val displayName: String) {
    NOT_DEEP_ENOUGH("Half rep: hips did not reach knee level"),
    TOO_FAST("Rep too fast: minimum duration not met"),
    TOO_SLOW("Rep too slow: maximum duration exceeded"),
    TRACKING_LOST_DURING_REP("Rep invalid: person lost from camera"),
    SCALE_CHANGED("Rep invalid: you moved toward or away from the camera"),
    HEAD_ONLY_MOVEMENT("Rep invalid: only the head moved, shoulders did not drop"),
    POSTURE_BROKEN("Hold broken: keep your body steady in the plank position"),
    WALL_SIT_BROKEN("Hold broken: keep your hips near knee level"),
    NOT_A_LUNGE("Rep invalid: one knee must drop lower than the other"),
    INCOMPLETE_JACK("Rep invalid: raise both hands overhead and spread your feet wide"),
    NONE("None")
}