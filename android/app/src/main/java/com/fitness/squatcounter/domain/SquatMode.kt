package com.fitness.squatcounter.domain

/**
 * How squats are detected in a session. Chosen automatically when the session starts and
 * kept for the whole session.
 */
enum class SquatMode {
    /** Shoulders, hips, knees and ankles visible: counts using the knee angle. */
    FULL_BODY,

    /** Only shoulders and hips needed: counts using how far the hips drop. */
    CLOSE_UP
}