package com.fitness.squatcounter.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitness.squatcounter.data.CreditConfig
import com.fitness.squatcounter.domain.ExerciseType

private val HomeBg = Color(0xFF0B1220)
private val CardBg = Color(0xFF111827)
private val Accent = Color(0xFF2563EB)
private val Good = Color(0xFF10B981)
private val Warn = Color(0xFFF59E0B)
private val SoftText = Color(0xFF9CA3AF)
private val Neutral = Color(0xFF374151)

/**
 * The first screen: pick an exercise from the list, or open the blocker settings.
 */
@Composable
fun HomeScreen(
    balanceSeconds: Int,
    onSelectExercise: (ExerciseType) -> Unit,
    onOpenBlocker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HomeBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Text(
            text = "RepLock",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Earn your screen time with real exercise.",
            color = SoftText,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        BalanceCard(balanceSeconds)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Choose an exercise",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (type in ExerciseType.values()) {
                ExerciseCard(type = type, onClick = { onSelectExercise(type) })
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Neutral)
                .clickable(onClick = onOpenBlocker)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Blocker: choose which apps to lock",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BalanceCard(balanceSeconds: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(16.dp)
    ) {
        Text(text = "Screen time available", color = SoftText, fontSize = 13.sp)
        Text(
            text = formatDuration(balanceSeconds),
            color = if (balanceSeconds > 0) Good else Warn,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black
        )
        if (balanceSeconds <= 0) {
            Text(
                text = "Do an exercise to earn time for your locked apps.",
                color = SoftText,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ExerciseCard(type: ExerciseType, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = type.label,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = descriptionOf(type), color = SoftText, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = rewardOf(type),
                color = Good,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = "\u203A", color = Accent, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    }
}

private fun descriptionOf(type: ExerciseType): String = when (type) {
    ExerciseType.SQUATS ->
        "Front camera. Keep your shoulders and hips in view. Showing your whole body is the most accurate."
    ExerciseType.PUSHUPS ->
        "Phone on the floor in front of you. Keep your head, shoulders and hands in view."
    ExerciseType.PLANK ->
        "Phone on the floor in front of you. Hold still in your plank. Every 5 seconds counts."
    ExerciseType.LUNGES ->
        "Front camera, a few steps back. Keep your shoulders, hips and knees in view. One knee must drop lower than the other."
    ExerciseType.JUMPING_JACKS ->
        "Front camera, far enough back for your whole body. Hands overhead and feet wide, then back to closed."
}

private fun rewardOf(type: ExerciseType): String {
    val reward = CreditConfig().rewardFor(type.creditKey) ?: return ""
    val amount = formatDuration(reward.secondsPerRep)
    return when (type) {
        ExerciseType.PLANK -> "Earns $amount per 5 seconds held"
        else -> "Earns $amount per rep"
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        minutes > 0 && seconds > 0 -> "${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}