package com.livetranslate.screen.core

/**
 * يكتشف نظام الكتابة في الصورة تلقائياً (ياباني/صيني/كوري/لاتيني/ديفاناغاري)
 * ليختار محرك OCR المناسب دون أن يحدده المستخدم.
 *
 * الاستراتيجية: محرك اليابانية يقرأ الكانا والكانجي واللاتينية معاً، فهو مرور أول جيد:
 * - وجود كانا ← يابانية.
 * - كانجي/هانزي بلا كانا ← صينية.
 * وإن لم يُحسم: لاتيني إذا تعرّفت عليه أداة اكتشاف اللغة، ثم الكورية، ثم الديفاناغاري.
 */
object ScriptDetector {

    data class Counts(val latin: Int, val kana: Int, val han: Int, val hangul: Int, val devanagari: Int)

    fun count(text: String): Counts {
        var latin = 0; var kana = 0; var han = 0; var hangul = 0; var dev = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            when (Character.UnicodeScript.of(cp)) {
                Character.UnicodeScript.LATIN -> latin++
                Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> kana++
                Character.UnicodeScript.HAN -> han++
                Character.UnicodeScript.HANGUL -> hangul++
                Character.UnicodeScript.DEVANAGARI -> dev++
                else -> Unit
            }
            i += Character.charCount(cp)
        }
        return Counts(latin, kana, han, hangul, dev)
    }

    /** حسم من نتيجة محرك اليابانية: JAPANESE أو CHINESE أو null (غير محسوم). */
    fun fromJapanesePass(text: String): OcrScript? {
        val c = count(text)
        return when {
            c.kana >= 2 -> OcrScript.JAPANESE
            c.han >= 4 && c.han > c.latin -> OcrScript.CHINESE
            else -> null
        }
    }

    /** هل نتيجة المحرك اللاتيني نص حقيقي (لغة معروفة) وليست قراءة خاطئة لخط آخر؟ */
    fun isLatinConfident(text: String, detectedLanguage: String?): Boolean =
        count(text).latin >= 4 && detectedLanguage != null && detectedLanguage !in setOf("ja", "zh", "ko", "hi")

    fun hasHangul(text: String): Boolean = count(text).hangul >= 2
    fun hasDevanagari(text: String): Boolean = count(text).devanagari >= 3

    /** لغة مؤكدة من نظام الكتابة (تساعد في النصوص القصيرة جداً داخل الفقاعات). */
    fun languageHint(script: OcrScript): String? = when (script) {
        OcrScript.JAPANESE -> "ja"
        OcrScript.KOREAN -> "ko"
        OcrScript.CHINESE -> "zh"
        else -> null
    }
}
