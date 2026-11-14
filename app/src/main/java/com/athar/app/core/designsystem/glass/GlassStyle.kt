package com.athar.app.core.designsystem.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/**
 * User-tunable glass parameters. The same model will drive the widget editor
 * (transparency 0–100%, tint, blur), so every surface reads from here.
 */
@Immutable
data class GlassStyle(
    /** Surface tint alpha: 0f = fully transparent, 1f = opaque. */
    val opacity: Float = 0.12f,
    val blurRadius: Dp = 28.dp,
    val tint: Color = Color.White,
    val borderOpacity: Float = 0.22f,
    val noise: Float = 0.06f,
    val cornerRadius: Dp = 28.dp,
)

val LocalGlassStyle = staticCompositionLocalOf { GlassStyle() }

/** The blur source (the aurora background) that every glass surface samples. */
val LocalHazeState = staticCompositionLocalOf<HazeState> {
    error("LocalHazeState not provided: wrap content in a HazeState provider")
}
