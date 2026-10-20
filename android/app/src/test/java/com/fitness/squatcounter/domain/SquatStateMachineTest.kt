package com.fitness.squatcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SquatStateMachineTest {

    private lateinit var config: SquatConfig
    private lateinit var stateMachine: SquatStateMachine
    private val recordedEvents = mutableListOf<SquatEvent>()

    @Before
    fun setUp() {
        config = SquatConfig(
            standingKneeAngle = 160.0f,
            bottomKneeAngle = 95.0f,
            hysteresis = 5.0f,
            requireHipsAtKneeLevel = true,
            smoothingAlpha = 1.0f // Pass-through for synthetic sequence tests
        )
        stateMachine = SquatStateMachine(config)
        recordedEvents.clear()
        stateMachine.onEventListener = { recordedEvents.add(it) }
    }

    private fun feedFrame(
        kneeAngle: Float,
        hipsAtOrBelowKnees: Boolean = false,
        isFullBodyVisible: Boolean = true
    ) {
        stateMachine.processFrame(
            PoseFeatures(
                rawKneeAngle = kneeAngle,
                smoothedKneeAngle = kneeAngle,
                rawHipAngle = 170.0f,
                smoothedHipAngle = 170.0f,
                areHipsAtOrBelowKnees = hipsAtOrBelowKnees,
                isFullBodyVisible = isFullBodyVisible
            )
        )
    }

    @Test
    fun testInitialStateIsStanding() {
        assertEquals(SquatState.STANDING, stateMachine.currentState)
        assertEquals(0, stateMachine.repCount)
    }

    @Test
    fun testFullValidSquatRepCountsOne() {
        // 1. Standing
        feedFrame(kneeAngle = 175.0f, hipsAtOrBelowKnees = false)
        assertEquals(SquatState.STANDING, stateMachine.currentState)

        // 2. Descending (crosses 160 - 5 = 155°)
        feedFrame(kneeAngle = 150.0f, hipsAtOrBelowKnees = false)
        assertEquals(SquatState.DESCENDING, stateMachine.currentState)

        feedFrame(kneeAngle = 120.0f, hipsAtOrBelowKnees = false)
        assertEquals(SquatState.DESCENDING, stateMachine.currentState)

        // 3. Reaches bottom (crosses <= 95°) with hips at or below knees
        feedFrame(kneeAngle = 90.0f, hipsAtOrBelowKnees = true)
        assertEquals(SquatState.BOTTOM, stateMachine.currentState)

        feedFrame(kneeAngle = 85.0f, hipsAtOrBelowKnees = true)
        assertEquals(SquatState.BOTTOM, stateMachine.currentState)

        // 4. Ascending (crosses 95 + 5 = 100°)
        feedFrame(kneeAngle = 105.0f, hipsAtOrBelowKnees = false)
        assertEquals(SquatState.ASCENDING, stateMachine.currentState)

        feedFrame(kneeAngle = 135.0f, hipsAtOrBelowKnees = false)
        assertEquals(SquatState.ASCENDING, stateMachine.currentState)

        // 5. Returns to standing (crosses >= 160 - 5 = 155°)
        feedFrame(kneeAngle = 165.0f, hipsAtOrBelowKnees = false)
        assertEquals(SquatState.STANDING, stateMachine.currentState)
        assertEquals(1, stateMachine.repCount)
        assertEquals(RejectionReason.NONE, stateMachine.lastRejectionReason)

        val repEvents = recordedEvents.filterIsInstance<SquatEvent.RepValidated>()
        assertEquals(1, repEvents.size)
        assertEquals(1, repEvents[0].repCount)
    }

    @Test
    fun testMultipleConsecutiveValidReps() {
        // Rep 1
        feedFrame(175f, false)
        feedFrame(140f, false)
        feedFrame(88f, true)
        feedFrame(110f, false)
        feedFrame(165f, false)
        assertEquals(1, stateMachine.repCount)

        // Rep 2
        feedFrame(140f, false)
        feedFrame(85f, true)
        feedFrame(110f, false)
        feedFrame(165f, false)
        assertEquals(2, stateMachine.repCount)

        // Rep 3
        feedFrame(140f, false)
        feedFrame(80f, true)
        feedFrame(110f, false)
        feedFrame(165f, false)
        assertEquals(3, stateMachine.repCount)
    }

    @Test
    fun testHalfRepRejectedWhenNotDeepEnough() {
        // Starts descending
        feedFrame(kneeAngle = 175.0f, hipsAtOrBelowKnees = false)
        feedFrame(kneeAngle = 140.0f, hipsAtOrBelowKnees = false)
        assertEquals(SquatState.DESCENDING, stateMachine.currentState)

        // Only goes down to 115° (did NOT reach bottom threshold 95°)
        feedFrame(kneeAngle = 115.0f, hipsAtOrBelowKnees = false)
        assertEquals(SquatState.DESCENDING, stateMachine.currentState)

        // Turns around early: increases past 115 + 7.5 = 122.5°
        feedFrame(kneeAngle = 125.0f, hipsAtOrBelowKnees = false)
        assertEquals(SquatState.ASCENDING, stateMachine.currentState)

        // Returns to standing
        feedFrame(kneeAngle = 165.0f, hipsAtOrBelowKnees = false)
        assertEquals(SquatState.STANDING, stateMachine.currentState)

        // Assert rep was rejected
        assertEquals(0, stateMachine.repCount)
        assertEquals(RejectionReason.NOT_DEEP_ENOUGH, stateMachine.lastRejectionReason)

        val rejectedEvents = recordedEvents.filterIsInstance<SquatEvent.RepRejected>()
        assertEquals(1, rejectedEvents.size)
        assertEquals(RejectionReason.NOT_DEEP_ENOUGH, rejectedEvents[0].reason)
    }

    @Test
    fun testRejectedWhenHipsDoNotReachKneeLevel() {
        // User bends knees to 90°, but hips never reach knee level
        feedFrame(kneeAngle = 175.0f, hipsAtOrBelowKnees = false)
        feedFrame(kneeAngle = 140.0f, hipsAtOrBelowKnees = false)
        feedFrame(kneeAngle = 90.0f, hipsAtOrBelowKnees = false) // bottom reached, but hips didn't drop
        feedFrame(kneeAngle = 110.0f, hipsAtOrBelowKnees = false)
        feedFrame(kneeAngle = 165.0f, hipsAtOrBelowKnees = false)

        assertEquals(0, stateMachine.repCount)
        assertEquals(RejectionReason.NOT_DEEP_ENOUGH, stateMachine.lastRejectionReason)
    }

    @Test
    fun testTrackingLostMidRepRejectsRep() {
        feedFrame(kneeAngle = 175.0f, hipsAtOrBelowKnees = false)
        feedFrame(kneeAngle = 140.0f, hipsAtOrBelowKnees = false)
        assertEquals(SquatState.DESCENDING, stateMachine.currentState)

        // User drops out of camera mid-rep
        feedFrame(kneeAngle = 120.0f, hipsAtOrBelowKnees = false, isFullBodyVisible = false)
        assertTrue(stateMachine.isTrackingLost)

        // User comes back into camera and stands up
        feedFrame(kneeAngle = 120.0f, hipsAtOrBelowKnees = false, isFullBodyVisible = true)
        feedFrame(kneeAngle = 165.0f, hipsAtOrBelowKnees = false, isFullBodyVisible = true)

        assertEquals(SquatState.STANDING, stateMachine.currentState)
        assertEquals(0, stateMachine.repCount)
        assertEquals(RejectionReason.TRACKING_LOST_DURING_REP, stateMachine.lastRejectionReason)

        assertTrue(recordedEvents.contains(SquatEvent.TrackingLost))
        assertTrue(recordedEvents.contains(SquatEvent.TrackingRegained))
    }

    @Test
    fun testHysteresisPreventsJitterAtStandingBoundary() {
        feedFrame(kneeAngle = 170.0f)
        assertEquals(SquatState.STANDING, stateMachine.currentState)

        // Oscillate near 158° (above 160 - 5 = 155°)
        feedFrame(kneeAngle = 158.0f)
        assertEquals(SquatState.STANDING, stateMachine.currentState)
        feedFrame(kneeAngle = 156.0f)
        assertEquals(SquatState.STANDING, stateMachine.currentState)
        feedFrame(kneeAngle = 159.0f)
        assertEquals(SquatState.STANDING, stateMachine.currentState)

        // Only transitions when drops strictly below 155°
        feedFrame(kneeAngle = 154.0f)
        assertEquals(SquatState.DESCENDING, stateMachine.currentState)
    }
}
