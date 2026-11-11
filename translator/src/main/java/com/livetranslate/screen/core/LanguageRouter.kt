package com.livetranslate.screen.core

data class Route(val source: String, val target: String)

/**
 * يحدد اتجاه الترجمة لكل كتلة نصية بشكل مستقل:
 * - الافتراضي: (اللغة الأصلية ← العربية).
 * - إذا كانت الكتلة بالعربية أصلاً والاتجاه ثنائي: (العربية ← اللغة الثانوية).
 */
object LanguageRouter {

    const val AUTO = "auto"

    /** يحوّل رمز ML Kit (مثل "zh-Latn" أو "und") إلى رمز صالح أو null. */
    fun normalize(tag: String?): String? {
        if (tag.isNullOrBlank() || tag == "und") return null
        if (tag.endsWith("-Latn")) return null // نص مكتوب بحروف لاتينية (Romanized) — غير مدعوم للترجمة
        val base = tag.substringBefore('-').lowercase()
        return when (base) {
            "iw" -> "he"
            "in" -> "id"
            "fil" -> "tl"
            else -> base
        }
    }

    fun route(
        detected: String?,
        configuredSource: String,
        target: String,
        secondary: String,
        bidirectional: Boolean,
    ): Route? {
        val src = if (configuredSource != AUTO) configuredSource else normalize(detected) ?: return null
        val tgt = when {
            src != target -> target
            bidirectional && secondary != target -> secondary
            else -> return null // النص بلغة الهدف أصلاً
        }
        return if (src == tgt) null else Route(src, tgt)
    }
}
