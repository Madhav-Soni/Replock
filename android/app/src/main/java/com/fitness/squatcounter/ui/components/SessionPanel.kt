package com.fitness.squatcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitness.squatcounter.session.SessionEndReason
import com.fitness.squatcounter.session.SessionPhase
import com.fitness.squatcounter.session.SetupProblem
import com.fitness.squatcounter.ui.SquatUiState

/**
 * Minimal session controls drawn over the camera:
 * Start button, setup guidance, big countdown, and a Stop button while exercising.
 * The panel itself does not intercept touches outside its buttons.
 */
@Composable
fun SessionPanel(
    uiState: SquatUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        when (uiState.sessionPhase) {
            SessionPhase.IDLE, SessionPhase.ENDED -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiState.sessionPhase == SessionPhase.ENDED) {
                        PanelText(text = summaryText(uiState), color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (uiState.sessionPhase == SessionPhase.ENDED) {
                                "START NEW SESSION"
                            } else {
                                "START SQUATS"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            SessionPhase.SETUP_CHECK -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PanelText(text = setupText(uiState.setupProblem), color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "Cancel", color = Color.White)
                    }
                }
            }

            SessionPhase.COUNTDOWN -> {
                Text(
                    text = "${uiState.countdownSecondsLeft}",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Black
                )
            }

            SessionPhase.ACTIVE -> {
                Button(
                    onClick = onStop,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "STOP", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            SessionPhase.PAUSED -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PanelText(
                        text = "Paused: " + setupText(uiState.setupProblem),
                        color = Color(0xFFFCA5A5)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "STOP", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelText(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(Color(0xAA111827), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

private fun setupText(problem: SetupProblem): String = when (problem) {
    SetupProblem.NONE -> "Looks good. Hold your position."
    SetupProblem.BODY_NOT_VISIBLE -> "Step back: show your whole body"
    SetupProblem.FEED_LOST -> "Camera feed lost or frozen"
    SetupProblem.PHONE_MOVING -> "Put the phone down and keep it still"
}

private fun summaryText(uiState: SquatUiState): String {
    val reason = when (uiState.sessionEndReason) {
        SessionEndReason.USER_STOP -> "Session complete"
        SessionEndReason.PAUSE_TIMEOUT -> "Session ended: paused too long"
        SessionEndReason.INTERRUPTED -> "Session interrupted"
        null -> "Session ended"
    }
    return "$reason\n${uiState.repCount} reps, +${formatSeconds(uiState.sessionEarnedSeconds)} earned"
}

private fun formatSeconds(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}