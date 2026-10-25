package com.fitness.squatcounter.blocker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitness.squatcounter.data.AndroidTimeProvider
import com.fitness.squatcounter.data.db.RepLockDatabase
import com.fitness.squatcounter.data.repository.CreditRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppEntry(
    val packageName: String,
    val label: String
)

data class BlockerUiState(
    val apps: List<AppEntry> = emptyList(),
    val blocked: Set<String> = emptySet(),
    val pendingRemovals: Map<String, Long> = emptyMap(),
    val trustedNowMs: Long = 0L,
    val loading: Boolean = true,
    val serviceEnabled: Boolean = false,
    val serviceConnected: Boolean = false
)

class BlockerViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val config = BlockerConfig()
    private val neverBlock = NeverBlockList.resolve(appContext)
    private val store = BlocklistStore(
        storage = SharedPrefsBlocklistStorage(appContext),
        neverBlock = neverBlock,
        coolingOffMs = config.removalCoolingOffMs
    )
    private val repository = CreditRepository(
        database = RepLockDatabase.getInstance(appContext),
        timeProvider = AndroidTimeProvider(appContext)
    )

    private val _uiState = MutableStateFlow(BlockerUiState())
    val uiState: StateFlow<BlockerUiState> = _uiState.asStateFlow()

    init {
        loadApps()
        refreshStatus()
    }

    fun refreshStatus() {
        _uiState.update {
            it.copy(
                serviceEnabled = isServiceEnabled(),
                serviceConnected = BlockerAccessibilityService.isConnected
            )
        }
        syncBlocklist()
    }

    fun toggle(packageName: String) {
        viewModelScope.launch {
            val trustedNowMs = repository.getEffectiveTimeSnapshot().wallClockTimeMs
            val data = store.snapshot()
            when {
                packageName in data.pendingRemovals -> store.cancelRemoval(packageName)
                packageName in data.blocked -> store.requestRemoval(packageName, trustedNowMs)
                else -> store.add(packageName)
            }
            syncBlocklist()
        }
    }

    private fun syncBlocklist() {
        viewModelScope.launch {
            val trustedNowMs = repository.getEffectiveTimeSnapshot().wallClockTimeMs
            store.effectiveBlocklist(trustedNowMs)
            val data = store.snapshot()
            _uiState.update {
                it.copy(
                    blocked = data.blocked - neverBlock,
                    pendingRemovals = data.pendingRemovals,
                    trustedNowMs = trustedNowMs
                )
            }
        }
    }

    private fun loadApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.Default) {
                val pm = appContext.packageManager
                val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(launcherIntent, 0)
                    .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                    .filter { it.packageName !in neverBlock }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }
            _uiState.update { it.copy(apps = apps, loading = false) }
        }
    }

    private fun isServiceEnabled(): Boolean {
        val component = ComponentName(appContext, BlockerAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }
}
