package com.athar.app.core.designsystem.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import com.athar.app.core.designsystem.glass.GlassStyle
import com.athar.app.core.designsystem.glass.LocalGlassStyle

private val AtharColorScheme = darkColorScheme(
    primary = AtharColors.Gold,
    onPrimary = AtharColors.Night,
    secondary = AtharColors.Teal,
    onSecondary = AtharColors.Night,
    tertiary = AtharColors.Violet,
    background = AtharColors.Night,
    onBackground = AtharColors.TextPrimary,
    surface = AtharColors.Night,
    onSurface = AtharColors.TextPrimary,
    onSurfaceVariant = AtharColors.TextSecondary,
    error = AtharColors.Rose,
)

/** Font for Quran verses, adhkar and Hijri dates (user-selectable). */
val LocalDecorativeFont = staticCompositionLocalOf { Amiri }

@Composable
fun AtharTheme(
    glassStyle: GlassStyle = GlassStyle(),
    fontFamily: FontFamily = Tajawal,
    decorativeFamily: FontFamily = Amiri,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AtharColorScheme,
        typography = atharTypography(fontFamily),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides AtharColors.TextPrimary,
            LocalGlassStyle provides glassStyle,
            LocalDecorativeFont provides decorativeFamily,
            content = content,
        )
    }
}
