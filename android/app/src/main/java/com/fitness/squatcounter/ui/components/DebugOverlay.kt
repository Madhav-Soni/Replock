package com.fitness.squatcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitness.squatcounter.domain.RejectionReason
import com.fitness.squatcounter.domain.SquatState
import com.fitness.squatcounter.ui.SquatUiState
import java.util.Locale

/**
 * Heads-up display showing:
 * - Reps counter
 * - State machine status (STANDING, DESCENDING, BOTTOM, ASCENDING)
 * - Full body visibility indicator
 * - Metrics: FPS, smoothed knee angle, smoothed hip angle
 * - Last rejection reason
 */
@Composable
fun DebugOverlay(
    uiState: SquatUiState,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        // TOP SECTION: Big Rep Count, State Badge, Visibility Alert
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Full-body Tracking Banner
            if (!uiState.isFullBodyVisible) {
                Box(
                    modifier = Modifier
                        .background(
                            Color(0xDDCC0000),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "⚠️ STEP BACK: FULL BODY REQUIRED",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Big Reps Card
            Box(
                modifier = Modifier
                    .background(
                        Color(0xAA111827),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "REPS",
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "${uiState.repCount}",
                            color = Color.White,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Current State Badge
                        val (stateBg, stateText) = when (uiState.currentState) {
                            SquatState.STANDING -> Pair(Color(0xFF1E3A8A), "STANDING")
                            SquatState.DESCENDING -> Pair(Color(0xFFD97706), "DESCENDING")
                            SquatState.BOTTOM -> Pair(Color(0xFF059669), "BOTTOM (GOOD DEPTH)")
                            SquatState.ASCENDING -> Pair(Color(0xFF7C3AED), "ASCENDING")
                        }

                        Box(
                            modifier = Modifier
                                .background(stateBg, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stateText,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Screen-time Credit Balance Badge
                        val minutes = uiState.creditBalanceSeconds / 60
                        val seconds = uiState.creditBalanceSeconds % 60
                        val formattedTime = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"

                        Box(
                            modifier = Modifier
                                .background(Color(0xFF065F46), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "BALANCE: $formattedTime",
                                color = Color(0xFF6EE7B7),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // BOTTOM SECTION: Debug stats (FPS, angles, rejection reason, reset button)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Color(0xDD111827),
                    RoundedCornerShape(16.dp)
                )
                .padding(14.dp)
        ) {
            // Stats Row: FPS, Knee, Hip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                DebugMetric(label = "FPS", value = "${uiState.fps}")
                DebugMetric(
                    label = "KNEE ANGLE",
                    value = String.format(Locale.US, "%.1f°", uiState.kneeAngle)
                )
                DebugMetric(
                    label = "HIP ANGLE",
                    value = String.format(Locale.US, "%.1f°", uiState.hipAngle)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Last Rejection Reason
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LAST REJECTION",
                        color = Color(0xFF9CA3AF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (uiState.lastRejectionReason == RejectionReason.NONE) {
                            "None"
                        } else {
                            uiState.lastRejectionReason.displayName
                        },
                        color = if (uiState.lastRejectionReason == RejectionReason.NONE) {
                            Color(0xFF10B981)
                        } else {
                            Color(0xFFEF4444)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Reset", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DebugMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color(0xFF9CA3AF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
