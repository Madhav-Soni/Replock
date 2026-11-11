package com.fitness.squatcounter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitness.squatcounter.data.CreditConfig
import com.fitness.squatcounter.domain.ExerciseType
import com.fitness.squatcounter.ui.theme.AppBackground
import com.fitness.squatcounter.ui.theme.AppChip
import com.fitness.squatcounter.ui.theme.AppColors
import com.fitness.squatcounter.ui.theme.ProgressRing
import com.fitness.squatcounter.ui.theme.accent
import com.fitness.squatcounter.ui.theme.emoji
import com.fitness.squatcounter.ui.theme.formatDuration
import com.fitness.squatcounter.ui.theme.surfaceCard

/** The ring on the home screen fills up to this many seconds of available screen time. */
private const val RING_FULL_SECONDS = 1800

/**
 * The first screen: your screen-time balance, the exercise list, and the app blocker.
 */
@Composable
fun HomeScreen(
    balanceSeconds: Int,
    blockerActive: Boolean,
    onSelectExercise: (ExerciseType) -> Unit,
    onOpenBlocker: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                text = "RepLock",
                color = AppColors.TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Earn your screen time with real reps.",
                color = AppColors.TextMuted,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            HeroCard(balanceSeconds)

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "Workouts",
                color = AppColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            for (row in ExerciseType.values().toList().chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (type in row) {
                        ExerciseTile(
                            type = type,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectExercise(type) }
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            BlockerCard(active = blockerActive, onClick = onOpenBlocker)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "\uD83D\uDD12  Everything runs on this phone. No video is saved or sent anywhere.",
                color = AppColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HeroCard(balanceSeconds: Int) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF0C4A6E)))
            )
            .border(1.dp, AppColors.Outline, shape)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bigNumber = if (balanceSeconds >= 60) "${balanceSeconds / 60}" else "$balanceSeconds"
        val unit = if (balanceSeconds >= 60) "min" else "sec"

        ProgressRing(
            progress = balanceSeconds / RING_FULL_SECONDS.toFloat(),
            diameter = 108.dp,
            stroke = 11.dp
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = bigNumber,
                    color = AppColors.TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )
                Text(text = unit, color = AppColors.TextMuted, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.width(18.dp))

        Column {
            Text(text = "Screen time available", color = AppColors.TextMuted, fontSize = 13.sp)
            Text(
                text = formatDuration(balanceSeconds),
                color = AppColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (balanceSeconds > 0) {
                    "Ready for your locked apps"
                } else {
                    "Do a workout to earn time"
                },
                color = if (balanceSeconds > 0) AppColors.Good else AppColors.Warn,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ExerciseTile(
    type: ExerciseType,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = type.accent()
    Column(
        modifier = modifier
            .surfaceCard(shape = RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 150.dp)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = type.emoji(), fontSize = 24.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = type.label,
            color = AppColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(text = taglineOf(type), color = AppColors.TextMuted, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = rewardOf(type),
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BlockerCard(active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .surfaceCard(shape = RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AppColors.Indigo.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "\uD83D\uDD12", fontSize = 22.sp)
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "App blocker",
                color = AppColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Lock distracting apps until you earn time",
                color = AppColors.TextMuted,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        AppChip(
            text = if (active) "Active" else "Off",
            background = if (active) {
                AppColors.Good.copy(alpha = 0.18f)
            } else {
                AppColors.Warn.copy(alpha = 0.18f)
            },
            textColor = if (active) AppColors.Good else AppColors.Warn
        )
    }
}

private fun taglineOf(type: ExerciseType): String = when (type) {
    ExerciseType.SQUATS -> "Legs and core"
    ExerciseType.PUSHUPS -> "Chest and arms"
    ExerciseType.PLANK -> "Hold steady"
    ExerciseType.LUNGES -> "One leg at a time"
    ExerciseType.JUMPING_JACKS -> "Full-body cardio"
}

private fun rewardOf(type: ExerciseType): String {
    val reward = CreditConfig().rewardFor(type.creditKey) ?: return ""
    val amount = formatDuration(reward.secondsPerRep)
    return when (type) {
        ExerciseType.PLANK -> "+$amount per 5 s held"
        else -> "+$amount per rep"
    }
}