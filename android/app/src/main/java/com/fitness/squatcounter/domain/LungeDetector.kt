package com.fitness.squatcounter.domain

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Thresholds for lunges seen from the front (phone propped up facing you).
 * Drops and gaps are in torso lengths: 0.5 means half the distance from shoulders to hips.
 * These are starting guesses and must be tuned on a real phone with the Debug panel.
 */
data class LungeConfig(
    /** Hips dropping at least this far from standing starts a rep. */
    val startDropRatio: Float = 0.15f,

    /** Hips back within this of standing finishes a rep. Also the "stable standing" band. */
    val standingDropRatio: Float = 0.08f,

    /** Hips must drop at least this far for the rep to count. */
    val bottomDropRatio: Float = 0.35f,

    /** Drop must fall this far below the bottom threshold before ASCENDING begins. */
    val ascendHysteresis: Float = 0.08f,

    /** Turning back up this far below the deepest point before the bottom = half rep. */
    val halfRepReversal: Float = 0.10f,

    /**
     * Anti-squat: at the bottom, one knee must be at least this much higher than the other
     * (in torso lengths). In a squat both knees stay level.
     */
    val minKneeGap: Float = 0.20f,

    /** Torso size (relative to standing) must stay in this range during a rep. */
    val minTorsoScale: Float = 0.55f,
    val maxTorsoScale: Float = 1.45f,

    /** Standing frames averaged at the start of a session to measure the baseline. */
    val calibrationFrames: Int = 5,

    /** How quickly the standing baseline follows slow drift while standing. */
    val baselineAlpha: Float = 0.01f,

    /** Smoothing of the measurements. 1.0 = no smoothing. */
    val smoothingAlpha: Float = 0.35f,

    val minRepDurationMs: Long = 800L,
    val maxRepDurationMs: Long = 8000L
)

/**
 * Counts lunges from the front. Needs shoulders, hips and both knees visible.
 *
 * A rep counts when the hips drop far enough AND the two knees end up at clearly different
 * heights (one leg forward, one back). That second check is what separates a lunge from a
 * squat, so squats do not earn lunge credit. Either leg counts, so alternate freely.
 *
 * The first frames of a session (user standing) set the baseline. All distances are in torso
 * lengths, so it works at any distance from the phone.
 */
