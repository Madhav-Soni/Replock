
package com.fitness.squatcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CloseUpSquatStateMachineTest {

    private lateinit var machine: CloseUpSquatStateMachine
    private val events = mutableListOf<SquatEvent>()
    private var now = 1000L

    // Standing: hips at 0.50 of frame height, torso 0.25 long.
    // Drop ratio = (hipY - 0.50) / 0.25, so hipY 0.66 is a drop of 0.64 torso lengths.

    @Before
    fun setUp() {
        machine = CloseUpSquatStateMachine(
            CloseUpSquatConfig(smoothingAlpha = 1f, calibrationFrames = 3)
        )
        events.clear()
        now = 1000L
        machine.onEventListener = { events.add(it) }
    }

    private fun feed(
        hipY: Float,
        torso: Float = 0.25f,
        visible: Boolean = true,
        advanceMs: Long = 300L
    ) {
        now += advanceMs
        machine.processFrame(
            UpperBodyFeatures(
                hipY = hipY,
                shoulderY = hipY - torso,
                torsoLength = torso,
                isVisible = visible
            ),
            timestampMs = now
        )
    }

    private fun calibrate() {
        repeat(3) { feed(0.50f) }
    }

    @Test
    fun noCountingWhileCalibrating() {
        feed(0.50f)
        feed(0.50f)

        assertFalse(machine.isCalibrated)
        assertEquals(SquatState.STANDING, machine.currentState)
        assertEquals(0, machine.repCount)
    }

    @Test
    fun calibrationCompletesAfterConfiguredFrames() {
        calibrate()
        assertTrue(machine.isCalibrated)
    }

    @Test
    fun validRepIsCounted() {
        calibrate()

        feed(0.56f) // drop 0.24: DESCENDING
        assertEquals(SquatState.DESCENDING, machine.currentState)
        feed(0.66f) // drop 0.64: BOTTOM
        assertEquals(SquatState.BOTTOM, machine.currentState)
        feed(0.58f) // drop 0.32: ASCENDING
        assertEquals(SquatState.ASCENDING, machine.currentState)
        feed(0.51f) // drop 0.04: STANDING, rep took 900 ms

        assertEquals(SquatState.STANDING, machine.currentState)
        assertEquals(1, machine.repCount)
        assertEquals(RejectionReason.NONE, machine.lastRejectionReason)
        assertTrue(events.contains(SquatEvent.RepValidated(1)))
    }

    @Test
    fun twoRepsAreBothCounted() {
        calibrate()
        repeat(2) {
            feed(0.56f)
            feed(0.66f)
            feed(0.58f)
            feed(0.51f)
        }

        assertEquals(2, machine.repCount)
    }

    @Test
    fun halfRepIsRejectedAsNotDeepEnough() {
        calibrate()

        feed(0.56f) // DESCENDING
        feed(0.60f) // drop 0.40: not deep enough for the bottom (0.45)
        feed(0.56f) // turns back up: ASCENDING
        feed(0.50f) // STANDING

        assertEquals(0, machine.repCount)
        assertEquals(RejectionReason.NOT_DEEP_ENOUGH, machine.lastRejectionReason)
    }

    @Test
    fun tooFastRepIsRejected() {
        calibrate()

        feed(0.56f, advanceMs = 100L)
        feed(0.66f, advanceMs = 100L)
        feed(0.58f, advanceMs = 100L)
        feed(0.50f, advanceMs = 100L) // 300 ms total

        assertEquals(0, machine.repCount)
        assertEquals(RejectionReason.TOO_FAST, machine.lastRejectionReason)
    }

    @Test
    fun tooSlowRepIsRejected() {
        calibrate()

        feed(0.56f)
        feed(0.66f, advanceMs = 3000L)
        feed(0.58f, advanceMs = 3000L)
        feed(0.50f, advanceMs = 3000L) // 9000 ms total

        assertEquals(0, machine.repCount)
        assertEquals(RejectionReason.TOO_SLOW, machine.lastRejectionReason)
    }

    @Test
    fun trackingLostMidRepRejectsTheRep() {
        calibrate()

        feed(0.56f) // DESCENDING
        feed(0f, visible = false)
        assertTrue(machine.isTrackingLost)
        feed(0.66f) // back in view: BOTTOM
        feed(0.58f)
        feed(0.50f)

        assertEquals(0, machine.repCount)
        assertEquals(RejectionReason.TRACKING_LOST_DURING_REP, machine.lastRejectionReason)
        assertTrue(events.contains(SquatEvent.TrackingLost))
        assertTrue(events.contains(SquatEvent.TrackingRegained))
    }

    @Test
    fun movingTowardCameraDuringRepIsRejected() {
        calibrate()

        feed(0.56f)
        feed(0.66f, torso = 0.40f) // torso 1.6x bigger: walked toward the camera
        feed(0.58f)
        feed(0.50f)

        assertEquals(0, machine.repCount)
        assertEquals(RejectionReason.SCALE_CHANGED, machine.lastRejectionReason)
    }

    @Test
    fun smallShiftWhileStandingDoesNotStartARep() {
        calibrate()

        repeat(5) { feed(0.52f) } // drop 0.08, below the start threshold of 0.18

        assertEquals(SquatState.STANDING, machine.currentState)
        assertEquals(0, machine.repCount)
        assertTrue(events.none { it is SquatEvent.StateChanged })
    }

    @Test
    fun resetClearsCountAndRecalibrates() {
        calibrate()
        feed(0.56f)
        feed(0.66f)
        feed(0.58f)
        feed(0.51f)
        assertEquals(1, machine.repCount)

        machine.reset()

        assertEquals(0, machine.repCount)
        assertFalse(machine.isCalibrated)
        assertEquals(SquatState.STANDING, machine.currentState)
    }
}