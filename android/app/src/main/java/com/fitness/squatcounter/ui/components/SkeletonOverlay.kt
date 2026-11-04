package com.fitness.squatcounter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.fitness.squatcounter.domain.LandmarkPoint

/** Width / height of the portrait camera frame (4:3 sensor, rotated to portrait). */
private const val FRAME_ASPECT = 3f / 4f

/** Minimum visibility for a joint or bone to be drawn. */
private const val MIN_VISIBILITY = 0.3f

/** Body bones as pairs of MediaPipe pose landmark indices (face and fingers are skipped). */
private val BONES = listOf(
    11 to 12, // shoulders
    11 to 13, 13 to 15, // left arm
    12 to 14, 14 to 16, // right arm
    11 to 23, 12 to 24, 23 to 24, // torso
    23 to 25, 25 to 27, 27 to 29, 29 to 31, 27 to 31, // left leg and foot
    24 to 26, 26 to 28, 28 to 30, 30 to 32, 28 to 32 // right leg and foot
)

private val BODY_JOINTS = listOf(11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32)

/**
 * Draws the tracked skeleton over the camera preview.
 * The preview is letterboxed (FIT_CENTER), so landmarks are mapped into the same centred
 * rectangle. Green = full body tracked, orange = body not fully visible.
 */
@Composable
fun SkeletonOverlay(
    landmarks: List<LandmarkPoint>,
    isFullBodyVisible: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (landmarks.size < 33) return@Canvas

        // The rectangle the camera image actually occupies inside this canvas.
        val canvasAspect = size.width / size.height
        val drawWidth: Float
        val drawHeight: Float
        if (canvasAspect > FRAME_ASPECT) {
            drawHeight = size.height
            drawWidth = drawHeight * FRAME_ASPECT
        } else {
            drawWidth = size.width
            drawHeight = drawWidth / FRAME_ASPECT
        }
        val offsetX = (size.width - drawWidth) / 2f
        val offsetY = (size.height - drawHeight) / 2f

        fun toCanvas(point: LandmarkPoint) = Offset(
            x = offsetX + point.x * drawWidth,
            y = offsetY + point.y * drawHeight
        )

        val boneColor = if (isFullBodyVisible) Color(0xFF10B981) else Color(0xFFF59E0B)
        val jointColor = Color.White

        for ((a, b) in BONES) {
            val pa = landmarks[a]
            val pb = landmarks[b]
            if (pa.visibility < MIN_VISIBILITY || pb.visibility < MIN_VISIBILITY) continue
            drawLine(
                color = boneColor,
                start = toCanvas(pa),
                end = toCanvas(pb),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }

        for (index in BODY_JOINTS) {
            val point = landmarks[index]
            if (point.visibility < MIN_VISIBILITY) continue
            drawCircle(color = jointColor, radius = 7f, center = toCanvas(point))
        }
    }
}