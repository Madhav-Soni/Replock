package com.fitness.squatcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlankDetectorTest {

    private lateinit var detector: PlankDetector
    private val events = mutableListOf<SquatEvent>()
    private var now = 1000L

    // Plank position: nose at 0.30, shoulders at 0.45, shoulder width 0.25 (frame heights).
    // Deviation = (value - baseline) / 0.25, so shoulderY 0.60 is 0.6 shoulder widths away.

    @Before
    fun setUp() {
        detector = PlankDetector(PlankConfig(smoothingAlpha = 1f, calibrationFrames = 3))
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
        noseY: Float = 0.30f,
        shoulderY: Float = 0.45f,
        width: Float = 0.25f,
        visible: Boolean = true,
        advanceMs: Long = 500L
    ) {
        now += advanceMs
        detector.processFrame(pose(noseY, shoulderY, width, visible), now, 0.5f)
    }

    private fun calibrate() {
        repeat(3) { feed() }
    }

    private fun hold(frames: Int) {
        repeat(frames) { feed() }
    }

    @Test
    fun nothingCountsWhileCalibrating() {
        feed()
        feed()

        assertFalse(detector.isCalibrated)
        assertEquals(0, detector.repCount)
        assertEquals(0, detector.displayCount)
    }

    @Test
    fun steadyHoldCompletesAUnitEveryFiveSeconds() {
        calibrate()

        // The first valid frame only starts the clock, the next 10 add 5 seconds.
        hold(11)

        assertEquals(1, detector.repCount)
        assertTrue(events.contains(SquatEvent.RepValidated(1)))
        assertEquals(5, detector.displayCount)
    }

    @Test
    fun longerHoldCompletesMoreUnits() {
        calibrate()

        hold(21) // 20 steps = 10 seconds

        assertEquals(2, detector.repCount)
        assertEquals(10, detector.spokenNumber(detector.repCount))
    }

    @Test
    fun brokenPostureLosesThePartialUnit() {
        calibrate()
        hold(5) // 2 seconds of hold

        repeat(4) { feed(shoulderY = 0.60f) } // shoulders collapse for 1.5 s: past the grace period

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.POSTURE_BROKEN, detector.lastRejectionReason)
        assertTrue(events.contains(SquatEvent.RepRejected(RejectionReason.POSTURE_BROKEN)))

        // The partial unit is gone: a full 5 seconds is needed again.
        hold(11)
        assertEquals(1, detector.repCount)
    }

    @Test
    fun briefGlitchWithinGraceKeepsThePartialUnit() {
        calibrate()
        hold(5) // 2 seconds

        feed(shoulderY = 0.60f)
        feed(shoulderY = 0.60f) // only 0.5 s of broken posture

        assertEquals(RejectionReason.NONE, detector.lastRejectionReason)

        // 7 valid frames: the first restarts the clock, the next 6 add the missing 3 seconds.
        hold(7)
        assertEquals(1, detector.repCount)
    }

    @Test
    fun movingTowardThePhoneBreaksTheHold() {
        calibrate()
        hold(5)

        repeat(4) { feed(width = 0.40f) } // shoulders 1.6x wider: too close

        assertEquals(RejectionReason.POSTURE_BROKEN, detector.lastRejectionReason)
        assertEquals(0, detector.repCount)
    }

    @Test
    fun trackingLostStopsTheClockAndIsReported() {
        calibrate()
        hold(5)

        feed(visible = false)
        feed(visible = false)
        feed(visible = false)
        feed(visible = false) // 1.5 s lost: unit reset

        assertTrue(detector.isTrackingLost)
        assertTrue(events.contains(SquatEvent.TrackingLost))
        assertEquals(RejectionReason.POSTURE_BROKEN, detector.lastRejectionReason)

        hold(1)
        assertTrue(events.contains(SquatEvent.TrackingRegained))
    }

    @Test
    fun startingPositionIsMeasuredAgainIfTheUserWasNotInPlankYet() {
        calibrate() // baseline measured at 0.30 / 0.45

        // The user is actually lower and further down the frame: not a valid plank for the baseline.
        repeat(7) { feed(noseY = 0.45f, shoulderY = 0.60f) } // 3 s out of position: re-measure

        assertFalse(detector.isCalibrated)

        repeat(3) { feed(noseY = 0.45f, shoulderY = 0.60f) } // new baseline measured here
        assertTrue(detector.isCalibrated)

        repeat(11) { feed(noseY = 0.45f, shoulderY = 0.60f) }
        assertEquals(1, detector.repCount)
    }

    @Test
    fun resetClearsEverything() {
        calibrate()
        hold(11)
        assertEquals(1, detector.repCount)

        detector.reset()

        assertEquals(0, detector.repCount)
        assertEquals(0, detector.displayCount)
        assertFalse(detector.isCalibrated)
    }

    @Test
    fun bodyVisibleNeedsNoseShouldersAndWrists() {
        assertTrue(detector.isBodyVisible(pose(0.30f, 0.45f), 0.5f))
        assertFalse(detector.isBodyVisible(pose(0.30f, 0.45f, visible = false), 0.5f))
    }
}