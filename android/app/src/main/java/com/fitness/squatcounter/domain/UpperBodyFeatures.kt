package com.fitness.squatcounter.domain

import kotlin.math.sqrt

/**
 * Width / height of the portrait camera frame (4:3 sensor rotated to portrait).
 * Landmark x and y are normalized separately, so x is scaled by this to get true distances.
 */
const val CAMERA_FRAME_ASPECT = 3f / 4f

/**
 * Upper-body measurements for one frame, in units of frame height.
 *
 * @param hipY average hip height (0 = top of frame, 1 = bottom)
 * @param shoulderY average shoulder height
 * @param torsoLength distance from the shoulder midpoint to the hip midpoint
 * @param isVisible false if shoulders or hips are not clearly visible
 */
data class UpperBodyFeatures(
    val hipY: Float,
    val shoulderY: Float,
    val torsoLength: Float,
    val isVisible: Boolean
) {
    companion object {
        val NOT_VISIBLE = UpperBodyFeatures(
            hipY = 0f,
            shoulderY = 0f,
            torsoLength = 0f,
            isVisible = false
        )
    }
}

/**
 * Builds [UpperBodyFeatures] from the 33 MediaPipe pose landmarks.
 * Only shoulders (11, 12) and hips (23, 24) are needed, so it works with legs out of frame.
 */
object UpperBodyExtractor {

    private const val LEFT_SHOULDER = 11
    private const val RIGHT_SHOULDER = 12
    private const val LEFT_HIP = 23
    private const val RIGHT_HIP = 24

    /** Torso shorter than this (in frame heights) is treated as a bad detection. */
    private const val MIN_TORSO_LENGTH = 0.01f

    fun extract(landmarks: List<LandmarkPoint>, visibilityThreshold: Float): UpperBodyFeatures {
        if (landmarks.size <= RIGHT_HIP) return UpperBodyFeatures.NOT_VISIBLE

        val leftShoulder = landmarks[LEFT_SHOULDER]
        val rightShoulder = landmarks[RIGHT_SHOULDER]
        val leftHip = landmarks[LEFT_HIP]
        val rightHip = landmarks[RIGHT_HIP]

        val allVisible = leftShoulder.visibility >= visibilityThreshold &&
            rightShoulder.visibility >= visibilityThreshold &&
            leftHip.visibility >= visibilityThreshold &&
            rightHip.visibility >= visibilityThreshold
        if (!allVisible) return UpperBodyFeatures.NOT_VISIBLE

        val shoulderX = (leftShoulder.x + rightShoulder.x) / 2f
        val shoulderY = (leftShoulder.y + rightShoulder.y) / 2f
        val hipX = (leftHip.x + rightHip.x) / 2f
        val hipY = (leftHip.y + rightHip.y) / 2f

        val dx = (shoulderX - hipX) * CAMERA_FRAME_ASPECT
        val dy = shoulderY - hipY
        val torsoLength = sqrt(dx * dx + dy * dy)
        if (torsoLength < MIN_TORSO_LENGTH) return UpperBodyFeatures.NOT_VISIBLE

        return UpperBodyFeatures(
            hipY = hipY,
            shoulderY = shoulderY,
            torsoLength = torsoLength,
            isVisible = true
        )
    }
}