class LungeDetector(
    val config: LungeConfig = LungeConfig()
) : ExerciseDetector {

    override var currentState: SquatState = SquatState.STANDING
        private set

    override var repCount: Int = 0
        private set

    override var lastRejectionReason: RejectionReason = RejectionReason.NONE
        private set

    override var onEventListener: ((SquatEvent) -> Unit)? = null

    var isTrackingLost: Boolean = false
        private set

    var isCalibrated: Boolean = false
        private set

    private var dropRatio = 0f
    private var kneeGap = 0f
    private var torsoScale = 1f
    private var peakDropSinceStart = 0f
    private var lastRepPeakDrop = 0f
    private var lastRepKneeGap = 0f

    private var baseHipY = 0f
    private var baseTorso = 1f
    private var calibrationCount = 0
    private var calibrationHipSum = 0f
    private var calibrationTorsoSum = 0f

    private var smoothedHipY = 0f
    private var smoothedTorso = 0f
    private var smoothedKneeGap = 0f
    private var smoothingReady = false

    private var maxDropInRep = 0f
    private var kneeGapAtPeak = 0f
    private var hasReachedBottom = false
    private var minScaleInRep = 1f
    private var maxScaleInRep = 1f
    private var trackingLostDuringRep = false
    private var repStartTimeMs = 0L

    private data class Measure(val hipY: Float, val torso: Float, val kneeGap: Float)

    override fun isBodyVisible(landmarks: List<LandmarkPoint>, visibilityThreshold: Float): Boolean =
        measure(landmarks, visibilityThreshold) != null

    override fun reset() {
        currentState = SquatState.STANDING
        repCount = 0
        lastRejectionReason = RejectionReason.NONE
        isTrackingLost = false
        isCalibrated = false
        dropRatio = 0f
        kneeGap = 0f
        torsoScale = 1f
        peakDropSinceStart = 0f
        lastRepPeakDrop = 0f
        lastRepKneeGap = 0f
        baseHipY = 0f
        baseTorso = 1f
        calibrationCount = 0
        calibrationHipSum = 0f
        calibrationTorsoSum = 0f
        smoothedHipY = 0f
        smoothedTorso = 0f
        smoothedKneeGap = 0f
        smoothingReady = false
        maxDropInRep = 0f
        kneeGapAtPeak = 0f
        hasReachedBottom = false
        minScaleInRep = 1f
        maxScaleInRep = 1f
        trackingLostDuringRep = false
        repStartTimeMs = 0L
    }

    override fun processFrame(
        landmarks: List<LandmarkPoint>,
        timestampMs: Long,
        visibilityThreshold: Float
    ) {
        val measure = measure(landmarks, visibilityThreshold)

        // 1. Tracking check
        if (measure == null) {
            if (!isTrackingLost) {
                isTrackingLost = true
                emit(SquatEvent.TrackingLost)
            }
            if (currentState != SquatState.STANDING) {
                trackingLostDuringRep = true
            }
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

        smooth(measure)

        // 2. Calibration: measure the standing baseline, no counting yet
        if (!isCalibrated) {
            calibrationHipSum += smoothedHipY
            calibrationTorsoSum += smoothedTorso
            calibrationCount++
            if (calibrationCount >= config.calibrationFrames) {
                baseHipY = calibrationHipSum / calibrationCount
                baseTorso = calibrationTorsoSum / calibrationCount
                isCalibrated = true
            }
            return
        }

        dropRatio = (smoothedHipY - baseHipY) / baseTorso
        kneeGap = smoothedKneeGap / baseTorso
        torsoScale = smoothedTorso / baseTorso
        peakDropSinceStart = max(peakDropSinceStart, dropRatio)

        // 3. State transitions
        when (currentState) {
            SquatState.STANDING -> {
                if (dropRatio >= config.startDropRatio) {
                    maxDropInRep = dropRatio
                    kneeGapAtPeak = kneeGap
                    hasReachedBottom = false
                    minScaleInRep = torsoScale
                    maxScaleInRep = torsoScale
                    trackingLostDuringRep = false
                    repStartTimeMs = timestampMs
                    transitionTo(SquatState.DESCENDING)
                } else if (dropRatio < config.standingDropRatio) {
                    // Stable standing: follow slow drift
                    baseHipY += config.baselineAlpha * (smoothedHipY - baseHipY)
                    baseTorso += config.baselineAlpha * (smoothedTorso - baseTorso)
                }
            }

            SquatState.DESCENDING -> {
                trackRep()
                if (dropRatio >= config.bottomDropRatio) {
                    hasReachedBottom = true
                    transitionTo(SquatState.BOTTOM)
                } else if (dropRatio <= maxDropInRep - config.halfRepReversal) {
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
                    // Went down again (a stutter or bounce): same rep, back at the bottom.
                    hasReachedBottom = true
                    transitionTo(SquatState.BOTTOM)
                } else if (dropRatio <= config.standingDropRatio) {
                    validateAndFinishRep(timestampMs)
                    transitionTo(SquatState.STANDING)
                }
            }
        }
    }

    override fun rejectionDetail(): String? = when (lastRejectionReason) {
        RejectionReason.NOT_DEEP_ENOUGH ->
            "hips dropped %.2f, needs %.2f".format(lastRepPeakDrop, config.bottomDropRatio)

        RejectionReason.NOT_A_LUNGE ->
            "knee height gap %.2f, needs %.2f (one knee lower than the other)".format(
                lastRepKneeGap,
                config.minKneeGap
            )

        else -> null
    }

    override fun debugInfo(): String = listOf(
        "Calib   ${if (isCalibrated) "yes" else "no"}",
        "Drop    ${"%.2f".format(dropRatio)}",
        "KneeGap ${"%.2f".format(kneeGap)}",
        "Scale   ${"%.2f".format(torsoScale)}",
        "PeakDrop ${"%.2f".format(peakDropSinceStart)}"
    ).joinToString("\n")

    private fun measure(landmarks: List<LandmarkPoint>, threshold: Float): Measure? {
        if (landmarks.size <= RIGHT_KNEE) return null

        val leftShoulder = landmarks[LEFT_SHOULDER]
        val rightShoulder = landmarks[RIGHT_SHOULDER]
        val leftHip = landmarks[LEFT_HIP]
        val rightHip = landmarks[RIGHT_HIP]
        val leftKnee = landmarks[LEFT_KNEE]
        val rightKnee = landmarks[RIGHT_KNEE]

        val allVisible = leftShoulder.visibility >= threshold &&
            rightShoulder.visibility >= threshold &&
            leftHip.visibility >= threshold &&
            rightHip.visibility >= threshold &&
            leftKnee.visibility >= threshold &&
            rightKnee.visibility >= threshold
        if (!allVisible) return null

        val shoulderMidX = (leftShoulder.x + rightShoulder.x) / 2f
        val shoulderMidY = (leftShoulder.y + rightShoulder.y) / 2f
        val hipMidX = (leftHip.x + rightHip.x) / 2f
        val hipMidY = (leftHip.y + rightHip.y) / 2f

        val torso = hypot((shoulderMidX - hipMidX) * CAMERA_FRAME_ASPECT, shoulderMidY - hipMidY)
        if (torso < MIN_TORSO_LENGTH) return null

        return Measure(
            hipY = hipMidY,
            torso = torso,
            kneeGap = abs(leftKnee.y - rightKnee.y)
        )
    }

    private fun smooth(measure: Measure) {
        if (!smoothingReady) {
            smoothedHipY = measure.hipY
            smoothedTorso = measure.torso
            smoothedKneeGap = measure.kneeGap
            smoothingReady = true
        } else {
            val alpha = config.smoothingAlpha
            smoothedHipY += alpha * (measure.hipY - smoothedHipY)
            smoothedTorso += alpha * (measure.torso - smoothedTorso)
            smoothedKneeGap += alpha * (measure.kneeGap - smoothedKneeGap)
        }
    }

    private fun trackRep() {
        if (dropRatio > maxDropInRep) {
            maxDropInRep = dropRatio
            kneeGapAtPeak = kneeGap
        }
        minScaleInRep = min(minScaleInRep, torsoScale)
        maxScaleInRep = max(maxScaleInRep, torsoScale)
    }

    private fun validateAndFinishRep(timestampMs: Long) {
        lastRepPeakDrop = maxDropInRep
        lastRepKneeGap = kneeGapAtPeak

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

        if (minScaleInRep < config.minTorsoScale || maxScaleInRep > config.maxTorsoScale) {
            reject(RejectionReason.SCALE_CHANGED)
            return
        }

        if (!hasReachedBottom) {
            reject(RejectionReason.NOT_DEEP_ENOUGH)
            return
        }

        // Anti-squat: a lunge has one knee clearly lower than the other.
        if (kneeGapAtPeak < config.minKneeGap) {
            reject(RejectionReason.NOT_A_LUNGE)
            return
        }

        repCount++
        lastRejectionReason = RejectionReason.NONE
        emit(SquatEvent.RepValidated(repCount))
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

    private companion object {
        const val LEFT_SHOULDER = 11
        const val RIGHT_SHOULDER = 12
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        const val LEFT_KNEE = 25
        const val RIGHT_KNEE = 26

        /** Torso shorter than this (in frame heights) is treated as a bad detection. */
        const val MIN_TORSO_LENGTH = 0.01f
    }
}