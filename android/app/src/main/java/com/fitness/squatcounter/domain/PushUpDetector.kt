package com.fitness.squatcounter.domain

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Thresholds for push-ups seen from the front (phone on the floor facing you).
 * All drops are in shoulder widths: 0.5 means half a shoulder width.
 * These are starting guesses and must be tuned on a real phone with the Debug panel.
 */
data class PushUpConfig(
    /** Nose dropping at least this far from the top position starts a rep. */
    val startDropRatio: Float = 0.15f,

    /** Nose back within this of the top position finishes a rep. Also the "stable at top" band. */
    val topDropRatio: Float = 0.08f,

    /** The nose must drop at least this far for the rep to count. */
    val bottomDropRatio: Float = 0.40f,

    /** Drop must fall this far below the bottom threshold before ASCENDING begins. */
    val ascendHysteresis: Float = 0.08f,

    /** Turning back up this far below the deepest point before the bottom = half rep. */
    val halfRepReversal: Float = 0.10f,

    /** Anti-fake: shoulders must drop at least this fraction of how far the nose dropped. */
    val minShoulderDropFraction: Float = 0.5f,

    /** Shoulder width (relative to the top position) must stay in this range during a rep. */
    val minWidthScale: Float = 0.6f,
    val maxWidthScale: Float = 1.6f,

    /** Frames averaged at the start of a session to measure the top position. */
    val calibrationFrames: Int = 8,

    /** How quickly the top-position baseline follows slow drift. */
    val baselineAlpha: Float = 0.01f,

    /** Smoothing of the measurements. 1.0 = no smoothing. */
    val smoothingAlpha: Float = 0.35f,

    val minRepDurationMs: Long = 1000L,
    val maxRepDurationMs: Long = 8000L
)

/**
 * Counts push-ups from the front. The nose must drop AND the shoulders must drop with it,
 * so nodding the head does not count. Needs nose, both shoulders and both wrists visible.
 *
 * The first frames of a session (user in the top position) set the baseline. Distances are in
 * shoulder widths, so it works at any distance from the phone.
 */
