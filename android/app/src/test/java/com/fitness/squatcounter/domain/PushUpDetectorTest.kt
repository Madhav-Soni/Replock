package com.fitness.squatcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PushUpDetectorTest {

    private lateinit var detector: PushUpDetector
    private val events = mutableListOf<SquatEvent>()
    private var now = 1000L

    // Top position: nose at 0.30, shoulders at 0.45, shoulder width 0.25 (frame heights).
    // Nose drop = (noseY - 0.30) / 0.25, so noseY 0.42 is a drop of 0.48 shoulder widths.

    @Before
    fun setUp() {
        detector = PushUpDetector(PushUpConfig(smoothingAlpha = 1f, calibrationFrames = 3))
        events.clear()
        now = 1000L
        detector.onEventListener = { events.add(it) }
    }

    private fun pose(
        noseY: Float,
        shoulderY: Float,
        width: Float = 0.25f,
        visible: Boolean = true
    ): List<LandmarkPoint> {
        val v = if (visible) 1f else 0f
        val points = MutableList(33) { LandmarkPoint(0.5f, 0.5f, 0f, v) }
        val halfDx = (width / CAMERA_FRAME_ASPECT) / 2f
        points[0] = LandmarkPoint(0.5f, noseY, 0f, v)
        points[11] = LandmarkPoint(0.5f - halfDx, shoulderY, 0f, v)
        points[12] = LandmarkPoint(0.5f + halfDx, shoulderY, 0f, v)
        points[15] = LandmarkPoint(0.30f, shoulderY + 0.10f, 0f, v)
        points[16] = LandmarkPoint(0.70f, shoulderY + 0.10f, 0f, v)
        return points
    }

    private fun feed(
        noseY: Float,
        shoulderY: Float,
        width: Float = 0.25f,
        visible: Boolean = true,
        advanceMs: Long = 400L
    ) {
        now += advanceMs
        detector.processFrame(pose(noseY, shoulderY, width, visible), now, 0.5f)
    }

    private fun calibrate() {
        repeat(3) { feed(0.30f, 0.45f) }
    }

    @Test
    fun noCountingWhileCalibrating() {
        feed(0.30f, 0.45f)
        feed(0.30f, 0.45f)

        assertFalse(detector.isCalibrated)
        assertEquals(0, detector.repCount)
    }

    @Test
    fun bodyVisibleNeedsNoseShouldersAndWrists() {
        assertTrue(detector.isBodyVisible(pose(0.30f, 0.45f), 0.5f))
        assertFalse(detector.isBodyVisible(pose(0.30f, 0.45f, visible = false), 0.5f))
    }

    @Test
    fun validPushUpIsCounted() {
        calibrate()

        feed(0.35f, 0.47f) // nose drop 0.20: DESCENDING
        assertEquals(SquatState.DESCENDING, detector.currentState)
        feed(0.42f, 0.52f) // nose drop 0.48, shoulders 0.28: BOTTOM
        assertEquals(SquatState.BOTTOM, detector.currentState)
        feed(0.36f, 0.48f) // ASCENDING
        assertEquals(SquatState.ASCENDING, detector.currentState)
        feed(0.31f, 0.45f) // back at the top, rep took 1200 ms

        assertEquals(1, detector.repCount)
        assertEquals(RejectionReason.NONE, detector.lastRejectionReason)
        assertTrue(events.contains(SquatEvent.RepValidated(1)))
    }

    @Test
    fun twoPushUpsAreBothCounted() {
        calibrate()
        repeat(2) {
            feed(0.35f, 0.47f)
            feed(0.42f, 0.52f)
            feed(0.36f, 0.48f)
            feed(0.31f, 0.45f)
        }

        assertEquals(2, detector.repCount)
    }

    @Test
    fun headNodWithoutShouldersIsRejected() {
        calibrate()

        feed(0.35f, 0.45f)
        feed(0.42f, 0.45f) // nose drops 0.48 but the shoulders do not move
        feed(0.36f, 0.45f)
        feed(0.31f, 0.45f)

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.HEAD_ONLY_MOVEMENT, detector.lastRejectionReason)
    }

    @Test
    fun halfPushUpIsRejectedAsNotDeepEnough() {
        calibrate()

        feed(0.35f, 0.47f) // DESCENDING
        feed(0.39f, 0.49f) // nose drop 0.36, short of 0.40
        feed(0.35f, 0.47f) // turns back up: ASCENDING
        feed(0.30f, 0.45f) // top

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.NOT_DEEP_ENOUGH, detector.lastRejectionReason)
        assertTrue(detector.rejectionDetail()!!.contains("needs 0.40"))
    }

    @Test
    fun tooFastPushUpIsRejected() {
        calibrate()

        feed(0.35f, 0.47f, advanceMs = 100L)
        feed(0.42f, 0.52f, advanceMs = 100L)
        feed(0.36f, 0.48f, advanceMs = 100L)
        feed(0.30f, 0.45f, advanceMs = 100L) // 300 ms total

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.TOO_FAST, detector.lastRejectionReason)
    }

    @Test
    fun tooSlowPushUpIsRejected() {
        calibrate()

        feed(0.35f, 0.47f)
        feed(0.42f, 0.52f, advanceMs = 3000L)
        feed(0.36f, 0.48f, advanceMs = 3000L)
        feed(0.30f, 0.45f, advanceMs = 3000L) // 9000 ms total

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.TOO_SLOW, detector.lastRejectionReason)
    }

    @Test
    fun trackingLostMidRepRejectsTheRep() {
        calibrate()

        feed(0.35f, 0.47f)
        feed(0.30f, 0.45f, visible = false)
        feed(0.42f, 0.52f)
        feed(0.36f, 0.48f)
        feed(0.30f, 0.45f)

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.TRACKING_LOST_DURING_REP, detector.lastRejectionReason)
        assertTrue(events.contains(SquatEvent.TrackingLost))
        assertTrue(events.contains(SquatEvent.TrackingRegained))
    }

    @Test
    fun bigChangeInShoulderWidthIsRejected() {
        calibrate()

        feed(0.35f, 0.47f)
        feed(0.42f, 0.52f, width = 0.45f) // shoulders suddenly 1.8x wider: moved toward the camera
        feed(0.36f, 0.48f)
        feed(0.30f, 0.45f)

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.SCALE_CHANGED, detector.lastRejectionReason)
    }

    @Test
    fun goingDeeperAfterASmallReversalStillCounts() {
        calibrate()

        feed(0.35f, 0.47f) // DESCENDING
        feed(0.39f, 0.49f) // nose drop 0.36
        feed(0.34f, 0.47f) // small reversal: ASCENDING
        feed(0.42f, 0.52f) // down again, past the bottom: BOTTOM
        assertEquals(SquatState.BOTTOM, detector.currentState)
        feed(0.36f, 0.48f)
        feed(0.31f, 0.45f)

        assertEquals(1, detector.repCount)
    }

    @Test
    fun resetClearsCountAndRecalibrates() {
        calibrate()
        feed(0.35f, 0.47f)
        feed(0.42f, 0.52f)
        feed(0.36f, 0.48f)
        feed(0.31f, 0.45f)
        assertEquals(1, detector.repCount)

        detector.reset()

        assertEquals(0, detector.repCount)
        assertFalse(detector.isCalibrated)
    }
}