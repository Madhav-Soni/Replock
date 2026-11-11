package com.fitness.squatcounter.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitness.squatcounter.domain.ExerciseType

/** One place for every color in the app. */
object AppColors {
    val BgTop = Color(0xFF070B14)
    val BgBottom = Color(0xFF111A30)
    val Surface = Color(0xFF121A2B)
    val SurfaceHigh = Color(0xFF1B2540)
    val Outline = Color(0x22FFFFFF)

    val TextPrimary = Color(0xFFF8FAFC)
    val TextMuted = Color(0xFF94A3B8)

    val Indigo = Color(0xFF6366F1)
    val Cyan = Color(0xFF22D3EE)

    val Good = Color(0xFF34D399)
    val Warn = Color(0xFFFBBF24)
    val Bad = Color(0xFFF87171)
    val BadDeep = Color(0xFFB91C1C)
}

/** The app's signature gradient colors: indigo to cyan. */
val BrandGradient: List<Color> = listOf(AppColors.Indigo, AppColors.Cyan)

/** Full-screen dark gradient background. */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppColors.BgTop, AppColors.BgBottom))),
        content = content
    )
}

/** A rounded card with a soft outline. Add .clickable / .padding after it as needed. */
fun Modifier.surfaceCard(
    shape: Shape = RoundedCornerShape(20.dp),
    color: Color = AppColors.Surface
): Modifier = this
    .clip(shape)
    .background(color)
    .border(1.dp, AppColors.Outline, shape)

/** Large call-to-action button with a gradient fill. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color> = BrandGradient,
    height: Dp = 56.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(colors))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}

/** Quieter full-width button for secondary actions such as Cancel. */
@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.SurfaceHigh)
            .border(1.dp, AppColors.Outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = AppColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Small rounded label. Pass onClick to make it a button. */
@Composable
fun AppChip(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = AppColors.SurfaceHigh,
    textColor: Color = AppColors.TextPrimary,
    onClick: (() -> Unit)? = null
) {
    val base = modifier
        .clip(RoundedCornerShape(50))
        .background(background)
    Text(
        text = text,
        color = textColor,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(horizontal = 13.dp, vertical = 8.dp)
    )
}

/** Round icon-style button, used for Back. */
@Composable
fun CircleIconButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(AppColors.SurfaceHigh)
            .border(1.dp, AppColors.Outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = AppColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Message bubble drawn over the camera. */
@Composable
fun AppPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.92f))
            .padding(horizontal = 18.dp, vertical = 9.dp)
    )
}

/**
 * Circular progress ring that animates when the value changes.
 * @param progress 0.0 to 1.0
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 96.dp,
    stroke: Dp = 10.dp,
    colors: List<Color> = listOf(AppColors.Cyan, AppColors.Indigo, AppColors.Cyan),
    content: @Composable BoxScope.() -> Unit = {}
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900)
    )

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = Color.White.copy(alpha = 0.10f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            if (animated > 0.001f) {
                drawArc(
                    brush = Brush.sweepGradient(colors),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

/** Each exercise gets its own accent color. */
fun ExerciseType.accent(): Color = when (this) {
    ExerciseType.SQUATS -> Color(0xFF60A5FA)
    ExerciseType.PUSHUPS -> Color(0xFFF472B6)
    ExerciseType.PLANK -> Color(0xFF34D399)
    ExerciseType.LUNGES -> Color(0xFFFBBF24)
    ExerciseType.JUMPING_JACKS -> Color(0xFFA78BFA)
}

/** Emoji icon for each exercise (no icon library needed). */
fun ExerciseType.emoji(): String = when (this) {
    ExerciseType.SQUATS -> "\uD83E\uDDB5"
    ExerciseType.PUSHUPS -> "\uD83D\uDCAA"
    ExerciseType.PLANK -> "\uD83E\uDDD8"
    ExerciseType.LUNGES -> "\uD83C\uDFC3"
    ExerciseType.JUMPING_JACKS -> "\uD83E\uDD38"
}

/** "45s", "3m", "3m 20s". */
fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        minutes > 0 && seconds > 0 -> "${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}