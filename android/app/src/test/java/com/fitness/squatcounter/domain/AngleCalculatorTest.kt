package com.fitness.squatcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AngleCalculatorTest {

    @Test
    fun testStraightLineAngleIs180() {
        val a = LandmarkPoint(0f, 0f, 0f)
        val b = LandmarkPoint(0f, 1f, 0f)
        val c = LandmarkPoint(0f, 2f, 0f)

        val angle = AngleCalculator.calculateAngle(a, b, c)
        assertEquals(180.0f, angle, 0.01f)
    }

    @Test
    fun testRightAngleIs90() {
        val a = LandmarkPoint(0f, 0f, 0f)
        val b = LandmarkPoint(0f, 1f, 0f)
        val c = LandmarkPoint(1f, 1f, 0f)

        val angle = AngleCalculator.calculateAngle(a, b, c)
        assertEquals(90.0f, angle, 0.01f)
    }

    @Test
    fun testAverageLegAngles() {
        // Left leg at 90 degrees
        val lHip = LandmarkPoint(0f, 0f, 0f)
        val lKnee = LandmarkPoint(0f, 1f, 0f)
        val lAnkle = LandmarkPoint(1f, 1f, 0f)

        // Right leg at 180 degrees
        val rHip = LandmarkPoint(1f, 0f, 0f)
        val rKnee = LandmarkPoint(1f, 1f, 0f)
        val rAnkle = LandmarkPoint(1f, 2f, 0f)

        val dummyShoulder = LandmarkPoint(0f, -1f, 0f)

        val pose = FullBodyPose(
            leftShoulder = dummyShoulder,
            rightShoulder = dummyShoulder,
            leftHip = lHip,
            rightHip = rHip,
            leftKnee = lKnee,
            rightKnee = rKnee,
            leftAnkle = lAnkle,
            rightAnkle = rAnkle
        )

        val (avgKnee, _) = AngleCalculator.extractAngles(pose)
        // Average of 90 and 180 is 135
        assertEquals(135.0f, avgKnee, 0.01f)
    }

    @Test
    fun testAngleSmootherNoiseFiltering() {
        val smoother = AngleSmoother(alpha = 0.5f)
        // First value initializes
        assertEquals(100.0f, smoother.filter(100.0f), 0.01f)
        // Second value: 0.5 * 120 + 0.5 * 100 = 110
        assertEquals(110.0f, smoother.filter(120.0f), 0.01f)
        // Third value: 0.5 * 120 + 0.5 * 110 = 115
        assertEquals(115.0f, smoother.filter(120.0f), 0.01f)

        smoother.reset()
        // After reset, takes new raw value immediately
        assertEquals(160.0f, smoother.filter(160.0f), 0.01f)
    }

    @Test
    fun testFullBodyVisibilityCheck() {
        val visiblePoint = LandmarkPoint(0f, 0f, 0f, visibility = 0.9f)
        val hiddenPoint = LandmarkPoint(0f, 0f, 0f, visibility = 0.2f)

        val allVisiblePose = FullBodyPose(
            leftShoulder = visiblePoint, rightShoulder = visiblePoint,
            leftHip = visiblePoint, rightHip = visiblePoint,
            leftKnee = visiblePoint, rightKnee = visiblePoint,
            leftAnkle = visiblePoint, rightAnkle = visiblePoint
        )
        assertTrue(allVisiblePose.isFullBodyVisible(0.5f))

        val oneHiddenPose = allVisiblePose.copy(leftAnkle = hiddenPoint)
        assertTrue(!oneHiddenPose.isFullBodyVisible(0.5f))
    }
}