class PushUpDetector(
    val config: PushUpConfig = PushUpConfig()
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

    private var noseDrop = 0f
    private var shoulderDrop = 0f
    private var widthScale = 1f
    private var peakNoseDropSinceStart = 0f
    private var lastRepPeakNoseDrop = 0f
    private var lastRepShoulderDrop = 0f

    private var baseNoseY = 0f
    private var baseShoulderY = 0f
    private var baseWidth = 1f
    private var calibrationCount = 0
    private var calibrationNoseSum = 0f
    private var calibrationShoulderSum = 0f
    private var calibrationWidthSum = 0f

    private var smoothedNoseY = 0f
    private var smoothedShoulderY = 0f
    private var smoothedWidth = 0f
    private var smoothingReady = false

    private var maxNoseDrop = 0f
    private var shoulderDropAtPeak = 0f
    private var hasReachedBottom = false
    private var minScaleInRep = 1f
    private var maxScaleInRep = 1f
    private var trackingLostDuringRep = false
    private var repStartTimeMs = 0L

    private data class Measure(val noseY: Float, val shoulderY: Float, val width: Float)

    override fun isBodyVisible(landmarks: List<LandmarkPoint>, visibilityThreshold: Float): Boolean =
        measure(landmarks, visibilityThreshold) != null

    override fun reset() {
        currentState = SquatState.STANDING
        repCount = 0
        lastRejectionReason = RejectionReason.NONE
        isTrackingLost = false
        isCalibrated = false
        noseDrop = 0f
        shoulderDrop = 0f
        widthScale = 1f
        peakNoseDropSinceStart = 0f
        lastRepPeakNoseDrop = 0f
        lastRepShoulderDrop = 0f
        baseNoseY = 0f
        baseShoulderY = 0f
        baseWidth = 1f
        calibrationCount = 0
        calibrationNoseSum = 0f
        calibrationShoulderSum = 0f
        calibrationWidthSum = 0f
        smoothedNoseY = 0f
        smoothedShoulderY = 0f
        smoothedWidth = 0f
        smoothingReady = false
        maxNoseDrop = 0f
        shoulderDropAtPeak = 0f
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
                calibrationNoseSum = 0f
                calibrationShoulderSum = 0f
                calibrationWidthSum = 0f
            }
            return
        }

        if (isTrackingLost) {
            isTrackingLost = false
            emit(SquatEvent.TrackingRegained)
        }

        smooth(measure)

        // 2. Calibration: measure the top position, no counting yet
        if (!isCalibrated) {
            calibrationNoseSum += smoothedNoseY
            calibrationShoulderSum += smoothedShoulderY
            calibrationWidthSum += smoothedWidth
            calibrationCount++
            if (calibrationCount >= config.calibrationFrames) {
                baseNoseY = calibrationNoseSum / calibrationCount
                baseShoulderY = calibrationShoulderSum / calibrationCount
                baseWidth = calibrationWidthSum / calibrationCount
                isCalibrated = true
            }
            return
        }

        noseDrop = (smoothedNoseY - baseNoseY) / baseWidth
        shoulderDrop = (smoothedShoulderY - baseShoulderY) / baseWidth
        widthScale = smoothedWidth / baseWidth
        peakNoseDropSinceStart = max(peakNoseDropSinceStart, noseDrop)

        // 3. State transitions
        when (currentState) {
            SquatState.STANDING -> {
                if (noseDrop >= config.startDropRatio) {
                    maxNoseDrop = noseDrop
                    shoulderDropAtPeak = shoulderDrop
                    hasReachedBottom = false
                    minScaleInRep = widthScale
                    maxScaleInRep = widthScale
                    trackingLostDuringRep = false
                    repStartTimeMs = timestampMs
                    transitionTo(SquatState.DESCENDING)
                } else if (noseDrop < config.topDropRatio) {
                    // Stable at the top: follow slow drift
                    baseNoseY += config.baselineAlpha * (smoothedNoseY - baseNoseY)
                    baseShoulderY += config.baselineAlpha * (smoothedShoulderY - baseShoulderY)
                    baseWidth += config.baselineAlpha * (smoothedWidth - baseWidth)
                }
            }

            SquatState.DESCENDING -> {
                trackRep()
                if (noseDrop >= config.bottomDropRatio) {
                    hasReachedBottom = true
                    transitionTo(SquatState.BOTTOM)
                } else if (noseDrop <= maxNoseDrop - config.halfRepReversal) {
                    transitionTo(SquatState.ASCENDING)
                }
            }

            SquatState.BOTTOM -> {
                trackRep()
                if (noseDrop < config.bottomDropRatio - config.ascendHysteresis) {
                    transitionTo(SquatState.ASCENDING)
                }
            }

            SquatState.ASCENDING -> {
                trackRep()
                if (noseDrop >= config.bottomDropRatio) {
                    // Went down again (a stutter or bounce): same rep, back at the bottom.
                    hasReachedBottom = true
                    transitionTo(SquatState.BOTTOM)
                } else if (noseDrop <= config.topDropRatio) {
                    validateAndFinishRep(timestampMs)
                    transitionTo(SquatState.STANDING)
                }
            }
        }
    }

    override fun rejectionDetail(): String? = when (lastRejectionReason) {
        RejectionReason.NOT_DEEP_ENOUGH ->
            "nose dropped %.2f, needs %.2f (shoulder widths)".format(
                lastRepPeakNoseDrop,
                config.bottomDropRatio
            )

        RejectionReason.HEAD_ONLY_MOVEMENT ->
            "shoulders dropped %.2f but nose dropped %.2f".format(
                lastRepShoulderDrop,
                lastRepPeakNoseDrop
            )

        else -> null
    }

    override fun debugInfo(): String = listOf(
        "Calib    ${if (isCalibrated) "yes" else "no"}",
        "Nose     ${"%.2f".format(noseDrop)}",
        "Shoulder ${"%.2f".format(shoulderDrop)}",
        "Width    ${"%.2f".format(widthScale)}",
        "PeakNose ${"%.2f".format(peakNoseDropSinceStart)}"
    ).joinToString("\n")

    private fun measure(landmarks: List<LandmarkPoint>, threshold: Float): Measure? {
        if (landmarks.size <= RIGHT_WRIST) return null

        val nose = landmarks[NOSE]
        val leftShoulder = landmarks[LEFT_SHOULDER]
        val rightShoulder = landmarks[RIGHT_SHOULDER]
        val leftWrist = landmarks[LEFT_WRIST]
        val rightWrist = landmarks[RIGHT_WRIST]

        val allVisible = nose.visibility >= threshold &&
            leftShoulder.visibility >= threshold &&
            rightShoulder.visibility >= threshold &&
            leftWrist.visibility >= threshold &&
            rightWrist.visibility >= threshold
        if (!allVisible) return null

        val width = hypot(
            (leftShoulder.x - rightShoulder.x) * CAMERA_FRAME_ASPECT,
            leftShoulder.y - rightShoulder.y
        )
        if (width < MIN_SHOULDER_WIDTH) return null

        return Measure(
            noseY = nose.y,
            shoulderY = (leftShoulder.y + rightShoulder.y) / 2f,
            width = width
        )
    }

    private fun smooth(measure: Measure) {
        if (!smoothingReady) {
            smoothedNoseY = measure.noseY
            smoothedShoulderY = measure.shoulderY
            smoothedWidth = measure.width
            smoothingReady = true
        } else {
            val alpha = config.smoothingAlpha
            smoothedNoseY += alpha * (measure.noseY - smoothedNoseY)
            smoothedShoulderY += alpha * (measure.shoulderY - smoothedShoulderY)
            smoothedWidth += alpha * (measure.width - smoothedWidth)
        }
    }

    private fun trackRep() {
        if (noseDrop > maxNoseDrop) {
            maxNoseDrop = noseDrop
            shoulderDropAtPeak = shoulderDrop
        }
        minScaleInRep = min(minScaleInRep, widthScale)
        maxScaleInRep = max(maxScaleInRep, widthScale)
    }

    private fun validateAndFinishRep(timestampMs: Long) {
        lastRepPeakNoseDrop = maxNoseDrop
        lastRepShoulderDrop = shoulderDropAtPeak

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

        if (minScaleInRep < config.minWidthScale || maxScaleInRep > config.maxWidthScale) {
            reject(RejectionReason.SCALE_CHANGED)
            return
        }

        if (!hasReachedBottom) {
            reject(RejectionReason.NOT_DEEP_ENOUGH)
            return
        }

        // Anti-fake: a head nod moves the nose without the shoulders following.
        if (shoulderDropAtPeak < config.minShoulderDropFraction * maxNoseDrop) {
            reject(RejectionReason.HEAD_ONLY_MOVEMENT)
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
        const val NOSE = 0
        const val LEFT_SHOULDER = 11
        const val RIGHT_SHOULDER = 12
        const val LEFT_WRIST = 15
        const val RIGHT_WRIST = 16

        /** Shoulder width (in frame heights) below this is treated as a bad detection. */
        const val MIN_SHOULDER_WIDTH = 0.03f
    }
}