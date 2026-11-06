package com.fitness.squatcounter.domain

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Thresholds for jumping jacks seen from the front (phone propped up facing you).
 * Distances are in shoulder widths. These are starting guesses and must be tuned on a real
 * phone with the Debug panel.
 */
data class JumpingJackConfig(
    /** OPEN: feet (ankles) at least this far apart, in shoulder widths. */
    val openLegSpread: Float = 1.3f,

    /** CLOSED: feet no further apart than this, in shoulder widths. */
    val closedLegSpread: Float = 0.9f,

    /** OPEN: both wrists above the nose by at least this much (in shoulder widths). */
    val armsUpMargin: Float = 0.0f,

    /** CLOSED: both wrists below the shoulder line by at least this much (in shoulder widths). */
    val armsDownMargin: Float = 0.3f,

    /** Smoothing of the measurements. 1.0 = no smoothing. Jumping jacks are fast, so keep it light. */
    val smoothingAlpha: Float = 0.5f,

    val minRepDurationMs: Long = 400L,
    val maxRepDurationMs: Long = 4000L
)

/**
 * Counts jumping jacks from the front. One rep is closed -> open -> closed, where
 *  - CLOSED = arms down and feet together,
 *  - OPEN   = both hands overhead and feet wide apart.
 *
 * Both halves must happen, so waving only your arms or only moving your feet does not count.
 * Needs nose, shoulders, wrists and ankles visible, so stand far enough back for your whole
 * body and leave room above your head.
 *
 * States reuse [SquatState]: STANDING = closed, DESCENDING = opening, BOTTOM = open,
 * ASCENDING = closing.
 */
