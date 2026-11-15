package com.athar.app.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.athar.app.core.designsystem.theme.AtharColors

/**
 * Progress bar that eases to its value, with a light sweeping across the
 * filled part and a softly pulsing tip, so time visibly flows.
 */
@Composable
fun AnimatedGlassProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(AtharColors.Teal, AtharColors.Gold),
    height: Dp = 10.dp,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "progress",
    )
    val transition = rememberInfiniteTransition(label = "flow")
    val shine by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "shine")
    val pulse by transition.animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")

    Canvas(modifier.fillMaxWidth().height(height)) {
        val rtl = layoutDirection == LayoutDirection.Rtl
        val r = size.height / 2
        drawRoundRect(Color.White.copy(alpha = 0.10f), cornerRadius = CornerRadius(r, r))
        val w = size.width * animated
        if (w <= 0f) return@Canvas
        val left = if (rtl) size.width - w else 0f
        drawRoundRect(
            Brush.horizontalGradient(if (rtl) colors.reversed() else colors, startX = left, endX = left + w),
            topLeft = Offset(left, 0f),
            size = Size(w, size.height),
            cornerRadius = CornerRadius(r, r),
        )
        // Moving highlight inside the filled part.
        val band = w * 0.35f
        val pos = left - band + (w + band) * (if (rtl) 1f - shine else shine)
        drawRoundRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                startX = pos,
                endX = pos + band,
            ),
            topLeft = Offset(left, 0f),
            size = Size(w, size.height),
            cornerRadius = CornerRadius(r, r),
        )
        // Glowing tip.
        val tipX = if (rtl) left else left + w
        drawCircle(colors.last().copy(alpha = 0.35f * pulse), radius = r * 2.2f * pulse, center = Offset(tipX, r))
        drawCircle(Color.White, radius = r * 0.7f, center = Offset(tipX, r))
    }
}

/** Ring that fills toward the next event, with a breathing glow. */
@Composable
fun AnimatedRing(
    progress: Float,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(AtharColors.Violet, AtharColors.Gold, AtharColors.Teal),
    stroke: Dp = 10.dp,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(1000, easing = FastOutSlowInEasing), label = "ring")
    val transition = rememberInfiniteTransition(label = "ringGlow")
    val glow by transition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "glow")
    Canvas(modifier) {
        val px = stroke.toPx()
        val arc = Size(size.width - px, size.height - px)
        val tl = Offset(px / 2, px / 2)
        drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, tl, arc, style = Stroke(px))
        drawArc(
            Brush.sweepGradient(colors + colors.first()),
            -90f,
            360f * animated,
            false,
            tl,
            arc,
            style = Stroke(px, cap = StrokeCap.Round),
        )
        val angle = Math.toRadians((-90.0 + 360.0 * animated))
        val c = Offset(size.width / 2, size.height / 2)
        val rad = arc.width / 2
        val tip = Offset(c.x + (rad * Math.cos(angle)).toFloat(), c.y + (rad * Math.sin(angle)).toFloat())
        drawCircle(colors.last().copy(alpha = 0.45f * glow), radius = px * 1.3f * glow, center = tip)
        drawCircle(Color.White, radius = px * 0.45f, center = tip)
    }
}

/** Digits slide as they change (e.g. a countdown "01:23:45"). Digits stay left-to-right. */
@Composable
fun AnimatedCountdown(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Row(modifier) {
        text.forEach { ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    (slideInVertically { -it } + fadeIn(tween(250))) togetherWith (slideOutVertically { it } + fadeOut(tween(250)))
                },
                label = "digit",
            ) { c -> Text(c.toString(), style = style) }
        }
    }
    }
}
