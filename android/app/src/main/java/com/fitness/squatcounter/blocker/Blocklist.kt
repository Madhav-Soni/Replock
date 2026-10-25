package com.fitness.squatcounter.blocker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telecom.TelecomManager

/**
 * Apps that can NEVER be blocked: phone, messages, settings, launcher, emergency, and RepLock itself.
 * Enforced everywhere: the picker hides them, the store refuses them, the service ignores them,
 * even if someone edits the stored blocklist by hand.
 */
object NeverBlockList {

    val STATIC: Set<String> = setOf(
        // System
        "android",
        "com.android.settings",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.packageinstaller",
        // Phone, calls, emergency
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.android.emergency",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.contacts",
        "com.google.android.contacts",
        // Messages
        "com.android.mms",
        "com.android.messaging",
        "com.google.android.apps.messaging",
        // Launchers
        "com.miui.home",
        "com.mi.android.globallauncher",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        // MIUI security centre: needed to fix Autostart and battery settings
        "com.miui.securitycenter"
    )

    /** Static list plus this device's actual default dialer, SMS app and home launcher. */
    fun resolve(context: Context): Set<String> {
        val result = STATIC.toMutableSet()
        result.add(context.packageName)
        try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecom?.defaultDialerPackage?.let { result.add(it) }
        } catch (_: Exception) {}
        try {
            Telephony.Sms.getDefaultSmsPackage(context)?.let { result.add(it) }
        } catch (_: Exception) {}
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager
                .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
                ?.let { result.add(it) }
        } catch (_: Exception) {}
        return result
    }
}

/**
 * @param blocked package names the user chose to block
 * @param pendingRemovals package name -> trusted time (ms) when its removal takes effect
 */
data class BlocklistData(
    val blocked: Set<String> = emptySet(),
    val pendingRemovals: Map<String, Long> = emptyMap()
)

interface BlocklistStorage {
    fun load(): BlocklistData
    fun save(data: BlocklistData)
}

class SharedPrefsBlocklistStorage(context: Context) : BlocklistStorage {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    override fun load(): BlocklistData {
        val blocked = (prefs.getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()).toSet()
        val pending = (prefs.getStringSet(KEY_PENDING, emptySet()) ?: emptySet())
            .mapNotNull { entry ->
                val sep = entry.lastIndexOf('|')
                if (sep <= 0) return@mapNotNull null
                val due = entry.substring(sep + 1).toLongOrNull() ?: return@mapNotNull null
                entry.substring(0, sep) to due
            }
            .toMap()
        return BlocklistData(blocked, pending)
    }
    override fun save(data: BlocklistData) {
        prefs.edit()
            .putStringSet(KEY_BLOCKED, data.blocked.toSet())
            .putStringSet(KEY_PENDING, data.pendingRemovals.map { "${it.key}|${it.value}" }.toSet())
            .apply()
    }
    private companion object {
        const val PREFS_NAME = "replock_blocklist"
        const val KEY_BLOCKED = "blocked"
        const val KEY_PENDING = "pending_removals"
    }
}

/**
 * The user's blocklist. Adding an app is instant. Removing one waits for the cooling-off period.
 */
class BlocklistStore(
    private val storage: BlocklistStorage,
    private val neverBlock: Set<String>,
    private val coolingOffMs: Long
) {
    fun add(packageName: String): Boolean {
        if (packageName in neverBlock) return false
        val data = storage.load()
        storage.save(data.copy(blocked = data.blocked + packageName, pendingRemovals = data.pendingRemovals - packageName))
        return true
    }
    fun requestRemoval(packageName: String, nowMs: Long) {
        val data = storage.load()
        if (packageName !in data.blocked) return
        if (coolingOffMs <= 0L) {
            storage.save(data.copy(blocked = data.blocked - packageName, pendingRemovals = data.pendingRemovals - packageName))
        } else if (packageName !in data.pendingRemovals) {
            storage.save(data.copy(pendingRemovals = data.pendingRemovals + (packageName to nowMs + coolingOffMs)))
        }
    }
    fun cancelRemoval(packageName: String) {
        val data = storage.load()
        if (packageName in data.pendingRemovals) {
            storage.save(data.copy(pendingRemovals = data.pendingRemovals - packageName))
        }
    }
    fun effectiveBlocklist(nowMs: Long): Set<String> {
        val data = storage.load()
        val due = data.pendingRemovals.filterValues { it <= nowMs }.keys
        val current = if (due.isEmpty()) data else data.copy(blocked = data.blocked - due, pendingRemovals = data.pendingRemovals - due).also { storage.save(it) }
        return current.blocked - neverBlock
    }
    fun snapshot(): BlocklistData = storage.load()
}
