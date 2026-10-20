package com.fitness.squatcounter.domain

/**
 * Key landmarks required for full-body squat detection.
 * Shoulders, hips, knees, and ankles for both left and right sides.
 */
data class FullBodyPose(
    val leftShoulder: LandmarkPoint,
    val rightShoulder: LandmarkPoint,
    val leftHip: LandmarkPoint,
    val rightHip: LandmarkPoint,
    val leftKnee: LandmarkPoint,
    val rightKnee: LandmarkPoint,
    val leftAnkle: LandmarkPoint,
    val rightAnkle: LandmarkPoint,
    val isWorldCoordinates: Boolean = false
) {
    /**
     * Requirement: shoulders, hips, knees, ankles must all be visible above confidence threshold.
     */
    fun isFullBodyVisible(threshold: Float): Boolean {
        return leftShoulder.visibility >= threshold &&
                rightShoulder.visibility >= threshold &&
                leftHip.visibility >= threshold &&
                rightHip.visibility >= threshold &&
                leftKnee.visibility >= threshold &&
                rightKnee.visibility >= threshold &&
                leftAnkle.visibility >= threshold &&
                rightAnkle.visibility >= threshold
    }

    /**
     * Checks if hips have reached or gone below knee level.
     * In 2D normalized coordinates (y = 0 at top, 1 at bottom):
     * Hip being at or below knee level means hip.y >= knee.y.
     * In 3D metric coordinates (y = 0 at hip center, positive downwards towards feet in MediaPipe):
     * Hip being at or below knee level means hip.y >= knee.y.
     */
    fun areHipsAtOrBelowKnees(): Boolean {
        val avgHipY = (leftHip.y + rightHip.y) / 2.0f
        val avgKneeY = (leftKnee.y + rightKnee.y) / 2.0f
        // When standing: avgHipY is significantly smaller than avgKneeY (hip is higher up).
        // At full squat depth (parallel or below): avgHipY reaches or exceeds avgKneeY.
        return avgHipY >= avgKneeY
    }
}
