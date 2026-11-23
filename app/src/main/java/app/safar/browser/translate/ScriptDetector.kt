package app.safar.browser.translate

/**
 * تخمين لغة النص من نظام الكتابة (سريع ولا يحتاج نموذجاً). يُستخدم:
 *  - كبديل عندما يفشل ML Kit Language ID (نص قصير أو مخرجات OCR مشوشة)
 *  - لاختيار معرّف OCR المناسب في وضع المانغا التلقائي
 */
object ScriptDetector {
    private val hangul = Regex("""[\p{IsHangul}]""")
    private val kana = Regex("""[\p{IsHiragana}\p{IsKatakana}]""")
    private val han = Regex("""[\p{IsHan}]""")
    private val arabic = Regex("""[\p{IsArabic}]""")
    private val cyrillic = Regex("""[\p{IsCyrillic}]""")
    private val hebrew = Regex("""[\p{IsHebrew}]""")
    private val thai = Regex("""[\p{IsThai}]""")
    private val devanagari = Regex("""[\p{IsDevanagari}]""")
    private val latin = Regex("""[\p{IsLatin}]""")

    /** يعيد رمز لغة ISO أو null إن لم يوجد نص كافٍ. */
    fun guess(text: String): String? {
        val counts = linkedMapOf(
            "ko" to hangul.findAll(text).count(),
            "ja" to kana.findAll(text).count(),
            "zh" to han.findAll(text).count(),
            "ar" to arabic.findAll(text).count(),
            "ru" to cyrillic.findAll(text).count(),
            "he" to hebrew.findAll(text).count(),
            "th" to thai.findAll(text).count(),
            "hi" to devanagari.findAll(text).count(),
            "en" to latin.findAll(text).count(),
        )
        // خطوط الرسم قد تُقرأ ككانا مفردة (ノ ー ツ) ⇒ نشترط عدداً ونسبة معقولة قبل الحكم باليابانية
        val kanaN = counts.getValue("ja"); val hanN = counts.getValue("zh"); val latinN = counts.getValue("en")
        if (kanaN >= 3 && kanaN + hanN >= 0.3 * latinN && kanaN + hanN >= counts.getValue("ko")) return "ja"
        counts["ja"] = 0
        if (hanN < 3 || hanN < 0.3 * latinN) counts["zh"] = 0
        if (counts.getValue("ko") < 3 || counts.getValue("ko") < 0.3 * latinN) counts["ko"] = 0
        val best = counts.maxByOrNull { it.value } ?: return null
        return if (best.value >= 2) best.key else null
    }

    /** درجة تطابق النص مع لغة متوقعة (لمقارنة نتائج معرّفات OCR المختلفة). */
    fun nativeScore(text: String, script: String): Int = when (script) {
        "ko" -> hangul.findAll(text).count() * 3
        "ja" -> kana.findAll(text).count() * 3 + han.findAll(text).count() * 2
        "zh" -> han.findAll(text).count() * 3
        else -> 0
    } + latin.findAll(text).count()
}
