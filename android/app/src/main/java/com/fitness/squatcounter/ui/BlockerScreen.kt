package com.fitness.squatcounter.blocker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitness.squatcounter.blocker.BlockerUiState
import com.fitness.squatcounter.blocker.BlockerViewModel

private val ScreenBackground = Color(0xFF0B1220)
private val CardBackground = Color(0xFF111827)
private val MutedText = Color(0xFF9CA3AF)

@Composable
fun BlockerScreen(
    viewModel: BlockerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    androidx.activity.compose.BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Back", color = Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Blocker",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        StatusCard(
            state = state,
            onOpenAccessibility = {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onOpenAppInfo = {
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Apps that cost screen time",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
        Text(
            text = "Tick the apps you want locked until you earn time. Phone, messages, settings and the launcher can never be blocked.",
            color = MutedText,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (state.loading) {
            Text(text = "Loading apps...", color = MutedText)
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.apps, key = { it.packageName }) { app ->
                    val dueMs = state.pendingRemovals[app.packageName]
                    val pendingText = dueMs?.let { "Unblocks in ${formatWait(it - state.trustedNowMs)}" }
                    AppRow(
                        label = app.label,
                        packageName = app.packageName,
                        blocked = app.packageName in state.blocked,
                        pendingText = pendingText,
                        onToggle = { viewModel.toggle(app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    state: BlockerUiState,
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        when {
            state.serviceEnabled && state.serviceConnected -> {
                Text(
                    text = "Blocking is ACTIVE",
                    color = Color(0xFF10B981),
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
            state.serviceEnabled -> {
                Text(
                    text = "Enabled, but the service is not running",
                    color = Color(0xFFF59E0B),
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = "MIUI may have stopped it. Check Autostart and battery settings below, then reopen RepLock.",
                    color = MutedText,
                    fontSize = 12.sp
                )
            }
            else -> {
                Text(
                    text = "Blocking is OFF",
                    color = Color(0xFFEF4444),
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = "1. Open App info, tap the menu (three dots) and choose Allow restricted settings, if it is shown (Android 13).\n2. Open Accessibility settings, find RepLock and turn it on.",
                    color = MutedText,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onOpenAppInfo,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "App info", color = Color.White, fontSize = 12.sp)
            }
            Button(
                onClick = onOpenAccessibility,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Accessibility settings", color = Color.White, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Xiaomi/MIUI: turn on Autostart for RepLock, set Battery saver to No restrictions, and lock RepLock in the Recents screen so MIUI does not kill it.",
            color = MutedText,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun AppRow(
    label: String,
    packageName: String,
    blocked: Boolean,
    pendingText: String?,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = blocked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF2563EB),
                uncheckedColor = MutedText,
                checkmarkColor = Color.White
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = label, color = Color.White, fontSize = 16.sp)
            Text(text = packageName, color = MutedText, fontSize = 11.sp)
            if (pendingText != null) {
                Text(text = pendingText, color = Color(0xFFF59E0B), fontSize = 12.sp)
            }
        }
    }
}

private fun formatWait(ms: Long): String {
    val totalMinutes = (ms / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "${hours}h ${minutes}m"
}
