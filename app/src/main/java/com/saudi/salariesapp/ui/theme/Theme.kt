package com.saudi.salariesapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

val SaudiGreen = Color(0xFF007A5E)
val SaudiGreenLight = Color(0xFF4FB596)
val Gold = Color(0xFFFFD54F)

private val LightColors = lightColorScheme(
    primary = SaudiGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2F1),
    onPrimaryContainer = SaudiGreen,
    secondary = Color(0xFF00A896),
    tertiary = Gold,
    tertiaryContainer = Color(0xFFFFE082),
    onTertiaryContainer = Color(0xFF3E2C00),
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F2F4),
)

private val DarkColors = darkColorScheme(
    primary = SaudiGreenLight,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0B3D31),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFF4DD0C0),
    tertiary = Gold,
    tertiaryContainer = Color(0xFF5C4A12),
    onTertiaryContainer = Color(0xFFFFE9A8),
    background = Color(0xFF101413),
    surface = Color(0xFF1A1F1E),
    surfaceVariant = Color(0xFF252B2A),
)

/**
 * App theme with a light & dark scheme. Layout is forced right-to-left so the UI is
 * Arabic-first regardless of the device language.
 *
 * @param darkOverride null = follow the system setting.
 */
@Composable
fun SalariesTheme(darkOverride: Boolean? = null, content: @Composable () -> Unit) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
