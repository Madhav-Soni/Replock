package com.fitness.squatcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JumpingJackDetectorTest {

    private lateinit var detector: JumpingJackDetector
    private val events = mutableListOf<SquatEvent>()
    private var now = 1000L

    // Layout (frame units): nose at y 0.20, shoulders at y 0.30 and 0.16 apart in x,
    // so shoulder width is 0.16 * 0.75 = 0.12.
    //  - Arms down: wrists at y 0.55 (far below the shoulders). Arms up: wrists at y 0.10 (above the nose).
    //    Arms mid: wrists at y 0.30 (at shoulder height: neither up nor down).
    //  - Feet together: ankles 0.04 apart in x (spread 0.25 shoulder widths).
    //    Feet wide: ankles 0.40 apart (spread 2.5). Feet mid: 0.20 apart (spread 1.25).

    private enum class Arms(val wristY: Float) { DOWN(0.55f), MID(0.30f), UP(0.10f) }
    private enum class Feet(val leftX: Float, val rightX: Float) {
        TOGETHER(0.48f, 0.52f),
        MID(0.40f, 0.60f),
        WIDE(0.30f, 0.70f)
    }

    @Before
    fun setUp() {
        detector = JumpingJackDetector(JumpingJackConfig(smoothingAlpha = 1f))
        events.clear()
        now = 1000L
        detector.onEventListener = { events.add(it) }
    }

    private fun pose(arms: Arms, feet: Feet, visible: Boolean = true): List<LandmarkPoint> {
        val v = if (visible) 1f else 0f
        val points = MutableList(33) { LandmarkPoint(0.5f, 0.5f, 0f, v) }
        points[0] = LandmarkPoint(0.50f, 0.20f, 0f, v)
        points[11] = LandmarkPoint(0.42f, 0.30f, 0f, v)
        points[12] = LandmarkPoint(0.58f, 0.30f, 0f, v)
        points[15] = LandmarkPoint(0.35f, arms.wristY, 0f, v)
        points[16] = LandmarkPoint(0.65f, arms.wristY, 0f, v)
        points[27] = LandmarkPoint(feet.leftX, 0.90f, 0f, v)
        points[28] = LandmarkPoint(feet.rightX, 0.90f, 0f, v)
        return points
    }

    private fun feed(arms: Arms, feet: Feet, visible: Boolean = true, advanceMs: Long = 250L) {
        now += advanceMs
        detector.processFrame(pose(arms, feet, visible), now, 0.5f)
    }

    private fun closed(advanceMs: Long = 250L) = feed(Arms.DOWN, Feet.TOGETHER, advanceMs = advanceMs)
    private fun mid(advanceMs: Long = 250L) = feed(Arms.MID, Feet.MID, advanceMs = advanceMs)
    private fun open(advanceMs: Long = 250L) = feed(Arms.UP, Feet.WIDE, advanceMs = advanceMs)

    private fun jack() {
        mid()
        open()
        mid()
        closed()
    }

    @Test
    fun bodyVisibleNeedsNoseShouldersWristsAndAnkles() {
        assertTrue(detector.isBodyVisible(pose(Arms.DOWN, Feet.TOGETHER), 0.5f))
        assertFalse(detector.isBodyVisible(pose(Arms.DOWN, Feet.TOGETHER, visible = false), 0.5f))
    }

    @Test
    fun standingStillCountsNothing() {
        repeat(10) { closed() }

        assertEquals(0, detector.repCount)
        assertEquals(SquatState.STANDING, detector.currentState)
        assertTrue(events.none { it is SquatEvent.StateChanged })
    }

    @Test
    fun fullJumpingJackIsCounted() {
        closed()
        closed()
        jack()

        assertEquals(1, detector.repCount)
        assertEquals(SquatState.STANDING, detector.currentState)
        assertEquals(RejectionReason.NONE, detector.lastRejectionReason)
        assertTrue(events.contains(SquatEvent.RepValidated(1)))
    }

    @Test
    fun severalJacksInARowAreAllCounted() {
        closed()
        jack()
        jack()
        jack()

        assertEquals(3, detector.repCount)
    }

    @Test
    fun wavingOnlyTheArmsIsRejected() {
        closed()

        mid()
        feed(Arms.UP, Feet.TOGETHER) // hands overhead, feet still together
        mid()
        closed()

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.INCOMPLETE_JACK, detector.lastRejectionReason)
        assertTrue(detector.rejectionDetail()!!.contains("hands overhead: yes"))
    }

    @Test
    fun movingOnlyTheFeetIsRejected() {
        closed()

        mid()
        feed(Arms.DOWN, Feet.WIDE) // feet wide, hands never go up
        mid()
        closed()

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.INCOMPLETE_JACK, detector.lastRejectionReason)
        assertTrue(detector.rejectionDetail()!!.contains("hands overhead: no"))
    }

    @Test
    fun aTinyTwitchIsIgnoredWithoutFeedback() {
        closed()

        mid(advanceMs = 100L)
        closed(advanceMs = 100L) // 100 ms: far too short to be a real attempt

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.NONE, detector.lastRejectionReason)
        assertTrue(events.none { it is SquatEvent.RepRejected })
    }

    @Test
    fun tooFastJackIsRejected() {
        closed()

        mid(advanceMs = 100L)
        open(advanceMs = 100L)
        mid(advanceMs = 100L)
        closed(advanceMs = 100L) // 300 ms total

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.TOO_FAST, detector.lastRejectionReason)
    }

    @Test
    fun tooSlowJackIsRejected() {
        closed()

        mid(advanceMs = 1500L)
        open(advanceMs = 1500L)
        mid(advanceMs = 1500L)
        closed(advanceMs = 1500L) // 4500 ms total

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.TOO_SLOW, detector.lastRejectionReason)
    }

    @Test
    fun trackingLostMidRepRejectsTheRep() {
        closed()

        mid()
        feed(Arms.MID, Feet.MID, visible = false)
        assertTrue(detector.isTrackingLost)
        open()
        mid()
        closed()

        assertEquals(0, detector.repCount)
        assertEquals(RejectionReason.TRACKING_LOST_DURING_REP, detector.lastRejectionReason)
        assertTrue(events.contains(SquatEvent.TrackingLost))
        assertTrue(events.contains(SquatEvent.TrackingRegained))
    }

    @Test
    fun resetClearsTheCount() {
        closed()
        jack()
        assertEquals(1, detector.repCount)

        detector.reset()

        assertEquals(0, detector.repCount)
        assertEquals(SquatState.STANDING, detector.currentState)
    }
}