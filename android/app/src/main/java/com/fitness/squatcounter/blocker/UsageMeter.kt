package com.fitness.squatcounter.blocker

/**
 * All blocker thresholds in one place.
 */
data class BlockerConfig(
    /** How often the service re-checks while a blocked app is in front. */
    val tickMs: Long = 1_000L,

    /** Spend credits in batches of this many seconds, not on every tick. */
    val flushSeconds: Int = 10,

    /** Show a warning when this many seconds are left. */
    val warnSecondsLeft: Int = 30,

    /** A single time step is never charged for more than this (guards against long gaps). */
    val maxGapMs: Long = 5_000L,

    /**
     * How long removing an app from the blocklist takes to take effect.
     * 0 = immediate (testing only). RAISE THIS (e.g. 24 hours) before real use,
     * otherwise you can un-block an app the moment you run out of credits.
     */
    val removalCoolingOffMs: Long = 0L
)

/**
 * Counts how long a blocked app has been in use. Pure Kotlin, time is passed in.
 *
 * Call update(now, metering) on every check. Whole seconds owed are read with
 * pendingSeconds and removed with takeWholeSeconds(); the leftover fraction carries over.
 */
class UsageMeter(
    private val maxGapMs: Long = 5_000L
) {
    private var lastUpdateMs: Long? = null
    private var pendingMs: Long = 0L

    val pendingSeconds: Int
        get() = (pendingMs / 1000L).toInt()

    /**
     * @param metering true while a blocked app is on screen and the screen is usable.
     * Time since the previous update is charged only if the previous update was metering.
     */
    fun update(nowMs: Long, metering: Boolean) {
        val last = lastUpdateMs
        if (last != null) {
            pendingMs += (nowMs - last).coerceIn(0L, maxGapMs)
        }
        lastUpdateMs = if (metering) nowMs else null
    }

    /** Removes and returns the whole seconds owed. The leftover fraction stays. */
    fun takeWholeSeconds(): Int {
        val seconds = pendingSeconds
        pendingMs -= seconds * 1000L
        return seconds
    }

    fun reset() {
        lastUpdateMs = null
        pendingMs = 0L
    }
}
