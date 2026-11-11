package com.fitness.squatcounter.blocker

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitness.squatcounter.ui.theme.AppBackground
import com.fitness.squatcounter.ui.theme.AppChip
import com.fitness.squatcounter.ui.theme.AppColors
import com.fitness.squatcounter.ui.theme.CircleIconButton
import com.fitness.squatcounter.ui.theme.surfaceCard
import kotlin.math.abs

/**
 * Pick which apps cost screen time, and see whether the blocking service is running.
 */
@Composable
fun BlockerScreen(
    viewModel: BlockerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(text = "\u2039", onClick = onBack)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "App blocker",
                        color = AppColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Lock apps until you earn time",
                        color = AppColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            StatusCard(
                state = state,
                onOpenAccessibility = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onOpenAppInfo = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Apps that cost screen time",
                color = AppColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Switch on the apps you want locked. Phone, messages, settings and the launcher can never be blocked.",
                color = AppColors.TextMuted,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (state.loading) {
                Text(text = "Loading apps...", color = AppColors.TextMuted)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.apps, key = { it.packageName }) { app ->
                        val dueMs = state.pendingRemovals[app.packageName]
                        val pendingText = dueMs?.let {
                            "Unblocks in ${formatWait(it - state.trustedNowMs)}"
                        }
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
}

@Composable
private fun StatusCard(
    state: BlockerUiState,
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit
) {
    val active = state.serviceEnabled && state.serviceConnected
    val title: String
    val color: Color
    when {
        active -> {
            title = "Blocking is active"
            color = AppColors.Good
        }
        state.serviceEnabled -> {
            title = "Enabled, but not running"
            color = AppColors.Warn
        }
        else -> {
            title = "Blocking is off"
            color = AppColors.Bad
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .surfaceCard(shape = RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                color = color,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        when {
            active -> {
                Text(
                    text = "RepLock checks which app is in front and sends you Home when your earned time runs out.",
                    color = AppColors.TextMuted,
                    fontSize = 12.sp
                )
            }

            state.serviceEnabled -> {
                Text(
                    text = "MIUI may have stopped it. Check Autostart and battery settings below, then reopen RepLock.",
                    color = AppColors.TextMuted,
                    fontSize = 12.sp
                )
            }

            else -> {
                Text(
                    text = "1. Open App info, tap the menu (three dots) and choose Allow restricted settings, if it is shown (Android 13).\n2. Open Accessibility settings, find RepLock and turn it on.",
                    color = AppColors.TextMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppChip(text = "App info", onClick = onOpenAppInfo)
                    AppChip(
                        text = "Accessibility settings",
                        background = AppColors.Indigo,
                        textColor = Color.White,
                        onClick = onOpenAccessibility
                    )
                }
            }
        }

        if (!active) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Xiaomi/MIUI: turn on Autostart for RepLock, set Battery saver to No restrictions, and lock RepLock in the Recents screen so MIUI does not stop it.",
                color = AppColors.TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

private val AvatarColors = listOf(
    Color(0xFF6366F1),
    Color(0xFF22D3EE),
    Color(0xFFF472B6),
    Color(0xFFFBBF24),
    Color(0xFF34D399),
    Color(0xFFA78BFA)
)

@Composable
private fun AppRow(
    label: String,
    packageName: String,
    blocked: Boolean,
    pendingText: String?,
    onToggle: () -> Unit
) {
    val avatarColor = AvatarColors[abs(packageName.hashCode() % AvatarColors.size)]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .surfaceCard(shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(avatarColor.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label.firstOrNull()?.uppercase() ?: "?",
                color = avatarColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = AppColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = packageName,
                color = AppColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (pendingText != null) {
                Text(text = pendingText, color = AppColors.Warn, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = blocked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppColors.Indigo,
                uncheckedThumbColor = AppColors.TextMuted,
                uncheckedTrackColor = AppColors.SurfaceHigh,
                uncheckedBorderColor = AppColors.Outline
            )
        )
    }
}

private fun formatWait(ms: Long): String {
    val totalMinutes = (ms / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "${hours}h ${minutes}m"
}