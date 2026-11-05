package com.fitness.squatcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Hips reaching knee height counts as a full squat even when the 2D knee angle stays shallow.
 */
class SquatHipLevelTest {

    private var now = 1000L

    @Before
    fun setUp() {
        now = 1000L
    }

    private fun machine(acceptHipsAlone: Boolean = true) = SquatStateMachine(
        SquatConfig(
            standingKneeAngle = 160.0f,
            bottomKneeAngle = 95.0f,
            hysteresis = 5.0f,
            requireHipsAtKneeLevel = true,
            acceptHipsAtKneeLevelAlone = acceptHipsAlone,
            smoothingAlpha = 1.0f,
            minRepDurationMs = 800L,
            maxRepDurationMs = 8000L
        )
    )

    private fun SquatStateMachine.feed(kneeAngle: Float, hipsAtKneeLevel: Boolean = false) {
        now += 300L
        processFrame(
            PoseFeatures(
                rawKneeAngle = kneeAngle,
                smoothedKneeAngle = kneeAngle,
                rawHipAngle = 170.0f,
                smoothedHipAngle = 170.0f,
                areHipsAtOrBelowKnees = hipsAtKneeLevel,
                isFullBodyVisible = true
            ),
            timestampMs = now
        )
    }

    @Test
    fun hipsAtKneeHeightAloneCountsAsAFullSquat() {
        val m = machine()

        m.feed(175f)
        m.feed(140f) // DESCENDING
        m.feed(110f, hipsAtKneeLevel = true) // knee angle only 110, but hips reached knee height
        m.feed(130f) // ASCENDING
        m.feed(165f) // STANDING

        assertEquals(SquatState.STANDING, m.currentState)
        assertEquals(1, m.repCount)
        assertEquals(RejectionReason.NONE, m.lastRejectionReason)
    }

    @Test
    fun shallowSquatWithHipsAboveKneesIsStillRejected() {
        val m = machine()

        m.feed(175f)
        m.feed(140f)
        m.feed(110f, hipsAtKneeLevel = false)
        m.feed(130f)
        m.feed(165f)

        assertEquals(0, m.repCount)
        assertEquals(RejectionReason.NOT_DEEP_ENOUGH, m.lastRejectionReason)
    }

    @Test
    fun hipLevelAloneIsRejectedWhenTheOptionIsOff() {
        val m = machine(acceptHipsAlone = false)

        m.feed(175f)
        m.feed(140f)
        m.feed(110f, hipsAtKneeLevel = true)
        m.feed(130f)
        m.feed(165f)

        assertEquals(0, m.repCount)
        assertEquals(RejectionReason.NOT_DEEP_ENOUGH, m.lastRejectionReason)
    }

    @Test
    fun deepKneeAngleWithHipsAtKneeLevelStillCounts() {
        val m = machine()

        m.feed(175f)
        m.feed(140f)
        m.feed(88f, hipsAtKneeLevel = true)
        m.feed(110f)
        m.feed(165f)

        assertEquals(1, m.repCount)
    }
}