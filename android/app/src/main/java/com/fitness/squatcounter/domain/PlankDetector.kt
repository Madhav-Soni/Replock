package com.fitness.squatcounter.domain

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Thresholds for plank holds seen from the front (phone on the floor facing you).
 * Deviations are in shoulder widths: 0.3 means 30% of the distance between your shoulders.
 * These are starting guesses and must be tuned on a real phone with the Debug panel.
 */
data class PlankConfig(
    /** Every this many seconds of valid hold counts as one unit (one "rep" in the ledger). */
    val unitSeconds: Int = 5,

    /** Shoulders may move this far (up or down) from the starting position and still be a plank. */
    val maxShoulderDeviation: Float = 0.30f,

    /** The nose may move this far from the starting position (head dropping to the floor). */
    val maxNoseDeviation: Float = 0.40f,

    /** Shoulder width relative to the start must stay in this range (no creeping toward the phone). */
    val minWidthScale: Float = 0.75f,
    val maxWidthScale: Float = 1.35f,

    /** A broken posture shorter than this is ignored. Longer than this resets the current unit. */
    val breakGraceMs: Long = 1_500L,

    /** Not in position at the start for this long: measure the starting position again. */
    val recalibrateAfterMs: Long = 3_000L,

    /** Frames averaged at the start of a session to measure the plank position. */
    val calibrationFrames: Int = 8,

    /** Smoothing of the measurements. 1.0 = no smoothing. */
    val smoothingAlpha: Float = 0.35f
)

/**
 * Counts plank time from the front. Time only adds up while your head and shoulders stay near
 * the position measured at the start of the session and you stay at the same distance.
 *
 * Every [PlankConfig.unitSeconds] of continuous valid hold completes one unit, which pays out
 * through the normal credits ledger. A broken hold loses the partial unit but never earned ones.
 *
 * Limits: from the front it cannot see whether your hips sag or pike. It only checks that you
 * are holding still in the position you started in.
 */
