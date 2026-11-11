package com.fitness.squatcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WallSitDetectorTest {

    private lateinit var detector: WallSitDetector
    private val events = mutableListOf<SquatEvent>()
    private var now = 1000L

    // Torso length is 0.20 (shoulders y 0.30, hips y 0.50). Knee y = 0.50 + gap * 0.20.
    // So gap 0.0 = thighs level, gap 1.0 = standing.

    @Before
    fun setUp() {
        detector = WallSitDetector(WallSitConfig(smoothingAlpha = 1f))
        events.clear()
        now = 1000L
        detector.onEventListener = { events.add(it) }
    }

    private fun pose(gap: Float, visible: Boolean = true): List<LandmarkPoint> {
        val v = if (visible) 1f else 0f
        val points = MutableList(33) { LandmarkPoint(0.5f, 0.5f, 0f, v) }
        points[11] = LandmarkPoint(0.45f, 0.30f, 0f, v)
        points[12] = LandmarkPoint(0.55f, 0.30f, 0f, v)
        points[23] = LandmarkPoint(0.46f, 0.50f, 0f, v)
        points[24] = LandmarkPoint(0.54f, 0.50f, 0f, v)
        val kneeY = 0.50f + gap * 0.20f
        points[25] = LandmarkPoint(0.45f, kneeY, 0f, v)
        points[26] = LandmarkPoint(0.55f, kneeY, 0f, v)
        return points
    }

    private fun feed(gap: Float = 0.0f, visible: Boolean = true, advanceMs: Long = 500L) {
        now += advanceMs
        detector.processFrame(pose(gap, visible), now, 0.5f)
    }

    private fun hold(frames: Int, gap: Float = 0.0f) = repeat(frames) { feed(gap) }

    @Test
    fun levelThighsHoldCompletesAUnitEveryFiveSeconds() {
        hold(11) // first frame starts the clock, next 10 add 5 s

        assertEquals(1, detector.repCount)
        assertTrue(events.contains(SquatEvent.RepValidated(1)))
        assertEquals(5, detector.displayCount)
        assertEquals(5, detector.spokenNumber(1))
    }

    @Test
    fun longerHoldCompletesMoreUnits() {
        hold(21)

        assertEquals(2, detector.repCount)
        assertEquals(10, detector.spokenNumber(detector.repCount))
    }

    @Test
    fun standingDoesNotCount() {
        hold(30, gap = 1.0f)

        assertEquals(0, detector.repCount)
        assertEquals(0, detector.displayCount)
        assertEquals(SquatState.STANDING, detector.currentState)
    }

    @Test
    fun halfSquatDoesNotCount() {
        hold(30, gap = 0.5f)

        assertEquals(0, detector.repCount)
    }

    @Test
    fun hipsSlightlyBelowKneesStillCount() {
        hold(11, gap = -0.2f)

        assertEquals(1, detector.repCount)
    }

    @Test
    fun sittingOnTheFloorDoesNotCount() {
        hold(30, gap = -0.8f)

        assertEquals(0, detector.repCount)
    }

    @Test
    fun risingUpBreaksTheHoldAndLosesThePartialUnit() {
        hold(5) // 2 s of hold
        repeat(4) { feed(gap = 1.0f) } // standing up for 1.5 s: past the grace period

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.WALL_SIT_BROKEN, detector.lastRejectionReason)
        assertTrue(events.contains(SquatEvent.RepRejected(RejectionReason.WALL_SIT_BROKEN)))
        assertTrue(detector.rejectionDetail()!!.isNotEmpty())

        hold(11) // a full 5 s is needed again
        assertEquals(1, detector.repCount)
    }

    @Test
    fun briefGlitchWithinGraceKeepsThePartialUnit() {
        hold(5) // 2 s
        feed(gap = 1.0f)
        feed(gap = 1.0f) // only 0.5 s broken

        assertEquals(RejectionReason.NONE, detector.lastRejectionReason)

        hold(7) // first restarts the clock, next 6 add the missing 3 s
        assertEquals(1, detector.repCount)
    }

    @Test
    fun earnedUnitsSurviveABrokenHold() {
        hold(11)
        assertEquals(1, detector.repCount)

        repeat(5) { feed(gap = 1.0f) }

        assertEquals(1, detector.repCount)
    }

    @Test
    fun trackingLostStopsTheClockAndIsReported() {
        hold(5)
        feed(visible = false)

        assertTrue(detector.isTrackingLost)
        assertTrue(events.contains(SquatEvent.TrackingLost))

        feed()
        assertFalse(detector.isTrackingLost)
        assertTrue(events.contains(SquatEvent.TrackingRegained))
    }

    @Test
    fun timeIsNotCreditedAcrossAGap() {
        hold(3)
        feed(advanceMs = 10_000L) // a 10 s gap is credited at most one 0.5 s step

        assertTrue(detector.displayCount <= 1)
        assertEquals(0, detector.repCount)
    }

    @Test
    fun kneesNotVisibleMeansBodyNotVisible() {
        assertTrue(detector.isBodyVisible(pose(0f), 0.5f))
        assertFalse(detector.isBodyVisible(pose(0f, visible = false), 0.5f))
    }

    @Test
    fun resetClearsEverything() {
        hold(11)
        detector.reset()

        assertEquals(0, detector.repCount)
        assertEquals(0, detector.displayCount)
        assertEquals(RejectionReason.NONE, detector.lastRejectionReason)
    }
}