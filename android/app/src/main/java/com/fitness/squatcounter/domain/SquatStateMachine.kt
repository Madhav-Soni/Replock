package com.fitness.squatcounter.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.min

/**
 * Exercise state machine for squat rep counting and quality validation.
 *
 * Sequence:
 * STANDING -> DESCENDING -> BOTTOM -> ASCENDING -> STANDING
 *
 * Emits events via SharedFlow and an optional direct listener.
 */
class SquatStateMachine(
    val config: SquatConfig = SquatConfig()
) {
    var currentState: SquatState = SquatState.STANDING
        private set

    var repCount: Int = 0
        private set

    var isTrackingLost: Boolean = false
        private set

    var lastRejectionReason: RejectionReason = RejectionReason.NONE
        private set

    /** Smallest knee angle reached in the most recent finished rep (degrees). */
    var lastRepMinKneeAngle: Float = 180.0f
        private set

    /** Smallest knee angle since the session started (degrees). For the Debug panel. */
    var lowestKneeAngleSinceStart: Float = 180.0f
        private set

    /** Whether the hips reached knee level in the most recent finished rep. */
    var lastRepHipsReachedKneeLevel: Boolean = false
        private set

    // Internal state tracking for current rep
    private var hasReachedBottom: Boolean = false
    private var hipsReachedKneeLevel: Boolean = false
    private var minKneeAngleInRep: Float = 180.0f
    private var trackingLostDuringRep: Boolean = false
    private var repStartTimeMs: Long = 0L

    // Event streaming
    private val _events = MutableSharedFlow<SquatEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SquatEvent> = _events.asSharedFlow()

    /**
     * Optional synchronous event listener, ideal for unit tests and instant UI/Audio triggers.
     */
    var onEventListener: ((SquatEvent) -> Unit)? = null

    private fun emitEvent(event: SquatEvent) {
        _events.tryEmit(event)
        onEventListener?.invoke(event)
    }

    private fun transitionTo(newState: SquatState) {
        if (currentState != newState) {
            val old = currentState
            currentState = newState
            emitEvent(SquatEvent.StateChanged(old, newState))
        }
    }

    /**
     * Resets counters and state back to STANDING.
     */
    fun reset() {
        currentState = SquatState.STANDING
        repCount = 0
        isTrackingLost = false
        lastRejectionReason = RejectionReason.NONE
        hasReachedBottom = false
        hipsReachedKneeLevel = false
        minKneeAngleInRep = 180.0f
        lowestKneeAngleSinceStart = 180.0f
        trackingLostDuringRep = false
        repStartTimeMs = 0L
    }

    /**
     * Processes features extracted from the current frame.
     */
    fun processFrame(features: PoseFeatures, timestampMs: Long = System.currentTimeMillis()) {
        val kneeAngle = features.smoothedKneeAngle
        val hipsBelowKnees = features.areHipsAtOrBelowKnees
        val fullBodyVisible = features.isFullBodyVisible

        // 1. Full-body tracking check
        if (!fullBodyVisible) {
            if (!isTrackingLost) {
                isTrackingLost = true
                emitEvent(SquatEvent.TrackingLost)
            }
            if (currentState != SquatState.STANDING) {
                trackingLostDuringRep = true
            }
            return
        } else {
            if (isTrackingLost) {
                isTrackingLost = false
                emitEvent(SquatEvent.TrackingRegained)
            }
        }

        lowestKneeAngleSinceStart = min(lowestKneeAngleSinceStart, kneeAngle)

        // 2. State Machine transitions
        when (currentState) {
            SquatState.STANDING -> {
                // Standing boundary: kneeAngle >= standingKneeAngle - hysteresis
                if (kneeAngle < (config.standingKneeAngle - config.hysteresis)) {
                    // Beginning to descend
                    hasReachedBottom = false
                    hipsReachedKneeLevel = hipsBelowKnees
                    minKneeAngleInRep = kneeAngle
                    trackingLostDuringRep = false
                    repStartTimeMs = timestampMs
                    transitionTo(SquatState.DESCENDING)
                }
            }

            SquatState.DESCENDING -> {
                minKneeAngleInRep = min(minKneeAngleInRep, kneeAngle)
                if (hipsBelowKnees) {
                    hipsReachedKneeLevel = true
                }

                // Check if bottom depth reached
                if (kneeAngle <= config.bottomKneeAngle) {
                    hasReachedBottom = true
                    transitionTo(SquatState.BOTTOM)
                } else if (config.acceptHipsAtKneeLevelAlone && hipsBelowKnees) {
                    // Hips at knee height is a full-depth squat even if the 2D knee angle
                    // did not get down to the bottom threshold.
                    transitionTo(SquatState.BOTTOM)
                } else if (kneeAngle >= (minKneeAngleInRep + config.hysteresis * 1.5f)) {
                    // User started moving back up before reaching bottom threshold -> Half Rep!
                    transitionTo(SquatState.ASCENDING)
                }
            }

            SquatState.BOTTOM -> {
                minKneeAngleInRep = min(minKneeAngleInRep, kneeAngle)
                if (hipsBelowKnees) {
                    hipsReachedKneeLevel = true
                }

                // Ascending when knee angle extends past bottom threshold + hysteresis
                if (kneeAngle > (config.bottomKneeAngle + config.hysteresis)) {
                    transitionTo(SquatState.ASCENDING)
                }
            }

            SquatState.ASCENDING -> {
                if (hipsBelowKnees) {
                    hipsReachedKneeLevel = true
                }

                // Returned to standing upright
                if (kneeAngle >= (config.standingKneeAngle - config.hysteresis)) {
                    validateAndFinishRep(timestampMs)
                    transitionTo(SquatState.STANDING)
                }
            }
        }
    }

    private fun validateAndFinishRep(timestampMs: Long) {
        lastRepMinKneeAngle = minKneeAngleInRep
        lastRepHipsReachedKneeLevel = hipsReachedKneeLevel
        if (trackingLostDuringRep) {
            lastRejectionReason = RejectionReason.TRACKING_LOST_DURING_REP
            emitEvent(SquatEvent.RepRejected(RejectionReason.TRACKING_LOST_DURING_REP))
            return
        }

        val repDurationMs = if (repStartTimeMs > 0L) timestampMs - repStartTimeMs else 0L

        if (repDurationMs < config.minRepDurationMs) {
            lastRejectionReason = RejectionReason.TOO_FAST
            emitEvent(SquatEvent.RepRejected(RejectionReason.TOO_FAST))
            return
        }

        if (repDurationMs > config.maxRepDurationMs) {
            lastRejectionReason = RejectionReason.TOO_SLOW
            emitEvent(SquatEvent.RepRejected(RejectionReason.TOO_SLOW))
            return
        }

        // Depth: knee angle reached the bottom (with hips at knee level if required),
        // OR the hips reached knee height on their own (when that option is on)
        val kneeDepthOk = hasReachedBottom && (!config.requireHipsAtKneeLevel || hipsReachedKneeLevel)
        val hipLevelOk = config.acceptHipsAtKneeLevelAlone && hipsReachedKneeLevel
        val depthSatisfied = kneeDepthOk || hipLevelOk

        if (depthSatisfied) {
            repCount++
            lastRejectionReason = RejectionReason.NONE
            emitEvent(SquatEvent.RepValidated(repCount))
        } else {
            lastRejectionReason = RejectionReason.NOT_DEEP_ENOUGH
            emitEvent(SquatEvent.RepRejected(RejectionReason.NOT_DEEP_ENOUGH))
        }
    }
}