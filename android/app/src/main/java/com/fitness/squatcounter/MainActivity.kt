package com.fitness.squatcounter

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fitness.squatcounter.blocker.BlockerViewModel
import com.fitness.squatcounter.blocker.BlockerScreen
import com.fitness.squatcounter.ui.HomeScreen
import com.fitness.squatcounter.ui.SquatScreen
import com.fitness.squatcounter.ui.SquatViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The three screens: exercise list, a workout, and the blocker settings. */
private enum class AppScreen { HOME, WORKOUT, BLOCKER }

class MainActivity : ComponentActivity() {

    private val viewModel: SquatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SquatViewModel(applicationContext) as T
            }
        }
    }

    private val blockerViewModel: BlockerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BlockerViewModel(applicationContext) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the screen on only while a session is live (setup, countdown, active, paused).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { it.keepScreenOn }
                    .distinctUntilChanged()
                    .collect { keepOn ->
                        if (keepOn) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
            }
        }

        setContent {
            var screenName by rememberSaveable { mutableStateOf(AppScreen.HOME.name) }
            val uiState by viewModel.uiState.collectAsState()

            when (AppScreen.valueOf(screenName)) {
                AppScreen.HOME -> HomeScreen(
                    balanceSeconds = uiState.creditBalanceSeconds,
                    onSelectExercise = { type ->
                        viewModel.selectExercise(type)
                        // Start from a clean slate: no leftover summary from an earlier session.
                        viewModel.resetCounter()
                        screenName = AppScreen.WORKOUT.name
                    },
                    onOpenBlocker = { screenName = AppScreen.BLOCKER.name }
                )

                AppScreen.WORKOUT -> SquatScreen(
                    viewModel = viewModel,
                    onBack = { screenName = AppScreen.HOME.name }
                )

                AppScreen.BLOCKER -> BlockerScreen(
                    viewModel = blockerViewModel,
                    onBack = { screenName = AppScreen.HOME.name }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have just changed Accessibility settings.
        blockerViewModel.refreshStatus()
    }

    override fun onPause() {
        super.onPause()
        // Backgrounded, screen off, or a call took over: end a live session as INTERRUPTED.
        viewModel.onAppInterrupted()
    }
}