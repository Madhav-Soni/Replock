package com.fitness.squatcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LungeDetectorTest {

    private lateinit var detector: LungeDetector
    private val events = mutableListOf<SquatEvent>()
    private var now = 1000L

    // Standing: shoulders at 0.35, hips at 0.55 (torso 0.20 long), both knees at 0.75.
    // Drop = (hipY - 0.55) / 0.20, so hipY 0.64 is a drop of 0.45 torso lengths.
    // Knee gap = |leftKneeY - rightKneeY| / 0.20, so knees 0.80 and 0.70 are a gap of 0.50.

    @Before
    fun setUp() {
        detector = LungeDetector(LungeConfig(smoothingAlpha = 1f, calibrationFrames = 3))
        events.clear()
        now = 1000L
        detector.onEventListener = { events.add(it) }
    }

    private fun pose(
        hipY: Float = 0.55f,
        leftKneeY: Float = 0.75f,
        rightKneeY: Float = 0.75f,
        torso: Float = 0.20f,
        visible: Boolean = true
    ): List<LandmarkPoint> {
        val v = if (visible) 1f else 0f
        val shoulderY = hipY - torso
        val points = MutableList(33) { LandmarkPoint(0.5f, 0.5f, 0f, v) }
        points[11] = LandmarkPoint(0.40f, shoulderY, 0f, v)
        points[12] = LandmarkPoint(0.60f, shoulderY, 0f, v)
        points[23] = LandmarkPoint(0.42f, hipY, 0f, v)
        points[24] = LandmarkPoint(0.58f, hipY, 0f, v)
        points[25] = LandmarkPoint(0.42f, leftKneeY, 0f, v)
        points[26] = LandmarkPoint(0.58f, rightKneeY, 0f, v)
        return points
    }

    private fun feed(
        hipY: Float = 0.55f,
        leftKneeY: Float = 0.75f,
        rightKneeY: Float = 0.75f,
        torso: Float = 0.20f,
        visible: Boolean = true,
        advanceMs: Long = 300L
    ) {
        now += advanceMs
        detector.processFrame(pose(hipY, leftKneeY, rightKneeY, torso, visible), now, 0.5f)
    }

    private fun calibrate() {
        repeat(3) { feed() }
    }

    /** One clean lunge: hips down 0.45 torso lengths with the knees split. */
    private fun lunge() {
        feed(hipY = 0.59f, leftKneeY = 0.77f, rightKneeY = 0.77f) // DESCENDING
        feed(hipY = 0.64f, leftKneeY = 0.80f, rightKneeY = 0.70f) // BOTTOM, knees split
        feed(hipY = 0.58f, leftKneeY = 0.77f, rightKneeY = 0.74f) // ASCENDING
        feed() // STANDING
    }

    @Test
    fun noCountingWhileCalibrating() {
        feed()
        feed()

        assertFalse(detector.isCalibrated)
        assertEquals(0, detector.repCount)
    }

    @Test
    fun bodyVisibleNeedsShouldersHipsAndKnees() {
        assertTrue(detector.isBodyVisible(pose(), 0.5f))
        assertFalse(detector.isBodyVisible(pose(visible = false), 0.5f))
    }

    @Test
    fun validLungeIsCounted() {
        calibrate()
        lunge()

        assertEquals(SquatState.STANDING, detector.currentState)
        assertEquals(1, detector.repCount)
        assertEquals(RejectionReason.NONE, detector.lastRejectionReason)
        assertTrue(events.contains(SquatEvent.RepValidated(1)))
    }

    @Test
    fun twoLungesAreBothCounted() {
        calibrate()
        lunge()
        lunge()

        assertEquals(2, detector.repCount)
    }

    @Test
    fun squatWithLevelKneesIsRejectedAsNotALunge() {
        calibrate()

        feed(hipY = 0.59f, leftKneeY = 0.77f, rightKneeY = 0.77f)
        feed(hipY = 0.64f, leftKneeY = 0.78f, rightKneeY = 0.78f) // deep, but knees level
        feed(hipY = 0.58f, leftKneeY = 0.77f, rightKneeY = 0.77f)
        feed()

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.NOT_A_LUNGE, detector.lastRejectionReason)
        assertTrue(detector.rejectionDetail()!!.contains("needs 0.20"))
    }

    @Test
    fun shallowDipIsRejectedAsNotDeepEnough() {
        calibrate()

        feed(hipY = 0.59f) // DESCENDING
        feed(hipY = 0.61f, leftKneeY = 0.80f, rightKneeY = 0.70f) // drop 0.30, short of 0.35
        feed(hipY = 0.58f) // turns back up: ASCENDING
        feed() // STANDING

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.NOT_DEEP_ENOUGH, detector.lastRejectionReason)
    }

    @Test
    fun tooFastLungeIsRejected() {
        calibrate()

        feed(hipY = 0.59f, advanceMs = 100L)
        feed(hipY = 0.64f, leftKneeY = 0.80f, rightKneeY = 0.70f, advanceMs = 100L)
        feed(hipY = 0.58f, advanceMs = 100L)
        feed(advanceMs = 100L) // 300 ms total

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.TOO_FAST, detector.lastRejectionReason)
    }

    @Test
    fun tooSlowLungeIsRejected() {
        calibrate()

        feed(hipY = 0.59f)
        feed(hipY = 0.64f, leftKneeY = 0.80f, rightKneeY = 0.70f, advanceMs = 3000L)
        feed(hipY = 0.58f, advanceMs = 3000L)
        feed(advanceMs = 3000L) // 9000 ms total

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.TOO_SLOW, detector.lastRejectionReason)
    }

    @Test
    fun trackingLostMidRepRejectsTheRep() {
        calibrate()

        feed(hipY = 0.59f)
        feed(visible = false)
        assertTrue(detector.isTrackingLost)
        feed(hipY = 0.64f, leftKneeY = 0.80f, rightKneeY = 0.70f)
        feed(hipY = 0.58f)
        feed()

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.TRACKING_LOST_DURING_REP, detector.lastRejectionReason)
        assertTrue(events.contains(SquatEvent.TrackingLost))
        assertTrue(events.contains(SquatEvent.TrackingRegained))
    }

    @Test
    fun movingTowardTheCameraIsRejected() {
        calibrate()

        feed(hipY = 0.59f)
        feed(hipY = 0.64f, leftKneeY = 0.80f, rightKneeY = 0.70f, torso = 0.35f) // torso 1.75x bigger
        feed(hipY = 0.58f)
        feed()

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.SCALE_CHANGED, detector.lastRejectionReason)
    }

    @Test
    fun goingDeeperAfterASmallReversalStillCounts() {
        calibrate()

        feed(hipY = 0.59f) // DESCENDING
        feed(hipY = 0.61f) // drop 0.30
        feed(hipY = 0.57f) // small reversal: ASCENDING
        feed(hipY = 0.64f, leftKneeY = 0.80f, rightKneeY = 0.70f) // down again: BOTTOM
        assertEquals(SquatState.BOTTOM, detector.currentState)
        feed(hipY = 0.58f)
        feed()

        assertEquals(1, detector.repCount)
    }

    @Test
    fun resetClearsCountAndRecalibrates() {
        calibrate()
        lunge()
        assertEquals(1, detector.repCount)

        detector.reset()

        assertEquals(0, detector.repCount)
        assertFalse(detector.isCalibrated)
    }
}