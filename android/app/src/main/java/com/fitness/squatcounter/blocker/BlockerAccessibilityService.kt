package com.fitness.squatcounter.blocker

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.fitness.squatcounter.MainActivity
import com.fitness.squatcounter.data.AndroidTimeProvider
import com.fitness.squatcounter.data.AppLimitsStore
import com.fitness.squatcounter.data.SharedPrefsAppLimitsStorage
import com.fitness.squatcounter.data.db.RepLockDatabase
import com.fitness.squatcounter.data.repository.CreditRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Detects which app is in front. A blocked app first uses its own free daily time, then earned
 * credits. Warns when time is nearly up, and sends the user Home when both are gone.
 *
 * It only listens for window-change events and never reads screen content.
 * It can be bypassed (disable it in Settings, force‑stop, uninstall). See the threat model.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private val config = BlockerConfig()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val evalMutex = Mutex()
    private val meter = UsageMeter(config.maxGapMs)

    private lateinit var repository: CreditRepository
    private lateinit var store: BlocklistStore
    private lateinit var limits: AppLimitsStore
    private lateinit var neverBlock: Set<String>
    private lateinit var ignoredPackages: Set<String>
    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager

    private var foregroundPackage: String? = null
    private var blocklist: Set<String> = emptySet()
    private var cachedBalanceSeconds = 0
    private var cachedFreeSeconds = 0
    /** The blocked app that the meter's pending seconds belong to. */
    private var meteredPkg: String? = null
    private var warned = false
    private var tickJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        repository = CreditRepository(
            database = RepLockDatabase.getInstance(applicationContext),
            timeProvider = AndroidTimeProvider(applicationContext)
        )
        neverBlock = NeverBlockList.resolve(this)
        store = BlocklistStore(
            storage = SharedPrefsBlocklistStorage(this),
            neverBlock = neverBlock,
            coolingOffMs = config.removalCoolingOffMs
        )
        limits = AppLimitsStore(SharedPrefsAppLimitsStorage(this))
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        // Keyboards and the system UI open their own windows over the app in use.
        // They must not count as "the user left the app".
        val imePackages = try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.map { it.packageName }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
        ignoredPackages = imePackages + "com.android.systemui"

        isConnected = true
        requestEvaluate(refresh = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignoredPackages) return

        foregroundPackage = pkg
        requestEvaluate(refresh = true)
    }

    override fun onInterrupt() {
        // Nothing to interrupt.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isConnected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isConnected = false
        scope.cancel()
        super.onDestroy()
    }

    // ---- Core logic ----

    private fun requestEvaluate(refresh: Boolean) {
        scope.launch {
            evalMutex.withLock {
                try {
                    evaluateLocked(refresh)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Never let a failure here crash the service.
                }
            }
        }
    }

    private suspend fun evaluateLocked(refresh: Boolean) {
        val nowMs = SystemClock.elapsedRealtime()
        val pkg = foregroundPackage

        // Switched apps: what is owed belongs to the app that was in front, not the new one.
        val previous = meteredPkg
        if (previous != null && previous != pkg) {
            meter.update(nowMs, false)
            flush(previous, force = true)
            meteredPkg = null
            warned = false
        }

        if (refresh) refreshState()

        val isBlockedApp = pkg != null && pkg in blocklist && pkg !in neverBlock
        val screenUsable = powerManager.isInteractive && !keyguardManager.isKeyguardLocked
        val metering = isBlockedApp && screenUsable

        meter.update(nowMs, metering)

        if (!metering || pkg == null) {
            // Charge what is owed up to now (left the app, screen off, locked).
            flush(meteredPkg ?: pkg, force = true)
            meteredPkg = null
            if (isBlockedApp) {
                // Keep watching so we notice the moment the screen is usable again.
                startTicker()
            } else {
                stopTicker()
                warned = false
            }
            return
        }

        meteredPkg = pkg

        // Free time for this app first, then the shared credit pool.
        val remaining = cachedFreeSeconds + cachedBalanceSeconds - meter.pendingSeconds
        if (remaining <= 0) {
            flush(pkg, force = true)
            enforce(pkg)
            return
        }

        if (remaining <= config.warnSecondsLeft) {
            if (!warned) {
                warned = true
                showToast("Less than ${config.warnSecondsLeft} seconds left on ${appLabel(pkg)}. Do exercise to earn more.")
            }
        } else {
            warned = false
        }

        if (meter.pendingSeconds >= config.flushSeconds) {
            flush(pkg, force = false)
        }
        startTicker()
    }

    private suspend fun refreshState() {
        val trustedNowMs = repository.getEffectiveTimeSnapshot().wallClockTimeMs
        blocklist = store.effectiveBlocklist(trustedNowMs)
        refreshCaches(foregroundPackage)
    }

    /** Reloads the credit balance and the free time left today for [pkg]. */
    private suspend fun refreshCaches(pkg: String?) {
        cachedBalanceSeconds = repository.getBalance()
        cachedFreeSeconds = if (pkg == null) 0 else limits.freeRemaining(pkg, currentDayStartMs())
    }

    private suspend fun currentDayStartMs(): Long =
        repository.getStartOfDayMs(repository.getEffectiveTimeSnapshot().wallClockTimeMs)

    /**
     * Charges the whole seconds owed to [pkg]: free daily time first (exactly what is left),
     * then earned credits for the rest, never more than the balance.
     */
    private suspend fun flush(pkg: String?, force: Boolean) {
        val owed = meter.pendingSeconds
        if (owed <= 0) return
        if (!force && owed < config.flushSeconds) return

        val taken = meter.takeWholeSeconds()
        if (pkg == null) return

        val fromFree = limits.consumeFree(pkg, taken, currentDayStartMs())
        val fromCredits = kotlin.math.min(taken - fromFree, repository.getBalance())
        if (fromCredits > 0) {
            repository.spendCredits(fromCredits, "blocked_app_usage")
        }
        refreshCaches(foregroundPackage)
    }

    private fun enforce(pkg: String) {
        foregroundPackage = null
        meteredPkg = null
        stopTicker()
        warned = false

        val label = appLabel(pkg)
        performGlobalAction(GLOBAL_ACTION_HOME)
        showToast("Time's up for $label. Do exercise to earn more.")
        openRepLock(label)
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    /**
     * Best effort. Android and MIUI may refuse to start an activity from the background;
     * the toast above is the fallback. Needs testing on the real phone.
     */
    private fun openRepLock(timesUpApp: String) {
        try {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_TIMES_UP_APP, timesUpApp)
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    private fun startTicker() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (true) {
                delay(config.tickMs)
                requestEvaluate(refresh = false)
            }
        }
    }

    private fun stopTicker() {
        tickJob?.cancel()
        tickJob = null
    }

    companion object {
        /** Intent extra for MainActivity: the app whose time just ran out. Shown by the Stage C screen. */
        const val EXTRA_TIMES_UP_APP = "com.fitness.squatcounter.TIMES_UP_APP"

        /** True while the system has the service bound. Read by the status screen. */
        @Volatile
        var isConnected: Boolean = false
            private set
    }
}