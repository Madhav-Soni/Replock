package com.fitness.squatcounter.domain

/**
 * Normalized (2D) or World (3D metric) point for a landmark.
 */
data class LandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float = 1.0f
)
