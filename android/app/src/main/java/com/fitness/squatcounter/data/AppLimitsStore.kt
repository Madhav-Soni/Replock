package com.fitness.squatcounter.data

import android.content.Context

/**
 * @param limitSeconds free daily time for this app
 * @param usedSeconds free time already used on the day starting at [dayStartMs]
 */
data class AppUsage(
    val limitSeconds: Int,
    val usedSeconds: Int,
    val dayStartMs: Long
)

interface AppLimitsStorage {
    fun load(): Map<String, AppUsage>
    fun save(data: Map<String, AppUsage>)
}

class SharedPrefsAppLimitsStorage(context: Context) : AppLimitsStorage {

    private val prefs =
        context.applicationContext.getSharedPreferences("replock_app_limits", Context.MODE_PRIVATE)

    override fun load(): Map<String, AppUsage> =
        (prefs.getStringSet(KEY, emptySet()) ?: emptySet())
            .mapNotNull { entry ->
                val parts = entry.split('|')
                if (parts.size != 4) return@mapNotNull null
                val limit = parts[1].toIntOrNull() ?: return@mapNotNull null
                val used = parts[2].toIntOrNull() ?: return@mapNotNull null
                val dayStart = parts[3].toLongOrNull() ?: return@mapNotNull null
                parts[0] to AppUsage(limit, used, dayStart)
            }
            .toMap()

    override fun save(data: Map<String, AppUsage>) {
        prefs.edit()
            .putStringSet(
                KEY,
                data.map { "${it.key}|${it.value.limitSeconds}|${it.value.usedSeconds}|${it.value.dayStartMs}" }
                    .toSet()
            )
            .apply()
    }

    private companion object {
        const val KEY = "apps"
    }
}

/**
 * A free daily time allowance for each locked app. Free time is used first; after it runs out,
 * the app costs earned credits. The day is identified by its start time (use the ledger's
 * trusted time), so usage resets by itself when a new day begins.
 */
class AppLimitsStore(
    private val storage: AppLimitsStorage,
    val defaultLimitSeconds: Int = 1800,
    val minLimitSeconds: Int = 300,
    val maxLimitSeconds: Int = 14_400
) {

    fun limitFor(packageName: String): Int =
        storage.load()[packageName]?.limitSeconds ?: defaultLimitSeconds

    fun setLimit(packageName: String, seconds: Int) {
        val limit = seconds.coerceIn(minLimitSeconds, maxLimitSeconds)
        val data = storage.load()
        val existing = data[packageName]
        storage.save(data + (packageName to (existing?.copy(limitSeconds = limit) ?: AppUsage(limit, 0, 0L))))
    }

    fun usedToday(packageName: String, dayStartMs: Long): Int {
        val entry = storage.load()[packageName] ?: return 0
        return if (entry.dayStartMs == dayStartMs) entry.usedSeconds else 0
    }

    fun freeRemaining(packageName: String, dayStartMs: Long): Int =
        maxOf(0, limitFor(packageName) - usedToday(packageName, dayStartMs))

    /**
     * Uses up to [wantedSeconds] of the app's free time today.
     * @return how many seconds the free allowance covered (0 if it is already used up)
     */
    fun consumeFree(packageName: String, wantedSeconds: Int, dayStartMs: Long): Int {
        if (wantedSeconds <= 0) return 0

        val data = storage.load()
        val entry = data[packageName] ?: AppUsage(defaultLimitSeconds, 0, dayStartMs)
        val usedBefore = if (entry.dayStartMs == dayStartMs) entry.usedSeconds else 0
        val free = maxOf(0, entry.limitSeconds - usedBefore)
        val covered = minOf(wantedSeconds, free)

        storage.save(
            data + (packageName to entry.copy(usedSeconds = usedBefore + covered, dayStartMs = dayStartMs))
        )
        return covered
    }
}
