package com.fitness.squatcounter.domain

/**
 * Central configuration object holding all thresholds for squat counting.
 * Kept together in one place so parameters can be tuned or adapted without scattering.
 */
data class SquatConfig(
    /**
     * Knee angle (in degrees) when fully standing / upright.
     * Typically around 160° - 180°.
     */
    val standingKneeAngle: Float = 160.0f,

    /**
     * Knee angle (in degrees) required to register reaching the bottom of a squat.
     * Typically 90° - 95° when thighs are parallel to ground.
     */
    val bottomKneeAngle: Float = 95.0f,

    /**
     * Hysteresis buffer (in degrees) applied to state transitions to prevent rapid toggling
     * when knee angle hovers right at threshold boundaries.
     */
    val hysteresis: Float = 6.0f,

    /**
     * Whether hip height must reach knee height (hips at or below knee level)
     * during the rep to qualify as valid depth.
     */
    val requireHipsAtKneeLevel: Boolean = true,

    /**
     * Minimum confidence score for each required body landmark (shoulders, hips, knees, ankles)
     * to consider tracking valid.
     */
    val visibilityConfidenceThreshold: Float = 0.5f,

    /**
     * Exponential Moving Average (EMA) smoothing factor: 0 < alpha <= 1.
     * Alpha = 0.35 provides great jitter reduction while remaining highly responsive at 10-15 fps.
     */
    val smoothingAlpha: Float = 0.35f,

    /**
     * Minimum duration (in ms) for a valid rep. Reps completed faster are rejected with TOO_FAST.
     */
    val minRepDurationMs: Long = 800L,

    /**
     * Maximum duration (in ms) for a valid rep. Reps taking longer are rejected with TOO_SLOW.
     */
    val maxRepDurationMs: Long = 8000L
)
