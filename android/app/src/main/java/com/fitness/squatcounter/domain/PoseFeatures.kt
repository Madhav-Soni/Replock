package com.fitness.squatcounter.domain

/**
 * Extracted features for a single frame.
 */
data class PoseFeatures(
    val rawKneeAngle: Float,
    val smoothedKneeAngle: Float,
    val rawHipAngle: Float,
    val smoothedHipAngle: Float,
    val areHipsAtOrBelowKnees: Boolean,
    val isFullBodyVisible: Boolean
)
