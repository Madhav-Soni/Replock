package com.fitness.squatcounter.domain

import kotlin.math.max
import kotlin.math.min

/**
 * Thresholds for close-up squat counting (shoulders and hips only).
 * All drop values are in torso lengths: 0.5 means the hips dropped half a torso length.
 * These are starting guesses and must be tuned on a real phone using the Debug panel.
 */
data class CloseUpSquatConfig(
    /** Hips dropping at least this far from standing starts a rep. */
    val startDropRatio: Float = 0.18f,

    /** Hips back within this of standing finishes a rep. Also the "stable standing" band. */
    val standingDropRatio: Float = 0.10f,

    /** Hips must drop at least this far during the rep for it to count. */
    val bottomDropRatio: Float = 0.45f,

    /** Drop must fall this far below the bottom threshold before ASCENDING begins. */
    val ascendHysteresis: Float = 0.08f,

    /** Turning back up this far below the deepest point before the bottom = half rep. */
    val halfRepReversal: Float = 0.10f,

    /** Torso size (relative to standing) must stay in this range during a rep. */
    val minTorsoScale: Float = 0.55f,
    val maxTorsoScale: Float = 1.45f,

    /** Standing frames averaged at the start of a session to measure the baseline. */
    val calibrationFrames: Int = 5,

    /** How quickly the standing baseline follows slow drift while standing. */
    val baselineAlpha: Float = 0.01f,

    /** Smoothing of hip height and torso size. 1.0 = no smoothing. */
    val smoothingAlpha: Float = 0.35f,

    val minRepDurationMs: Long = 800L,
    val maxRepDurationMs: Long = 8000L
)

/**
 * Counts squats from hip movement only, so it works when the legs are out of frame.
 *
 * At the start of a session the first few frames (user standing) set a baseline for hip height
 * and torso length. A rep is counted when the hips drop far enough relative to the torso and
 * come back up. Drop is measured in torso lengths, so it works at any distance.
 *
 * Emits the same [SquatEvent]s as [SquatStateMachine]. Not thread-safe.
 */
