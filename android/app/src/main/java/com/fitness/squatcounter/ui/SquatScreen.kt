package com.fitness.squatcounter.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fitness.squatcounter.domain.ExerciseType
import com.fitness.squatcounter.domain.RejectionReason
import com.fitness.squatcounter.domain.SquatMode
import com.fitness.squatcounter.session.SessionEndReason
import com.fitness.squatcounter.session.SessionPhase
import com.fitness.squatcounter.session.SetupProblem
import com.fitness.squatcounter.ui.components.CameraPreview
import com.fitness.squatcounter.ui.components.SkeletonOverlay
import com.fitness.squatcounter.ui.theme.AppBackground
import com.fitness.squatcounter.ui.theme.AppChip
import com.fitness.squatcounter.ui.theme.AppColors
import com.fitness.squatcounter.ui.theme.AppPill
import com.fitness.squatcounter.ui.theme.CircleIconButton
import com.fitness.squatcounter.ui.theme.GradientButton
import com.fitness.squatcounter.ui.theme.SoftButton
import com.fitness.squatcounter.ui.theme.accent
import com.fitness.squatcounter.ui.theme.emoji
import com.fitness.squatcounter.ui.theme.formatDuration
import com.fitness.squatcounter.ui.theme.surfaceCard

/**
 * The workout screen: camera in the middle, controls above and below it.
 */
@Composable
fun SquatScreen(
    viewModel: SquatViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    var showDebug by rememberSaveable { mutableStateOf(false) }

    // System back during a session ends it cleanly (like pressing STOP), then leaves.
    BackHandler {
        if (uiState.sessionPhase.isLive) viewModel.stopSession()
        onBack()
    }

    if (!hasCameraPermission) {
        PermissionScreen(
            modifier = modifier,
            onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onBack = onBack
        )
        return
    }

    AppBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            TopBar(
                uiState = uiState,
                showDebug = showDebug,
                onToggleDebug = { showDebug = !showDebug },
                onBack = onBack
            )

            // The camera image is letterboxed inside this card; controls live outside it.
            val cameraShape = RoundedCornerShape(26.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(cameraShape)
                    .background(Color.Black)
                    .border(1.dp, AppColors.Outline, cameraShape)
            ) {
                CameraPreview(
                    useFrontCamera = true, // front camera only while we test the exercises
                    onFrame = { imageProxy, isFrontCamera ->
                        viewModel.onCameraFrame(imageProxy, isFrontCamera)
                    }
                )

                SkeletonOverlay(
                    landmarks = uiState.landmarks,
                    isFullBodyVisible = uiState.isFullBodyVisible
                )

                CameraMessages(uiState)

                if (showDebug) {
                    DebugPanel(
                        uiState = uiState,
                        onReset = { viewModel.resetCounter() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            BottomPanel(
                uiState = uiState,
                onStart = { viewModel.startSession() },
                onStop = { viewModel.stopSession() }
            )
        }
    }
}

@Composable
private fun PermissionScreen(
    modifier: Modifier,
    onAllow: () -> Unit,
    onBack: () -> Unit
) {
    AppBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(AppColors.Indigo.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "\uD83D\uDCF7", fontSize = 38.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Camera access needed",
                color = AppColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "RepLock watches your workout live on this phone. Nothing is recorded, saved or sent anywhere.",
                color = AppColors.TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            GradientButton(text = "ALLOW CAMERA", onClick = onAllow)
            Spacer(modifier = Modifier.height(10.dp))
            SoftButton(text = "Back", onClick = onBack)
        }
    }
}

@Composable
private fun TopBar(
    uiState: SquatUiState,
    showDebug: Boolean,
    onToggleDebug: () -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leaving is only offered between sessions. During one, use STOP first.
        if (!uiState.sessionPhase.isLive) {
            CircleIconButton(text = "\u2039", onClick = onBack)
            Spacer(modifier = Modifier.width(10.dp))
        }

        Text(
            text = uiState.selectedExercise.emoji() + "  " + uiState.selectedExercise.label,
            color = AppColors.TextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.weight(1f))

        AppChip(
            text = "\u23F1 " + formatDuration(uiState.creditBalanceSeconds),
            background = AppColors.Good.copy(alpha = 0.16f),
            textColor = AppColors.Good
        )
        Spacer(modifier = Modifier.width(6.dp))
        AppChip(
            text = "Debug",
            background = if (showDebug) AppColors.Indigo else AppColors.SurfaceHigh,
            onClick = onToggleDebug
        )
    }
}

