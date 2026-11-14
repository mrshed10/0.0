package com.athar.app.core.format

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/** Money stored as minor units (1 riyal = 100 halalas). */
object Money {
    private val amountPattern = Regex("""^\d+(\.\d{1,2})?$""")

    /**
     * Parses user input such as "1500", "1,500.5", "١٥٠٠٫٧٥" into minor units.
     * Accepts Arabic-Indic and Persian digits; "," and "٬" are thousands
     * separators, "." and "٫" decimal points. Returns null if invalid.
     */
    fun parse(input: String): Long? {
        val normalized = buildString {
            for (c in input.trim()) {
                when (c) {
                    in '٠'..'٩' -> append('0' + (c - '٠'))
                    in '۰'..'۹' -> append('0' + (c - '۰'))
                    '٫' -> append('.')
                    ',', '٬', ' ', ' ' -> Unit
                    else -> append(c)
                }
            }
        }
        if (!amountPattern.matches(normalized)) return null
        val parts = normalized.split('.')
        val whole = parts[0].toLongOrNull() ?: return null
        val fraction = parts.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0L
        if (whole > Long.MAX_VALUE / 100 - 1) return null
        return whole * 100 + fraction
    }

    /** "1,250" or "1,250.50" (absolute value; callers show the sign/direction). */
    fun format(minor: Long): String {
        val value = abs(minor)
        val whole = NumberFormat.getIntegerInstance(Locale.ENGLISH).format(value / 100)
        val fraction = value % 100
        return if (fraction == 0L) whole else "$whole.${fraction.toString().padStart(2, '0')}"
    }

    /** Value to pre-fill an edit field: "1250" or "1250.5". */
    fun toInput(minor: Long): String {
        val value = abs(minor)
        val fraction = value % 100
        return if (fraction == 0L) "${value / 100}" else "${value / 100}.${fraction.toString().padStart(2, '0').trimEnd('0')}"
    }
}
