package com.fitness.squatcounter.session

import com.fitness.squatcounter.domain.LandmarkPoint
import kotlin.math.abs

/**
 * Watches the pose-result stream for two failure modes:
 *  1. Stale: no new result for too long (camera died, app stalled).
 *  2. Frozen: landmarks identical for many consecutive frames (stuck feed).
 *
 * A frozen or dead feed must never keep counting reps.
 * This is NOT replay detection: a live camera filming a screen still produces sensor noise.
 *
 * Time is passed in (monotonic ms). Not thread-safe: call from one thread only.
 */
class FeedMonitor(
    private val config: SessionConfig = SessionConfig()
) {
    private var lastFrameAtMs: Long = 0L
    private var identicalFrames: Int = 0
    private var lastPoints: List<LandmarkPoint>? = null

    /** Start watching from "now". Gives the camera a grace period to deliver its first frame. */
    fun reset(nowMs: Long) {
        lastFrameAtMs = nowMs
        identicalFrames = 0
        lastPoints = null
    }

    /**
     * Record one pose result. An empty list means "no person found": the feed is alive,
     * so only the frozen counter is cleared.
     */
    fun onFrame(nowMs: Long, landmarks: List<LandmarkPoint>) {
        lastFrameAtMs = nowMs

        if (landmarks.isEmpty()) {
            identicalFrames = 0
            lastPoints = null
            return
        }

        val previous = lastPoints
        identicalFrames = if (previous != null && isIdentical(previous, landmarks)) {
            identicalFrames + 1
        } else {
            0
        }
        lastPoints = landmarks
    }

    fun isHealthy(nowMs: Long): Boolean =
        nowMs - lastFrameAtMs <= config.staleFrameTimeoutMs &&
            identicalFrames < config.frozenFrameCount

    private fun isIdentical(a: List<LandmarkPoint>, b: List<LandmarkPoint>): Boolean {
        if (a.size != b.size) return false
        val eps = config.frozenLandmarkEpsilon
        for (i in a.indices) {
            if (abs(a[i].x - b[i].x) > eps || abs(a[i].y - b[i].y) > eps) return false
        }
        return true
    }
}