@Composable
private fun BoxScope.CameraMessages(uiState: SquatUiState) {
    when (uiState.sessionPhase) {
        SessionPhase.COUNTDOWN -> {
            Text(
                text = "${uiState.countdownSecondsLeft}",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                fontSize = 150.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 6f), 16f))
            )
        }

        SessionPhase.SETUP_CHECK -> {
            AppPill(
                text = setupText(uiState.setupProblem, uiState.selectedExercise),
                color = if (uiState.setupProblem == SetupProblem.NONE) AppColors.Indigo else AppColors.Warn,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
            )
        }

        SessionPhase.PAUSED -> {
            AppPill(
                text = pausedText(uiState),
                color = AppColors.Bad,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
            )
        }

        else -> Unit
    }
}

@Composable
private fun BottomPanel(
    uiState: SquatUiState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val panelShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val accent = uiState.selectedExercise.accent()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(panelShape)
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Outline, panelShape)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (uiState.sessionPhase) {
            SessionPhase.IDLE, SessionPhase.ENDED -> {
                if (uiState.sessionPhase == SessionPhase.ENDED) {
                    Text(
                        text = endText(uiState.sessionEndReason),
                        color = AppColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            value = "${uiState.repCount}",
                            label = uiState.countLabel.lowercase(),
                            color = accent,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            value = "+" + formatDuration(uiState.sessionEarnedSeconds),
                            label = "earned",
                            color = AppColors.Good,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                GradientButton(
                    text = if (uiState.sessionPhase == SessionPhase.ENDED) {
                        "START NEW SESSION"
                    } else {
                        "START " + uiState.selectedExercise.label.uppercase()
                    },
                    onClick = onStart
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = hintText(uiState.selectedExercise),
                    color = AppColors.TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            SessionPhase.SETUP_CHECK, SessionPhase.COUNTDOWN -> {
                Text(
                    text = if (uiState.sessionPhase == SessionPhase.COUNTDOWN) "Get ready" else "Setting up",
                    color = AppColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                SoftButton(text = "CANCEL", onClick = onStop)
            }

            SessionPhase.ACTIVE, SessionPhase.PAUSED -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${uiState.repCount}",
                            color = accent,
                            fontSize = 84.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = uiState.countLabel,
                            color = AppColors.TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        AppChip(
                            text = "+" + formatDuration(uiState.sessionEarnedSeconds) + " earned",
                            background = AppColors.Good.copy(alpha = 0.16f),
                            textColor = AppColors.Good
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when {
                                uiState.selectedExercise != ExerciseType.SQUATS -> uiState.selectedExercise.label
                                uiState.squatMode == SquatMode.CLOSE_UP -> "Close-up mode"
                                else -> "Full-body mode"
                            },
                            color = AppColors.TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                if (uiState.lastRejectionReason != RejectionReason.NONE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .surfaceCard(
                                shape = RoundedCornerShape(14.dp),
                                color = AppColors.Warn.copy(alpha = 0.12f)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Last rep not counted: " +
                                uiState.lastRejectionReason.name.lowercase().replace('_', ' '),
                            color = AppColors.Warn,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        uiState.rejectionDetail?.let { detail ->
                            Text(text = detail, color = AppColors.Warn, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                GradientButton(
                    text = "STOP",
                    onClick = onStop,
                    colors = listOf(AppColors.Bad, AppColors.BadDeep)
                )
            }
        }
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .surfaceCard(shape = RoundedCornerShape(16.dp), color = AppColors.SurfaceHigh)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, color = color, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(text = label, color = AppColors.TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun DebugPanel(
    uiState: SquatUiState,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color(0xE60B1220))
            .border(1.dp, AppColors.Outline, shape)
            .padding(10.dp)
    ) {
        val lines = listOf(
            "FPS     ${uiState.fps}",
            "Knee    ${"%.0f".format(uiState.kneeAngle)} deg",
            "Hip     ${"%.0f".format(uiState.hipAngle)} deg",
            "State   ${uiState.currentState}",
            "Reject  ${uiState.lastRejectionReason}",
            "Body    ${if (uiState.isFullBodyVisible) "visible" else "not visible"}",
            "Feed    ${if (uiState.feedHealthy) "ok" else "stale or frozen"}",
            "Session ${uiState.sessionPhase}",
            "Mode    ${uiState.squatMode}",
            "Drop    ${"%.2f".format(uiState.dropRatio)} torso",
            "Scale   ${"%.2f".format(uiState.torsoScale)}",
            "PeakDrop ${"%.2f".format(uiState.peakDrop)}",
            "MinKnee  ${"%.0f".format(uiState.lowestKnee)} deg",
            "Lost     ${uiState.trackingLostCount} times"
        )
        for (line in lines) {
            Text(
                text = line,
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        if (uiState.exerciseDebug.isNotEmpty()) {
            for (line in uiState.exerciseDebug.split("\n")) {
                Text(
                    text = line,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        uiState.errorMessage?.let {
            Text(text = it, color = AppColors.Warn, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        AppChip(text = "Reset", onClick = onReset)
    }
}

private fun hintText(exercise: ExerciseType): String = when (exercise) {
    ExerciseType.SQUATS ->
        "Prop the phone up so your shoulders and hips are in view. Showing your whole body is the most accurate."
    ExerciseType.PUSHUPS ->
        "Put the phone on the floor in front of you, tilted up, so your head, shoulders and hands are in view. Be in the top push-up position when the countdown ends."
    ExerciseType.PLANK ->
        "Put the phone on the floor in front of you, tilted up, so your head, shoulders and hands are in view. Get into your plank before the countdown ends and hold it still. Every 5 seconds earns time."
    ExerciseType.LUNGES ->
        "Prop the phone up a few steps back so your shoulders, hips and knees stay in view while you lunge. Stand upright until the countdown ends. Either leg counts."
    ExerciseType.JUMPING_JACKS ->
        "Prop the phone up and stand far enough back to show your whole body, with room above your head for your hands. Start with your arms down and feet together."
}

private fun setupText(problem: SetupProblem, exercise: ExerciseType): String = when (problem) {
    SetupProblem.NONE -> "Looks good. Hold your position."
    SetupProblem.BODY_NOT_VISIBLE ->
        when (exercise) {
            ExerciseType.SQUATS -> "Keep your shoulders and hips in view"
            ExerciseType.LUNGES -> "Keep your shoulders, hips and knees in view"
            ExerciseType.JUMPING_JACKS -> "Step back: show your whole body, with room above your head"
            else -> "Keep your head, shoulders and hands in view"
        }
    SetupProblem.FEED_LOST -> "Camera feed lost or frozen"
    SetupProblem.PHONE_MOVING -> "Put the phone down and keep it still"
}

private fun pausedText(uiState: SquatUiState): String =
    if (uiState.setupProblem == SetupProblem.BODY_NOT_VISIBLE &&
        uiState.selectedExercise == ExerciseType.SQUATS &&
        uiState.squatMode == SquatMode.FULL_BODY
    ) {
        "Paused: keep your whole body in view"
    } else {
        "Paused: " + setupText(uiState.setupProblem, uiState.selectedExercise)
    }

private fun endText(reason: SessionEndReason?): String = when (reason) {
    SessionEndReason.USER_STOP -> "Session complete"
    SessionEndReason.PAUSE_TIMEOUT -> "Ended: paused too long"
    SessionEndReason.INTERRUPTED -> "Session interrupted"
    null -> "Session ended"
}