package com.athar.app.widget

import androidx.annotation.LayoutRes
import com.athar.app.R

/**
 * Live clocks use the launcher's TextClock, which can only take fonts bundled
 * in the app (declared in XML), not imported font files.
 */
object ClockLayouts {
    private val digital = mapOf(
        "tajawal" to R.layout.widget_digital_tajawal,
        "amiri" to R.layout.widget_digital_amiri,
        "scheherazade" to R.layout.widget_digital_scheherazade,
        "arefruqaa" to R.layout.widget_digital_arefruqaa,
        "lalezar" to R.layout.widget_digital_lalezar,
        "rakkas" to R.layout.widget_digital_rakkas,
        "poppins" to R.layout.widget_digital_poppins,
    )

    @LayoutRes
    fun digital(fontId: String?): Int = digital[fontId] ?: R.layout.widget_digital_system

    fun supportsFont(fontId: String?): Boolean = fontId == null || fontId in digital
}
