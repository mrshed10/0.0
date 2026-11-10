package com.saudi.salariesapp.ui.theme

import androidx.compose.ui.graphics.Color

val SaudiGreen = Color(0xFF007A5E)
val SaudiGreenDark = Color(0xFF4FD8B0)
val SaudiGold = Color(0xFFFFD54F)
val LightBackground = Color(0xFFF8F9FA)
val DarkBackground = Color(0xFF101413)
val DarkSurface = Color(0xFF1B2120)

/** لوحة ألوان جاهزة لاختيار لون البطاقات والودجت. */
val PaletteHex = listOf(
    "#007A5E", "#00A896", "#1E88E5", "#3949AB", "#8E24AA", "#C2185B",
    "#D84315", "#F4511E", "#FFB300", "#FFD54F", "#6D4C41", "#455A64",
    "#212121", "#FFFFFF",
)

fun parseHex(hex: String, fallback: Color = SaudiGreen): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
