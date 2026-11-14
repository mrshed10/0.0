package com.athar.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.athar.app.R

/** Modern Arabic/Latin UI font. */
val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
)

/** Classical Naskh font for Quran verses, adhkar and Hijri dates. */
val Amiri = FontFamily(Font(R.font.amiri_regular, FontWeight.Normal))

private fun TextStyle.withFamily(family: FontFamily) = copy(fontFamily = family)

/** Material type scale re-skinned with [family] (swappable later from font settings). */
fun atharTypography(family: FontFamily = Tajawal): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.withFamily(family),
        displayMedium = base.displayMedium.withFamily(family),
        displaySmall = base.displaySmall.withFamily(family),
        headlineLarge = base.headlineLarge.withFamily(family),
        headlineMedium = base.headlineMedium.withFamily(family),
        headlineSmall = base.headlineSmall.withFamily(family),
        titleLarge = base.titleLarge.withFamily(family),
        titleMedium = base.titleMedium.withFamily(family),
        titleSmall = base.titleSmall.withFamily(family),
        bodyLarge = base.bodyLarge.withFamily(family),
        bodyMedium = base.bodyMedium.withFamily(family),
        bodySmall = base.bodySmall.withFamily(family),
        labelLarge = base.labelLarge.withFamily(family),
        labelMedium = base.labelMedium.withFamily(family),
        labelSmall = base.labelSmall.withFamily(family),
    )
}