class PlankDetector(
    val config: PlankConfig = PlankConfig()
) : ExerciseDetector {

    override var currentState: SquatState = SquatState.STANDING
        private set

    /** Completed hold units. */
    override var repCount: Int = 0
        private set

    override var lastRejectionReason: RejectionReason = RejectionReason.NONE
        private set

    override var onEventListener: ((SquatEvent) -> Unit)? = null

    override val countLabel: String
        get() = "SECONDS"

    /** Seconds of valid hold so far, for the big number on screen. */
    override val displayCount: Int
        get() = (totalHeldMs / 1000L).toInt()

    /** Plank units are announced as seconds held: 5, 10, 15, ... */
    override fun spokenNumber(unitCount: Int): Int = unitCount * config.unitSeconds

    var isTrackingLost: Boolean = false
        private set

    var isCalibrated: Boolean = false
        private set

    private var noseDeviation = 0f
    private var shoulderDeviation = 0f
    private var widthScale = 1f

    private var unitMs = 0L
    private var totalHeldMs = 0L
    private var lastValidFrameMs: Long? = null
    private var invalidSinceMs: Long? = null
    private var lastBreakShoulderDeviation = 0f
    private var lastBreakWidthScale = 1f

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

    private data class Measure(val noseY: Float, val shoulderY: Float, val width: Float)

    override fun isBodyVisible(landmarks: List<LandmarkPoint>, visibilityThreshold: Float): Boolean =
        measure(landmarks, visibilityThreshold) != null

    override fun reset() {
        currentState = SquatState.STANDING
        repCount = 0
        lastRejectionReason = RejectionReason.NONE
        isTrackingLost = false
        noseDeviation = 0f
        shoulderDeviation = 0f
        widthScale = 1f
        unitMs = 0L
        totalHeldMs = 0L
        lastValidFrameMs = null
        invalidSinceMs = null
        lastBreakShoulderDeviation = 0f
        lastBreakWidthScale = 1f
        smoothedNoseY = 0f
        smoothedShoulderY = 0f
        smoothedWidth = 0f
        smoothingReady = false
        restartCalibration()
    }

    override fun processFrame(
        landmarks: List<LandmarkPoint>,
        timestampMs: Long,
        visibilityThreshold: Float
    ) {
        val measure = measure(landmarks, visibilityThreshold)

        // 1. Tracking check: nothing accumulates while the person is not tracked
        if (measure == null) {
            if (!isTrackingLost) {
                isTrackingLost = true
                emit(SquatEvent.TrackingLost)
            }
            smoothingReady = false
            if (!isCalibrated) {
                calibrationCount = 0
                calibrationNoseSum = 0f
                calibrationShoulderSum = 0f
                calibrationWidthSum = 0f
            }
            markInvalid(timestampMs)
            return
        }

        if (isTrackingLost) {
            isTrackingLost = false
            emit(SquatEvent.TrackingRegained)
        }

        smooth(measure)

        // 2. Calibration: measure the plank position, no timing yet
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

        // 3. Is the posture still the starting plank position?
        noseDeviation = (smoothedNoseY - baseNoseY) / baseWidth
        shoulderDeviation = (smoothedShoulderY - baseShoulderY) / baseWidth
        widthScale = smoothedWidth / baseWidth

        val valid = abs(shoulderDeviation) <= config.maxShoulderDeviation &&
            abs(noseDeviation) <= config.maxNoseDeviation &&
            widthScale >= config.minWidthScale &&
            widthScale <= config.maxWidthScale

        if (valid) markValid(timestampMs) else markInvalid(timestampMs)
    }

    override fun rejectionDetail(): String? =
        if (lastRejectionReason == RejectionReason.POSTURE_BROKEN) {
            "shoulders moved %.2f (limit %.2f), distance %.2fx".format(
                lastBreakShoulderDeviation,
                config.maxShoulderDeviation,
                lastBreakWidthScale
            )
        } else {
            null
        }

    override fun debugInfo(): String = listOf(
        "Calib    ${if (isCalibrated) "yes" else "no"}",
        "Shoulder ${"%.2f".format(shoulderDeviation)}",
        "Nose     ${"%.2f".format(noseDeviation)}",
        "Width    ${"%.2f".format(widthScale)}",
        "Unit     ${"%.1f".format(unitMs / 1000f)} s",
        "Held     ${"%.1f".format(totalHeldMs / 1000f)} s"
    ).joinToString("\n")

    private fun markValid(timestampMs: Long) {
        invalidSinceMs = null

        // Time only adds up between two consecutive valid frames, never across a gap.
        val last = lastValidFrameMs
        if (last != null) {
            val step = min(timestampMs - last, MAX_STEP_MS).coerceAtLeast(0L)
            unitMs += step
            totalHeldMs += step
        }
        lastValidFrameMs = timestampMs
        transitionTo(SquatState.BOTTOM)

        val unitLimitMs = config.unitSeconds * 1000L
        while (unitMs >= unitLimitMs) {
            unitMs -= unitLimitMs
            repCount++
            lastRejectionReason = RejectionReason.NONE
            emit(SquatEvent.RepValidated(repCount))
        }
    }

    private fun markInvalid(timestampMs: Long) {
        lastValidFrameMs = null
        transitionTo(SquatState.STANDING)

        val since = invalidSinceMs ?: timestampMs
        invalidSinceMs = since
        val invalidForMs = timestampMs - since

        // A longer break loses the partial unit (units already completed stay earned).
        if (invalidForMs >= config.breakGraceMs && unitMs > 0L) {
            unitMs = 0L
            lastBreakShoulderDeviation = shoulderDeviation
            lastBreakWidthScale = widthScale
            lastRejectionReason = RejectionReason.POSTURE_BROKEN
            emit(SquatEvent.RepRejected(RejectionReason.POSTURE_BROKEN))
        }

        // Not in position at all yet: the starting position was measured too early. Measure again.
        if (isCalibrated && totalHeldMs == 0L && invalidForMs >= config.recalibrateAfterMs) {
            restartCalibration()
        }
    }

    private fun restartCalibration() {
        isCalibrated = false
        calibrationCount = 0
        calibrationNoseSum = 0f
        calibrationShoulderSum = 0f
        calibrationWidthSum = 0f
        invalidSinceMs = null
    }

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

        /** A single time step is never credited for more than this (guards against long gaps). */
        const val MAX_STEP_MS = 500L
    }
}