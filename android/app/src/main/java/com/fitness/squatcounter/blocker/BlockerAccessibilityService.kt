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
 * Detects which app is in front, charges earned credits while a blocked app is used,
 * warns when time is nearly up, and sends the user Home when credits reach zero.
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
    private lateinit var neverBlock: Set<String>
    private lateinit var ignoredPackages: Set<String>
    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager

    private var foregroundPackage: String? = null
    private var blocklist: Set<String> = emptySet()
    private var cachedBalanceSeconds = 0
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
        if (refresh) refreshState()

        val nowMs = SystemClock.elapsedRealtime()
        val pkg = foregroundPackage
        val isBlockedApp = pkg != null && pkg in blocklist && pkg !in neverBlock
        val screenUsable = powerManager.isInteractive && !keyguardManager.isKeyguardLocked
        val metering = isBlockedApp && screenUsable

        meter.update(nowMs, metering)

        if (!metering) {
            // Charge what is owed up to now (left the app, screen off, locked).
            flush(force = true)
            if (isBlockedApp) {
                // Keep watching so we notice the moment the screen is usable again.
                startTicker()
            } else {
                stopTicker()
                warned = false
            }
            return
        }

        val remaining = cachedBalanceSeconds - meter.pendingSeconds
        if (remaining <= 0) {
            flush(force = true)
            enforce()
            return
        }

        if (remaining <= config.warnSecondsLeft) {
            if (!warned) {
                warned = true
                showToast("Less than ${config.warnSecondsLeft} seconds of screen time left. Do squats to earn more.")
            }
        } else {
            warned = false
        }

        if (meter.pendingSeconds >= config.flushSeconds) {
            flush(force = false)
        }
        startTicker()
    }

    private suspend fun refreshState() {
        val trustedNowMs = repository.getEffectiveTimeSnapshot().wallClockTimeMs
        blocklist = store.effectiveBlocklist(trustedNowMs)
        cachedBalanceSeconds = repository.getBalance()
    }

    /** Spends the whole seconds owed, never more than the balance. */
    private suspend fun flush(force: Boolean) {
        val owed = meter.pendingSeconds
        if (owed <= 0) return
        if (!force && owed < config.flushSeconds) return

        val taken = meter.takeWholeSeconds()
        val balance = repository.getBalance()
        val amount = kotlin.math.min(taken, balance)
        if (amount > 0) {
            repository.spendCredits(amount, "blocked_app_usage")
        }
        cachedBalanceSeconds = repository.getBalance()
    }

    private fun enforce() {
        foregroundPackage = null
        stopTicker()
        warned = false

        performGlobalAction(GLOBAL_ACTION_HOME)
        showToast("Out of credits. Do squats to earn more.")
        openRepLock()
    }

    /**
     * Best effort. Android and MIUI may refuse to start an activity from the background;
     * the toast above is the fallback. Needs testing on the real phone.
     */
    private fun openRepLock() {
        try {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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
        /** True while the system has the service bound. Read by the status screen. */
        @Volatile
        var isConnected: Boolean = false
            private set
    }
}
