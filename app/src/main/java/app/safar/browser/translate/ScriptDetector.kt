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
        // الكانجي (Han) مشترك؛ وجود أي كانا يعني اليابانية
        if (counts.getValue("ja") > 0 && counts.getValue("ja") + counts.getValue("zh") >= counts.getValue("ko")) return "ja"
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