class JumpingJackDetector(
    val config: JumpingJackConfig = JumpingJackConfig()
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

    private var armsUpScore = 0f
    private var armsDownScore = 0f
    private var legSpread = 0f
    private var peakSpreadSinceStart = 0f
    private var smoothingReady = false

    private var reachedOpen = false
    private var handsWereOverhead = false
    private var maxSpreadInRep = 0f
    private var lastRepHandsOverhead = false
    private var lastRepMaxSpread = 0f
    private var trackingLostDuringRep = false
    private var repStartTimeMs = 0L

    private data class Scores(val armsUp: Float, val armsDown: Float, val legSpread: Float)

    override fun isBodyVisible(landmarks: List<LandmarkPoint>, visibilityThreshold: Float): Boolean =
        measure(landmarks, visibilityThreshold) != null

    override fun reset() {
        currentState = SquatState.STANDING
        repCount = 0
        lastRejectionReason = RejectionReason.NONE
        isTrackingLost = false
        armsUpScore = 0f
        armsDownScore = 0f
        legSpread = 0f
        peakSpreadSinceStart = 0f
        smoothingReady = false
        reachedOpen = false
        handsWereOverhead = false
        maxSpreadInRep = 0f
        lastRepHandsOverhead = false
        lastRepMaxSpread = 0f
        trackingLostDuringRep = false
        repStartTimeMs = 0L
    }

    override fun processFrame(
        landmarks: List<LandmarkPoint>,
        timestampMs: Long,
        visibilityThreshold: Float
    ) {
        val scores = measure(landmarks, visibilityThreshold)

        // 1. Tracking check
        if (scores == null) {
            if (!isTrackingLost) {
                isTrackingLost = true
                emit(SquatEvent.TrackingLost)
            }
            if (currentState != SquatState.STANDING) {
                trackingLostDuringRep = true
            }
            smoothingReady = false
            return
        }

        if (isTrackingLost) {
            isTrackingLost = false
            emit(SquatEvent.TrackingRegained)
        }

        smooth(scores)
        peakSpreadSinceStart = max(peakSpreadSinceStart, legSpread)

        val armsUp = armsUpScore >= config.armsUpMargin
        val armsDown = armsDownScore >= config.armsDownMargin
        val open = armsUp && legSpread >= config.openLegSpread
        val closed = armsDown && legSpread <= config.closedLegSpread

        // 2. State transitions
        when (currentState) {
            SquatState.STANDING -> {
                if (!closed) {
                    reachedOpen = false
                    handsWereOverhead = armsUp
                    maxSpreadInRep = legSpread
                    trackingLostDuringRep = false
                    repStartTimeMs = timestampMs
                    transitionTo(SquatState.DESCENDING)
                    if (open) {
                        reachedOpen = true
                        transitionTo(SquatState.BOTTOM)
                    }
                }
            }

            SquatState.DESCENDING, SquatState.ASCENDING -> {
                trackRep(armsUp)
                if (open) {
                    reachedOpen = true
                    transitionTo(SquatState.BOTTOM)
                } else if (closed) {
                    finishRep(timestampMs)
                    transitionTo(SquatState.STANDING)
                }
            }

            SquatState.BOTTOM -> {
                trackRep(armsUp)
                if (closed) {
                    finishRep(timestampMs)
                    transitionTo(SquatState.STANDING)
                } else if (!open) {
                    transitionTo(SquatState.ASCENDING)
                }
            }
        }
    }

    override fun rejectionDetail(): String? =
        if (lastRejectionReason == RejectionReason.INCOMPLETE_JACK) {
            "hands overhead: %s, feet spread %.2f (needs %.2f)".format(
                if (lastRepHandsOverhead) "yes" else "no",
                lastRepMaxSpread,
                config.openLegSpread
            )
        } else {
            null
        }

    override fun debugInfo(): String = listOf(
        "ArmsUp    ${"%.2f".format(armsUpScore)}",
        "ArmsDown  ${"%.2f".format(armsDownScore)}",
        "Spread    ${"%.2f".format(legSpread)}",
        "PeakSpread ${"%.2f".format(peakSpreadSinceStart)}"
    ).joinToString("\n")

    private fun measure(landmarks: List<LandmarkPoint>, threshold: Float): Scores? {
        if (landmarks.size <= RIGHT_ANKLE) return null

        val nose = landmarks[NOSE]
        val leftShoulder = landmarks[LEFT_SHOULDER]
        val rightShoulder = landmarks[RIGHT_SHOULDER]
        val leftWrist = landmarks[LEFT_WRIST]
        val rightWrist = landmarks[RIGHT_WRIST]
        val leftAnkle = landmarks[LEFT_ANKLE]
        val rightAnkle = landmarks[RIGHT_ANKLE]

        val allVisible = nose.visibility >= threshold &&
            leftShoulder.visibility >= threshold &&
            rightShoulder.visibility >= threshold &&
            leftWrist.visibility >= threshold &&
            rightWrist.visibility >= threshold &&
            leftAnkle.visibility >= threshold &&
            rightAnkle.visibility >= threshold
        if (!allVisible) return null

        val width = hypot(
            (leftShoulder.x - rightShoulder.x) * CAMERA_FRAME_ASPECT,
            leftShoulder.y - rightShoulder.y
        )
        if (width < MIN_SHOULDER_WIDTH) return null

        val shoulderY = (leftShoulder.y + rightShoulder.y) / 2f
        val lowerWristY = max(leftWrist.y, rightWrist.y)
        val upperWristY = min(leftWrist.y, rightWrist.y)
        val spread = kotlin.math.abs(leftAnkle.x - rightAnkle.x) * CAMERA_FRAME_ASPECT

        return Scores(
            armsUp = (nose.y - lowerWristY) / width,
            armsDown = (upperWristY - shoulderY) / width,
            legSpread = spread / width
        )
    }

    private fun smooth(scores: Scores) {
        if (!smoothingReady) {
            armsUpScore = scores.armsUp
            armsDownScore = scores.armsDown
            legSpread = scores.legSpread
            smoothingReady = true
        } else {
            val alpha = config.smoothingAlpha
            armsUpScore += alpha * (scores.armsUp - armsUpScore)
            armsDownScore += alpha * (scores.armsDown - armsDownScore)
            legSpread += alpha * (scores.legSpread - legSpread)
        }
    }

    private fun trackRep(armsUp: Boolean) {
        if (armsUp) handsWereOverhead = true
        maxSpreadInRep = max(maxSpreadInRep, legSpread)
    }

    private fun finishRep(timestampMs: Long) {
        lastRepHandsOverhead = handsWereOverhead
        lastRepMaxSpread = maxSpreadInRep

        if (trackingLostDuringRep) {
            reject(RejectionReason.TRACKING_LOST_DURING_REP)
            return
        }

        val repDurationMs = if (repStartTimeMs > 0L) timestampMs - repStartTimeMs else 0L

        if (!reachedOpen) {
            // A real attempt that never reached the open pose gets feedback.
            // A tiny twitch is ignored so it does not spam rejections.
            if (repDurationMs >= config.minRepDurationMs) {
                reject(RejectionReason.INCOMPLETE_JACK)
            }
            return
        }

        if (repDurationMs < config.minRepDurationMs) {
            reject(RejectionReason.TOO_FAST)
            return
        }
        if (repDurationMs > config.maxRepDurationMs) {
            reject(RejectionReason.TOO_SLOW)
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
        const val LEFT_ANKLE = 27
        const val RIGHT_ANKLE = 28

        /** Shoulder width (in frame heights) below this is treated as a bad detection. */
        const val MIN_SHOULDER_WIDTH = 0.03f
    }
}