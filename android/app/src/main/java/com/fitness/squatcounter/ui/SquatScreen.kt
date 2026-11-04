package com.fitness.squatcounter.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fitness.squatcounter.domain.RejectionReason
import com.fitness.squatcounter.domain.SquatMode
import com.fitness.squatcounter.session.SessionEndReason
import com.fitness.squatcounter.session.SessionPhase
import com.fitness.squatcounter.session.SetupProblem
import com.fitness.squatcounter.ui.components.CameraPreview
import com.fitness.squatcounter.ui.components.SkeletonOverlay

private val ScreenBg = Color(0xFF0B1220)
private val PanelBg = Color(0xFF111827)
private val Blue = Color(0xFF2563EB)
private val Green = Color(0xFF10B981)
private val Red = Color(0xFFDC2626)
private val Amber = Color(0xFFF59E0B)
private val Slate = Color(0xFF374151)
private val Muted = Color(0xFF9CA3AF)

@Composable
fun SquatScreen(
    viewModel: SquatViewModel,
    onOpenBlocker: () -> Unit,
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

    if (!hasCameraPermission) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ScreenBg)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Camera permission needed",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "RepLock watches your workout live on this phone. Nothing is recorded, saved or sent anywhere.",
                    color = Muted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = Blue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Allow camera", color = Color.White)
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ScreenBg)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            TopBar(
                uiState = uiState,
                showDebug = showDebug,
                onToggleDebug = { showDebug = !showDebug },
                onFlipCamera = { viewModel.toggleCamera() },
                onOpenBlocker = onOpenBlocker
            )

            // The camera image is letterboxed inside this area; controls live outside it.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                CameraPreview(
                    useFrontCamera = uiState.useFrontCamera,
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

            BottomPanel(
                uiState = uiState,
                onStart = { viewModel.startSession() },
                onStop = { viewModel.stopSession() }
            )
        }
    }
}

@Composable
private fun TopBar(
    uiState: SquatUiState,
    showDebug: Boolean,
    onToggleDebug: () -> Unit,
    onFlipCamera: () -> Unit,
    onOpenBlocker: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Chip(
            text = "Time  " + formatSeconds(uiState.creditBalanceSeconds),
            background = Color(0xFF064E3B),
            textColor = Green
        )

        Spacer(modifier = Modifier.weight(1f))

        // Camera and blocker settings are only offered between sessions.
        if (!uiState.sessionPhase.isLive) {
            Chip(
                text = if (uiState.useFrontCamera) "Front cam" else "Back cam",
                background = Slate,
                textColor = Color.White,
                onClick = onFlipCamera
            )
            Spacer(modifier = Modifier.width(6.dp))
            Chip(
                text = "Blocker",
                background = Slate,
                textColor = Color.White,
                onClick = onOpenBlocker
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Chip(
            text = "Debug",
            background = if (showDebug) Blue else Slate,
            textColor = Color.White,
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
                fontSize = 140.sp,
                fontWeight = FontWeight.Black
            )
        }

        SessionPhase.SETUP_CHECK -> {
            Pill(
                text = setupText(uiState.setupProblem),
                color = if (uiState.setupProblem == SetupProblem.NONE) Blue else Amber,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            )
        }

        SessionPhase.PAUSED -> {
            Pill(
                text = pausedText(uiState),
                color = Red,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (uiState.sessionPhase) {
            SessionPhase.IDLE, SessionPhase.ENDED -> {
                if (uiState.sessionPhase == SessionPhase.ENDED) {
                    Text(
                        text = endText(uiState.sessionEndReason),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${uiState.repCount} reps   +${formatSeconds(uiState.sessionEarnedSeconds)} earned",
                        color = Green,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                BigButton(
                    text = if (uiState.sessionPhase == SessionPhase.ENDED) "START NEW SESSION" else "START SQUATS",
                    color = Blue,
                    onClick = onStart
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Prop the phone up so your shoulders and hips are in view. Front or back camera both work. Showing your whole body is the most accurate.",
                    color = Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            SessionPhase.SETUP_CHECK, SessionPhase.COUNTDOWN -> {
                Text(
                    text = if (uiState.sessionPhase == SessionPhase.COUNTDOWN) "Get ready" else "Setting up",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                BigButton(text = "CANCEL", color = Slate, onClick = onStop)
            }

            SessionPhase.ACTIVE, SessionPhase.PAUSED -> {
                Text(
                    text = "${uiState.repCount}",
                    color = Color.White,
                    fontSize = 88.sp,
                    fontWeight = FontWeight.Black
                )
                Text(text = "REPS", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (uiState.squatMode == SquatMode.CLOSE_UP) {
                        "Close-up mode (shoulders and hips)"
                    } else {
                        "Full-body mode"
                    },
                    color = Muted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "+${formatSeconds(uiState.sessionEarnedSeconds)} earned",
                    color = Green,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (uiState.lastRejectionReason != RejectionReason.NONE) {
                    Text(
                        text = "Last rep not counted: " +
                            uiState.lastRejectionReason.name.lowercase().replace('_', ' '),
                        color = Amber,
                        fontSize = 12.sp
                    )
                    uiState.rejectionDetail?.let { detail ->
                        Text(text = detail, color = Amber, fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                BigButton(text = "STOP", color = Red, onClick = onStop)
            }
        }
    }
}

@Composable
private fun DebugPanel(
    uiState: SquatUiState,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xCC111827), RoundedCornerShape(12.dp))
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
            "Scale   ${"%.2f".format(uiState.torsoScale)}"
        )
        for (line in lines) {
            Text(
                text = line,
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        uiState.errorMessage?.let {
            Text(text = it, color = Amber, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Chip(text = "Reset", background = Slate, textColor = Color.White, onClick = onReset)
    }
}

@Composable
private fun Chip(
    text: String,
    background: Color,
    textColor: Color,
    onClick: (() -> Unit)? = null
) {
    val base = Modifier
        .clip(RoundedCornerShape(20.dp))
        .background(background)
    Text(
        text = text,
        color = textColor,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun BigButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun setupText(problem: SetupProblem): String = when (problem) {
    SetupProblem.NONE -> "Looks good. Hold your position."
    SetupProblem.BODY_NOT_VISIBLE -> "Keep your shoulders and hips in view"
    SetupProblem.FEED_LOST -> "Camera feed lost or frozen"
    SetupProblem.PHONE_MOVING -> "Put the phone down and keep it still"
}

private fun pausedText(uiState: SquatUiState): String =
    if (uiState.setupProblem == SetupProblem.BODY_NOT_VISIBLE &&
        uiState.squatMode == SquatMode.FULL_BODY
    ) {
        "Paused: keep your whole body in view"
    } else {
        "Paused: " + setupText(uiState.setupProblem)
    }

private fun endText(reason: SessionEndReason?): String = when (reason) {
    SessionEndReason.USER_STOP -> "Session complete"
    SessionEndReason.PAUSE_TIMEOUT -> "Ended: paused too long"
    SessionEndReason.INTERRUPTED -> "Session interrupted"
    null -> "Session ended"
}

private fun formatSeconds(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}