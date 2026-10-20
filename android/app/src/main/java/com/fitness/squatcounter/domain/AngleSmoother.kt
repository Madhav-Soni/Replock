package com.fitness.squatcounter.domain

/**
 * Low-latency Exponential Moving Average (EMA) filter for smoothing joint angles.
 * At 10-15 fps, EMA provides smooth transitions without the multi-frame lag of large window averages.
 */
class AngleSmoother(private val alpha: Float = 0.35f) {

    private var smoothedValue: Float? = null

    /**
     * Filters the raw angle value and returns the smoothed angle.
     */
    fun filter(rawValue: Float): Float {
        val currentSmoothed = smoothedValue
        val result = if (currentSmoothed == null) {
            rawValue
        } else {
            alpha * rawValue + (1.0f - alpha) * currentSmoothed
        }
        smoothedValue = result
        return result
    }

    /**
     * Resets the filter state (e.g., when tracking is lost).
     */
    fun reset() {
        smoothedValue = null
    }

    fun currentValue(): Float? = smoothedValue
}
