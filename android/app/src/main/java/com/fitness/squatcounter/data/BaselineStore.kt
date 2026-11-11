package com.fitness.squatcounter.data

import android.content.Context

/** A lower max the user asked for, waiting for the cooling-off period to pass. */
data class PendingLower(val newMax: Int, val dueMs: Long)

/**
 * @param max self-reported max per exercise key (reps, or seconds for holds)
 * @param pendingLower lowerings that have been requested but not applied yet
 * @param confirmed true once the user has been through the levels screen
 */
data class BaselineData(
    val max: Map<String, Int> = emptyMap(),
    val pendingLower: Map<String, PendingLower> = emptyMap(),
    val confirmed: Boolean = false
)

interface BaselineStorage {
    fun load(): BaselineData
    fun save(data: BaselineData)
}

class SharedPrefsBaselineStorage(context: Context) : BaselineStorage {

    private val prefs =
        context.applicationContext.getSharedPreferences("replock_baseline", Context.MODE_PRIVATE)

    override fun load(): BaselineData {
        val max = (prefs.getStringSet(KEY_MAX, emptySet()) ?: emptySet())
            .mapNotNull { entry ->
                val parts = entry.split('|')
                if (parts.size != 2) return@mapNotNull null
                val value = parts[1].toIntOrNull() ?: return@mapNotNull null
                parts[0] to value
            }
            .toMap()

        val pending = (prefs.getStringSet(KEY_PENDING, emptySet()) ?: emptySet())
            .mapNotNull { entry ->
                val parts = entry.split('|')
                if (parts.size != 3) return@mapNotNull null
                val newMax = parts[1].toIntOrNull() ?: return@mapNotNull null
                val due = parts[2].toLongOrNull() ?: return@mapNotNull null
                parts[0] to PendingLower(newMax, due)
            }
            .toMap()

        return BaselineData(max, pending, prefs.getBoolean(KEY_CONFIRMED, false))
    }

    override fun save(data: BaselineData) {
        prefs.edit()
            .putStringSet(KEY_MAX, data.max.map { "${it.key}|${it.value}" }.toSet())
            .putStringSet(
                KEY_PENDING,
                data.pendingLower.map { "${it.key}|${it.value.newMax}|${it.value.dueMs}" }.toSet()
            )
            .putBoolean(KEY_CONFIRMED, data.confirmed)
            .apply()
    }

    private companion object {
        const val KEY_MAX = "max"
        const val KEY_PENDING = "pending_lower"
        const val KEY_CONFIRMED = "confirmed"
    }
}

/** Allowed range for one exercise's max. Units are reps, or seconds for holds. */
data class BaselineRule(val default: Int, val min: Int, val max: Int)

object BaselineDefaults {
    val rules: Map<String, BaselineRule> = mapOf(
        CreditConfig.EXERCISE_SQUATS to BaselineRule(default = 20, min = 5, max = 300),
        CreditConfig.EXERCISE_PUSHUPS to BaselineRule(default = 10, min = 3, max = 300),
        CreditConfig.EXERCISE_PLANK to BaselineRule(default = 30, min = 10, max = 1800),
        CreditConfig.EXERCISE_WALL_SIT to BaselineRule(default = 30, min = 10, max = 1800),
        CreditConfig.EXERCISE_LUNGES to BaselineRule(default = 16, min = 4, max = 300),
        CreditConfig.EXERCISE_JUMPING_JACKS to BaselineRule(default = 40, min = 10, max = 500)
    )
}

/**
 * The user's self-reported max for each exercise (how many in one go).
 *
 * Because it is self-reported, it is guarded:
 *  - a floor: you cannot claim less than the exercise's minimum,
 *  - raising is instant, lowering waits for the cooling-off period (except the first time),
 *  - a verified count above your max raises it automatically.
 * Time is passed in (use the ledger's trusted time).
 */
class BaselineStore(
    private val storage: BaselineStorage,
    private val rules: Map<String, BaselineRule> = BaselineDefaults.rules,
    private val lowerCoolingOffMs: Long
) {

    fun maxFor(key: String, nowMs: Long): Int =
        loadCurrent(nowMs).max[key] ?: rule(key).default

    /** True if this exercise's max was set by the user (not just the default). */
    fun isSet(key: String, nowMs: Long): Boolean = key in loadCurrent(nowMs).max

    fun pendingLowerFor(key: String, nowMs: Long): PendingLower? =
        loadCurrent(nowMs).pendingLower[key]

    /** The user types in or adjusts a max. Clamped to the allowed range. */
    fun setMax(key: String, requested: Int, nowMs: Long) {
        val rule = rule(key)
        val value = requested.coerceIn(rule.min, rule.max)
        val data = loadCurrent(nowMs)
        val firstTime = key !in data.max
        val current = data.max[key] ?: rule.default

        if (value >= current || firstTime || lowerCoolingOffMs <= 0L) {
            storage.save(
                data.copy(
                    max = data.max + (key to value),
                    pendingLower = data.pendingLower - key
                )
            )
        } else {
            val due = data.pendingLower[key]?.dueMs ?: (nowMs + lowerCoolingOffMs)
            storage.save(
                data.copy(pendingLower = data.pendingLower + (key to PendingLower(value, due)))
            )
        }
    }

    /**
     * A verified count from a real session. If it beats the stored max, the max goes up.
     * Never lowers anything. Returns true if the max was raised.
     */
    fun raiseFromPerformance(key: String, achieved: Int, nowMs: Long): Boolean {
        val rule = rule(key)
        val value = achieved.coerceAtMost(rule.max)
        val data = loadCurrent(nowMs)
        val current = data.max[key] ?: rule.default
        if (value <= current) return false

        storage.save(
            data.copy(
                max = data.max + (key to value),
                pendingLower = data.pendingLower - key
            )
        )
        return true
    }

    fun isConfirmed(): Boolean = storage.load().confirmed

    fun confirm() {
        storage.save(storage.load().copy(confirmed = true))
    }

    private fun rule(key: String): BaselineRule = rules[key] ?: BaselineRule(10, 1, 500)

    /** Loads the data and applies any lowering whose wait is over. */
    private fun loadCurrent(nowMs: Long): BaselineData {
        val raw = storage.load()
        val due = raw.pendingLower.filterValues { it.dueMs <= nowMs }
        if (due.isEmpty()) return raw

        val applied = raw.copy(
            max = raw.max + due.mapValues { it.value.newMax },
            pendingLower = raw.pendingLower - due.keys
        )
        storage.save(applied)
        return applied
    }
}

