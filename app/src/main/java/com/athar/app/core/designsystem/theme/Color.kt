package com.athar.app.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/** Night palette with soft aurora accents; glass surfaces sit on top of it. */
object AtharColors {
    val Night = Color(0xFF0B1026)
    val NightDeep = Color(0xFF05070F)
    val Indigo = Color(0xFF3A3F9E)
    val Teal = Color(0xFF2EC4B6)
    val Violet = Color(0xFF8B7CF6)
    val Rose = Color(0xFFF28AB2)
    val Gold = Color(0xFFE6C27A)

    val TextPrimary = Color(0xF2FFFFFF)
    val TextSecondary = Color(0xB3FFFFFF)
    val TextTertiary = Color(0x80FFFFFF)
}

/** Accent tones a user can assign to any item (counter, card, widget). */
enum class AccentTone(val color: Color) {
    Gold(AtharColors.Gold),
    Teal(AtharColors.Teal),
    Violet(AtharColors.Violet),
    Rose(AtharColors.Rose),
}
