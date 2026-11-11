package com.fitness.squatcounter.domain

import kotlin.math.hypot
import kotlin.math.min

/**
 * Thresholds for wall sit holds. Works from the front or the side with the phone propped up
 * so shoulders, hips and knees are in view (ankles are not needed).
 *
 * The check is the gap between knee height and hip height, measured in torso lengths:
 *   gap = (kneeY - hipY) / torsoLength
 * Standing is about 1.0, a half squat about 0.5, thighs parallel to the floor about 0.
 * Negative means hips lower than knees. These are starting guesses: tune with the Debug panel.
 */
data class WallSitConfig(
    /** Every this many seconds of valid hold counts as one unit (one "rep" in the ledger). */
    val unitSeconds: Int = 5,

    /** Hips may be at most this far above the knee line (in torso lengths) and still count. */
    val maxHipsAboveKnees: Float = 0.30f,

    /** Hips may be at most this far below the knee line (sitting on the floor does not count). */
    val maxHipsBelowKnees: Float = 0.40f,

    /** A broken posture shorter than this is ignored. Longer than this resets the current unit. */
    val breakGraceMs: Long = 1_500L,

    /** Smoothing of the measurement. 1.0 = no smoothing. */
    val smoothingAlpha: Float = 0.35f
)

/**
 * Counts wall sit time. Time only adds up while your hips stay at about knee height.
 * Every [WallSitConfig.unitSeconds] of continuous valid hold completes one unit, paid out
 * through the normal credits ledger. A broken hold loses the partial unit, never earned ones.
 *
 * Limits: the camera cannot see the wall, so a free-air squat hold counts the same.
 */
class WallSitDetector(
    val config: WallSitConfig = WallSitConfig()
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

    override val displayCount: Int
        get() = (totalHeldMs / 1000L).toInt()

    override fun spokenNumber(unitCount: Int): Int = unitCount * config.unitSeconds

    var isTrackingLost: Boolean = false
        private set

    private var gap = 0f
    private var smoothedGap = 0f
    private var smoothingReady = false

    private var unitMs = 0L
    private var totalHeldMs = 0L
    private var lastValidFrameMs: Long? = null
    private var invalidSinceMs: Long? = null
    private var lastBreakGap = 0f

    override fun isBodyVisible(landmarks: List<LandmarkPoint>, visibilityThreshold: Float): Boolean =
        measureGap(landmarks, visibilityThreshold) != null

    override fun reset() {
        currentState = SquatState.STANDING
        repCount = 0
        lastRejectionReason = RejectionReason.NONE
        isTrackingLost = false
        gap = 0f
        smoothedGap = 0f
        smoothingReady = false
        unitMs = 0L
        totalHeldMs = 0L
        lastValidFrameMs = null
        invalidSinceMs = null
        lastBreakGap = 0f
    }

    override fun processFrame(
        landmarks: List<LandmarkPoint>,
        timestampMs: Long,
        visibilityThreshold: Float
    ) {
        val raw = measureGap(landmarks, visibilityThreshold)

        // Nothing accumulates while the person is not tracked.
        if (raw == null) {
            if (!isTrackingLost) {
                isTrackingLost = true
                emit(SquatEvent.TrackingLost)
            }
            smoothingReady = false
            markInvalid(timestampMs)
            return
        }

        if (isTrackingLost) {
            isTrackingLost = false
            emit(SquatEvent.TrackingRegained)
        }

        if (!smoothingReady) {
            smoothedGap = raw
            smoothingReady = true
        } else {
            smoothedGap += config.smoothingAlpha * (raw - smoothedGap)
        }
        gap = smoothedGap

        val valid = gap <= config.maxHipsAboveKnees && gap >= -config.maxHipsBelowKnees
        if (valid) markValid(timestampMs) else markInvalid(timestampMs)
    }

    override fun rejectionDetail(): String? =
        if (lastRejectionReason == RejectionReason.WALL_SIT_BROKEN) {
            "hips %.2f torso lengths above knees (allowed %.2f to %.2f)".format(
                lastBreakGap,
                -config.maxHipsBelowKnees,
                config.maxHipsAboveKnees
            )
        } else {
            null
        }

    override fun debugInfo(): String = listOf(
        "Gap      ${"%.2f".format(gap)}  (ok ${"%.2f".format(-config.maxHipsBelowKnees)}..${"%.2f".format(config.maxHipsAboveKnees)})",
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

        // A longer break loses the partial unit (units already completed stay earned).
        if (timestampMs - since >= config.breakGraceMs && unitMs > 0L) {
            unitMs = 0L
            lastBreakGap = gap
            lastRejectionReason = RejectionReason.WALL_SIT_BROKEN
            emit(SquatEvent.RepRejected(RejectionReason.WALL_SIT_BROKEN))
        }
    }

    /** Knee-minus-hip height in torso lengths, or null if the needed landmarks are not visible. */
    private fun measureGap(landmarks: List<LandmarkPoint>, threshold: Float): Float? {
        if (landmarks.size <= RIGHT_KNEE) return null

        val ls = landmarks[LEFT_SHOULDER]
        val rs = landmarks[RIGHT_SHOULDER]
        val lh = landmarks[LEFT_HIP]
        val rh = landmarks[RIGHT_HIP]
        val lk = landmarks[LEFT_KNEE]
        val rk = landmarks[RIGHT_KNEE]

        val allVisible = ls.visibility >= threshold && rs.visibility >= threshold &&
            lh.visibility >= threshold && rh.visibility >= threshold &&
            lk.visibility >= threshold && rk.visibility >= threshold
        if (!allVisible) return null

        val torso = hypot(
            ((ls.x + rs.x) / 2f - (lh.x + rh.x) / 2f) * CAMERA_FRAME_ASPECT,
            (ls.y + rs.y) / 2f - (lh.y + rh.y) / 2f
        )
        if (torso < MIN_TORSO_LENGTH) return null

        val hipY = (lh.y + rh.y) / 2f
        val kneeY = (lk.y + rk.y) / 2f
        return (kneeY - hipY) / torso
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

        /** Torso length (in frame heights) below this is treated as a bad detection. */
        const val MIN_TORSO_LENGTH = 0.05f

        /** A single time step is never credited for more than this (guards against long gaps). */
        const val MAX_STEP_MS = 500L
    }
}