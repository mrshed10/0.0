package com.saudi.salariesapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val LightColors = lightColorScheme(
    primary = SaudiGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2F1),
    onPrimaryContainer = SaudiGreen,
    secondary = Color(0xFF00A896),
    tertiary = SaudiGold,
    background = LightBackground,
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = SaudiGreenDark,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0E3B31),
    onPrimaryContainer = SaudiGreenDark,
    secondary = Color(0xFF4DD0C0),
    tertiary = SaudiGold,
    background = DarkBackground,
    surface = DarkSurface,
)

/**
 * ثيم التطبيق: فاتح/داكن (حسب النظام أو اختيار المستخدم) مع فرض اتجاه RTL للعربية.
 * themeMode: 0 = النظام، 1 = فاتح، 2 = داكن
 */
@Composable
fun SalariesTheme(themeMode: Int = 0, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = Typography()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
