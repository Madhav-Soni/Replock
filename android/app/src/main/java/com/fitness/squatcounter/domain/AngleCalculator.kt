package com.fitness.squatcounter.domain

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Calculates joint angles (in degrees) for 2D or 3D poses.
 */
object AngleCalculator {

    /**
     * Calculates the angle in degrees at vertex B formed by line segments BA and BC.
     * Works for both 2D (x, y) and 3D (x, y, z) coordinates.
     */
    fun calculateAngle(
        a: LandmarkPoint,
        b: LandmarkPoint,
        c: LandmarkPoint,
        use3D: Boolean = true
    ): Float {
        val v1x = a.x - b.x
        val v1y = a.y - b.y
        val v1z = if (use3D) a.z - b.z else 0f

        val v2x = c.x - b.x
        val v2y = c.y - b.y
        val v2z = if (use3D) c.z - b.z else 0f

        val dot = v1x * v2x + v1y * v2y + v1z * v2z
        val mag1 = sqrt(v1x * v1x + v1y * v1y + v1z * v1z)
        val mag2 = sqrt(v2x * v2x + v2y * v2y + v2z * v2z)

        if (mag1 < 1e-6f || mag2 < 1e-6f) {
            return 0.0f
        }

        val cosVal = (dot / (mag1 * mag2)).coerceIn(-1.0f, 1.0f)
        return (acos(cosVal.toDouble()) * 180.0 / Math.PI).toFloat()
    }

    /**
     * Knee flexion/extension angle: Hip -> Knee -> Ankle (around Knee).
     * Fully standing is ~160° to 180°.
     * Squat bottom (parallel/deep) is ~70° to 95°.
     */
    fun calculateKneeAngle(
        hip: LandmarkPoint,
        knee: LandmarkPoint,
        ankle: LandmarkPoint,
        use3D: Boolean = true
    ): Float = calculateAngle(hip, knee, ankle, use3D)

    /**
     * Hip flexion/extension angle: Shoulder -> Hip -> Knee (around Hip).
     * Standing is ~160° to 180°.
     * Squatting bends the torso relative to thighs (~60° to 100°).
     */
    fun calculateHipAngle(
        shoulder: LandmarkPoint,
        hip: LandmarkPoint,
        knee: LandmarkPoint,
        use3D: Boolean = true
    ): Float = calculateAngle(shoulder, hip, knee, use3D)

    /**
     * Extracts averaged knee and hip angles from both legs for the given full-body pose.
     * Returns Pair(kneeAngle, hipAngle).
     */
    fun extractAngles(
        pose: FullBodyPose,
        use3D: Boolean = true
    ): Pair<Float, Float> {
        val leftKnee = calculateKneeAngle(pose.leftHip, pose.leftKnee, pose.leftAnkle, use3D)
        val rightKnee = calculateKneeAngle(pose.rightHip, pose.rightKnee, pose.rightAnkle, use3D)
        val avgKnee = (leftKnee + rightKnee) / 2.0f

        val leftHip = calculateHipAngle(pose.leftShoulder, pose.leftHip, pose.leftKnee, use3D)
        val rightHip = calculateHipAngle(pose.rightShoulder, pose.rightHip, pose.rightKnee, use3D)
        val avgHip = (leftHip + rightHip) / 2.0f

        return Pair(avgKnee, avgHip)
    }
}
