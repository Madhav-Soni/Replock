package com.fitness.squatcounter.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * Provides time snapshots (wall clock, elapsed realtime, and boot count).
 * Abstracted into an interface for deterministic unit testing of time-based ledger behavior.
 */
interface TimeProvider {
    fun currentTimeMillis(): Long
    fun elapsedRealtime(): Long
    fun bootCount(): Int
}

class AndroidTimeProvider(private val context: Context) : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 1)
        } catch (_: Exception) {
            1
        }
    }
}