class CloseUpSquatStateMachine(
    val config: CloseUpSquatConfig = CloseUpSquatConfig()
) {
    var currentState: SquatState = SquatState.STANDING
        private set

    var repCount: Int = 0
        private set

    var isTrackingLost: Boolean = false
        private set

    var lastRejectionReason: RejectionReason = RejectionReason.NONE
        private set

    var isCalibrated: Boolean = false
        private set

    /** Current hip drop from standing, in torso lengths. For the Debug panel. */
    var dropRatio: Float = 0f
        private set

    /** Current torso size relative to standing (1.0 = same). For the Debug panel. */
    var torsoScale: Float = 1f
        private set

    /** Deepest hip drop reached in the most recent finished rep (torso lengths). */
    var lastRepPeakDrop: Float = 0f
        private set

    var onEventListener: ((SquatEvent) -> Unit)? = null

    private var baselineHipY = 0f
    private var baselineTorso = 1f
    private var calibrationCount = 0
    private var calibrationHipSum = 0f
    private var calibrationTorsoSum = 0f

    private var smoothedHipY = 0f
    private var smoothedTorso = 0f
    private var smoothingReady = false

    private var maxDropInRep = 0f
    private var hasReachedBottom = false
    private var minScaleInRep = 1f
    private var maxScaleInRep = 1f
    private var trackingLostDuringRep = false
    private var repStartTimeMs = 0L

    /** Back to the start of a session: counters cleared and the baseline measured again. */
    fun reset() {
        currentState = SquatState.STANDING
        repCount = 0
        isTrackingLost = false
        lastRejectionReason = RejectionReason.NONE
        isCalibrated = false
        dropRatio = 0f
        torsoScale = 1f
        lastRepPeakDrop = 0f
        baselineHipY = 0f
        baselineTorso = 1f
        calibrationCount = 0
        calibrationHipSum = 0f
        calibrationTorsoSum = 0f
        smoothedHipY = 0f
        smoothedTorso = 0f
        smoothingReady = false
        maxDropInRep = 0f
        hasReachedBottom = false
        minScaleInRep = 1f
        maxScaleInRep = 1f
        trackingLostDuringRep = false
        repStartTimeMs = 0L
    }

    fun processFrame(features: UpperBodyFeatures, timestampMs: Long) {
        // 1. Tracking check
        if (!features.isVisible) {
            if (!isTrackingLost) {
                isTrackingLost = true
                emit(SquatEvent.TrackingLost)
            }
            if (currentState != SquatState.STANDING) {
                trackingLostDuringRep = true
            }
            // Old values must not leak into the frames after the gap.
            smoothingReady = false
            if (!isCalibrated) {
                calibrationCount = 0
                calibrationHipSum = 0f
                calibrationTorsoSum = 0f
            }
            return
        }

        if (isTrackingLost) {
            isTrackingLost = false
            emit(SquatEvent.TrackingRegained)
        }

        smooth(features)

        // 2. Calibration: measure the standing baseline, no counting yet
        if (!isCalibrated) {
            calibrationHipSum += smoothedHipY
            calibrationTorsoSum += smoothedTorso
            calibrationCount++
            if (calibrationCount >= config.calibrationFrames) {
                baselineHipY = calibrationHipSum / calibrationCount
                baselineTorso = calibrationTorsoSum / calibrationCount
                isCalibrated = true
            }
            return
        }

        dropRatio = (smoothedHipY - baselineHipY) / baselineTorso
        torsoScale = smoothedTorso / baselineTorso

        // 3. State transitions
        when (currentState) {
            SquatState.STANDING -> {
                if (dropRatio >= config.startDropRatio) {
                    maxDropInRep = dropRatio
                    hasReachedBottom = false
                    minScaleInRep = torsoScale
                    maxScaleInRep = torsoScale
                    trackingLostDuringRep = false
                    repStartTimeMs = timestampMs
                    transitionTo(SquatState.DESCENDING)
                } else if (dropRatio < config.standingDropRatio) {
                    // Stable standing: follow slow drift (user shifts, phone settles)
                    baselineHipY += config.baselineAlpha * (smoothedHipY - baselineHipY)
                    baselineTorso += config.baselineAlpha * (smoothedTorso - baselineTorso)
                }
            }

            SquatState.DESCENDING -> {
                trackRep()
                if (dropRatio >= config.bottomDropRatio) {
                    hasReachedBottom = true
                    transitionTo(SquatState.BOTTOM)
                } else if (dropRatio <= maxDropInRep - config.halfRepReversal) {
                    // Turned back up before reaching the bottom: half rep
                    transitionTo(SquatState.ASCENDING)
                }
            }

            SquatState.BOTTOM -> {
                trackRep()
                if (dropRatio < config.bottomDropRatio - config.ascendHysteresis) {
                    transitionTo(SquatState.ASCENDING)
                }
            }

            SquatState.ASCENDING -> {
                trackRep()
                if (dropRatio >= config.bottomDropRatio) {
                    // Went down again (a stutter or bounce): still the same rep, back at the bottom.
                    hasReachedBottom = true
                    transitionTo(SquatState.BOTTOM)
                } else if (dropRatio <= config.standingDropRatio) {
                    validateAndFinishRep(timestampMs)
                    transitionTo(SquatState.STANDING)
                }
            }
        }
    }

    private fun smooth(features: UpperBodyFeatures) {
        if (!smoothingReady) {
            smoothedHipY = features.hipY
            smoothedTorso = features.torsoLength
            smoothingReady = true
        } else {
            val alpha = config.smoothingAlpha
            smoothedHipY += alpha * (features.hipY - smoothedHipY)
            smoothedTorso += alpha * (features.torsoLength - smoothedTorso)
        }
    }

    private fun trackRep() {
        maxDropInRep = max(maxDropInRep, dropRatio)
        minScaleInRep = min(minScaleInRep, torsoScale)
        maxScaleInRep = max(maxScaleInRep, torsoScale)
    }

    private fun validateAndFinishRep(timestampMs: Long) {
        lastRepPeakDrop = maxDropInRep
        if (trackingLostDuringRep) {
            reject(RejectionReason.TRACKING_LOST_DURING_REP)
            return
        }

        val repDurationMs = if (repStartTimeMs > 0L) timestampMs - repStartTimeMs else 0L

        if (repDurationMs < config.minRepDurationMs) {
            reject(RejectionReason.TOO_FAST)
            return
        }
        if (repDurationMs > config.maxRepDurationMs) {
            reject(RejectionReason.TOO_SLOW)
            return
        }

        // Walking toward or away from the camera changes torso size a lot.
        if (minScaleInRep < config.minTorsoScale || maxScaleInRep > config.maxTorsoScale) {
            reject(RejectionReason.SCALE_CHANGED)
            return
        }

        if (hasReachedBottom) {
            repCount++
            lastRejectionReason = RejectionReason.NONE
            emit(SquatEvent.RepValidated(repCount))
        } else {
            reject(RejectionReason.NOT_DEEP_ENOUGH)
        }
    }

    private fun reject(reason: RejectionReason) {
        lastRejectionReason = reason
        emit(SquatEvent.RepRejected(reason))
    }

    private fun transitionTo(newState: SquatState) {
        if (currentState != newState) {
            val old = currentState
            currentState = newState
            emit(SquatEvent.StateChanged(old, newState))
        }
    }

    private fun emit(event: SquatEvent) {
        onEventListener?.invoke(event)
    }
}