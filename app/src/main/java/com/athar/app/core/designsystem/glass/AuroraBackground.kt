package com.athar.app.core.designsystem.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.athar.app.core.designsystem.theme.AtharColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/**
 * Slowly drifting colour blobs behind the UI. This is the haze *source*:
 * glass cards blur whatever this draws. Later it can be swapped for a
 * user image or video background.
 */
@Composable
fun AuroraBackground(hazeState: HazeState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )

    Canvas(modifier.fillMaxSize().haze(hazeState)) {
        val t = drift.value // read in the draw phase: redraws without recomposing
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(AtharColors.Night, AtharColors.NightDeep)))
        blob(Offset(w * (0.10f + 0.30f * t), h * (0.10f + 0.06f * t)), w * 0.80f, AtharColors.Indigo)
        blob(Offset(w * (0.95f - 0.30f * t), h * (0.36f + 0.10f * t)), w * 0.65f, AtharColors.Teal.copy(alpha = 0.50f))
        blob(Offset(w * (0.25f + 0.25f * t), h * (0.72f - 0.10f * t)), w * 0.75f, AtharColors.Violet.copy(alpha = 0.50f))
        blob(Offset(w * 0.90f, h * (0.95f - 0.06f * t)), w * 0.50f, AtharColors.Gold.copy(alpha = 0.22f))
    }
}

private fun DrawScope.blob(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
        radius = radius,
        center = center,
    )
}
