package com.fitness.squatcounter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.fitness.squatcounter.domain.LandmarkPoint

/**
 * Draws the human skeleton overlay on top of the live camera preview.
 * Adapts color based on whether full-body tracking is active.
 */
@Composable
fun SkeletonOverlay(
    landmarks: List<LandmarkPoint>,
    isFullBodyVisible: Boolean,
    modifier: Modifier = Modifier
) {
    if (landmarks.isEmpty()) return

    val boneColor = if (isFullBodyVisible) Color(0xFF00E676) else Color(0xFFFF5252)
    val jointColor = if (isFullBodyVisible) Color(0xFF00B0FF) else Color(0xFFFFD600)

    val connections = listOf(
        // Torso & Shoulders
        Pair(11, 12), // left shoulder to right shoulder
        Pair(11, 23), // left shoulder to left hip
        Pair(12, 24), // right shoulder to right hip
        Pair(23, 24), // left hip to right hip

        // Left Leg
        Pair(23, 25), // left hip to left knee
        Pair(25, 27), // left knee to left ankle

        // Right Leg
        Pair(24, 26), // right hip to right knee
        Pair(26, 28), // right knee to right ankle

        // Arms (optional visual aid)
        Pair(11, 13), // left shoulder to left elbow
        Pair(13, 15), // left elbow to left wrist
        Pair(12, 14), // right shoulder to right elbow
        Pair(14, 16)  // right elbow to right wrist
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 1. Draw bones (connecting lines)
        for ((startIdx, endIdx) in connections) {
            if (startIdx < landmarks.size && endIdx < landmarks.size) {
                val start = landmarks[startIdx]
                val end = landmarks[endIdx]

                if (start.visibility >= 0.4f && end.visibility >= 0.4f) {
                    drawLine(
                        color = boneColor,
                        start = Offset(start.x * width, start.y * height),
                        end = Offset(end.x * width, end.y * height),
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // 2. Draw joints (circles)
        val keyJointIndices = listOf(11, 12, 23, 24, 25, 26, 27, 28)
        for (idx in keyJointIndices) {
            if (idx < landmarks.size) {
                val point = landmarks[idx]
                if (point.visibility >= 0.4f) {
                    drawCircle(
                        color = jointColor,
                        radius = 10f,
                        center = Offset(point.x * width, point.y * height)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4f,
                        center = Offset(point.x * width, point.y * height)
                    )
                }
            }
        }
    }
}
