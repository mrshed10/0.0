package com.livetranslate.overlay.translate

/**
 * نظام الكتابة. الخمسة الأولى يقرؤها ML Kit (مضمّن وسريع)، والبقية يقرؤها Tesseract
 * بحزمة تُنزَّل مرة واحدة لكل لغة (ML Kit لا يدعم السيريلية والعربية والعبرية واليونانية والتايلاندية…).
 */
enum class Script {
    LATIN, KOREAN, JAPANESE, CHINESE, DEVANAGARI,
    CYRILLIC, ARABIC, HEBREW, GREEK, THAI, BENGALI, TAMIL, TELUGU, KANNADA, GUJARATI, GEORGIAN;

    val readByMlKit get() = ordinal <= DEVANAGARI.ordinal
}

/** @param tess اسم حزمة Tesseract للغات التي لا يقرؤها ML Kit. */
data class Language(val code: String, val nameAr: String, val script: Script = Script.LATIN, val tess: String? = null)

object Languages {
    const val AUTO = "auto"

    /** كل لغات الترجمة على الجهاز في ML Kit (59 لغة)، مع نظام كتابتها ومحرك قراءتها. */
    val all = listOf(
        Language("ar", "العربية", Script.ARABIC, "ara"),
        Language("en", "الإنجليزية"),
        Language("fr", "الفرنسية"),
        Language("es", "الإسبانية"),
        Language("de", "الألمانية"),
        Language("tr", "التركية"),
        Language("ru", "الروسية", Script.CYRILLIC, "rus"),
        Language("uk", "الأوكرانية", Script.CYRILLIC, "ukr"),
        Language("be", "البيلاروسية", Script.CYRILLIC, "bel"),
        Language("bg", "البلغارية", Script.CYRILLIC, "bul"),
        Language("mk", "المقدونية", Script.CYRILLIC, "mkd"),
        Language("fa", "الفارسية", Script.ARABIC, "fas"),
        Language("ur", "الأردية", Script.ARABIC, "urd"),
        Language("he", "العبرية", Script.HEBREW, "heb"),
        Language("el", "اليونانية", Script.GREEK, "ell"),
        Language("th", "التايلاندية", Script.THAI, "tha"),
        Language("ja", "اليابانية", Script.JAPANESE),
        Language("zh", "الصينية", Script.CHINESE),
        Language("ko", "الكورية", Script.KOREAN),
        Language("hi", "الهندية", Script.DEVANAGARI),
        Language("mr", "الماراثية", Script.DEVANAGARI),
        Language("bn", "البنغالية", Script.BENGALI, "ben"),
        Language("ta", "التاميلية", Script.TAMIL, "tam"),
        Language("te", "التيلوغوية", Script.TELUGU, "tel"),
        Language("kn", "الكانادية", Script.KANNADA, "kan"),
        Language("gu", "الغوجاراتية", Script.GUJARATI, "guj"),
        Language("ka", "الجورجية", Script.GEORGIAN, "kat"),
        Language("id", "الإندونيسية"),
        Language("ms", "الملايوية"),
        Language("vi", "الفيتنامية"),
        Language("tl", "الفلبينية"),
        Language("it", "الإيطالية"),
        Language("pt", "البرتغالية"),
        Language("nl", "الهولندية"),
        Language("pl", "البولندية"),
        Language("cs", "التشيكية"),
        Language("sk", "السلوفاكية"),
        Language("sl", "السلوفينية"),
        Language("hr", "الكرواتية"),
        Language("ro", "الرومانية"),
        Language("hu", "المجرية"),
        Language("sv", "السويدية"),
        Language("no", "النرويجية"),
        Language("da", "الدنماركية"),
        Language("fi", "الفنلندية"),
        Language("is", "الآيسلندية"),
        Language("et", "الإستونية"),
        Language("lv", "اللاتفية"),
        Language("lt", "الليتوانية"),
        Language("sq", "الألبانية"),
        Language("ca", "الكتالونية"),
        Language("gl", "الجاليكية"),
        Language("ga", "الأيرلندية"),
        Language("cy", "الويلزية"),
        Language("mt", "المالطية"),
        Language("af", "الأفريقانية"),
        Language("sw", "السواحيلية"),
        Language("ht", "الكريولية الهايتية"),
        Language("eo", "الإسبرانتو"),
    )

    val sources = listOf(Language(AUTO, "تلقائي (كل اللغات)")) + all
    val targets = all

    private val byCode = all.associateBy { it.code }

    fun nameOf(code: String): String = if (code == AUTO) sources[0].nameAr else byCode[code]?.nameAr ?: code

    fun scriptOf(code: String): Script = byCode[code]?.script ?: Script.LATIN

    /** حزمة Tesseract للغة (null إن كان ML Kit يقرؤها). */
    fun tessOf(code: String): String? = byCode[code]?.tess

    /** لغات لا تفصل الكلمات بمسافات؛ تُضم أسطرها بلا فاصل وتُقسم ترجمتها حرفاً حرفاً. */
    fun joinsWithoutSpaces(script: Script) = script == Script.JAPANESE || script == Script.CHINESE || script == Script.THAI

    /** حزم ترجمة تُنزَّل مسبقاً في الوضع التلقائي حتى تُترجم هذه اللغات فوراً عند ظهورها. */
    val autoPreload = listOf("ja", "zh", "ko", "ru")

    /** حزم قراءة (Tesseract) تُنزَّل مسبقاً في الوضع التلقائي. */
    val autoOcrPreload = listOf("ru")

    /** حزم الترجمة اللازمة لهذا الاختيار (الإنجليزية مضمّنة دائماً). */
    fun modelsFor(source: String, target: String): List<String> =
        (listOf(target) + if (source == AUTO) autoPreload else listOf(source))
            .distinct()
            .filter { it != "en" && it != AUTO }

    /** حزم القراءة (Tesseract) اللازمة لهذا الاختيار. */
    fun ocrPacksFor(source: String): List<String> =
        (if (source == AUTO) autoOcrPreload else listOf(source)).mapNotNull { tessOf(it) }

    /** اللغة المؤكدة من نظام الكتابة وحده، أو null إن كان النظام مشتركاً بين عدة لغات. */
    fun languageOfScript(script: Script): String? =
        all.filter { it.script == script }.singleOrNull()?.code ?: when (script) {
            Script.DEVANAGARI -> "hi"
            else -> null
        }

    /** كل اللغات المكتوبة بنظام الكتابة هذا (لحصر كشف اللغة بينها). */
    fun languagesOfScript(script: Script): List<String> = all.filter { it.script == script }.map { it.code }
}

/** فحوص خفيفة على النص قبل إرساله للترجمة. */
object ScriptFilter {
    private fun Char.isHangul() = this in '가'..'힯' || this in 'ᄀ'..'ᇿ' || this in '㄰'..'㆏'
    private fun Char.isKana() = this in '぀'..'ヿ'
    private fun Char.isHan() = this in '一'..'鿿' || this in '㐀'..'䶿'
    private fun Char.isLatinLetter() = this in 'A'..'Z' || this in 'a'..'z' || this in 'À'..'ɏ' ||
        this in 'Ḁ'..'ỿ'

    /** نظام كتابة الحرف (الحروف الصينية تُعد CHINESE والكانا JAPANESE). */
    fun scriptOfChar(c: Char): Script? = when {
        c.isLatinLetter() -> Script.LATIN
        c.isHangul() -> Script.KOREAN
        c.isKana() -> Script.JAPANESE
        c.isHan() -> Script.CHINESE
        c in 'ऀ'..'ॿ' -> Script.DEVANAGARI
        c in 'Ѐ'..'ӿ' || c in 'Ԁ'..'ԯ' -> Script.CYRILLIC
        c in '؀'..'ۿ' || c in 'ݐ'..'ݿ' || c in 'ﭐ'..'﷿' || c in 'ﹰ'..'﻿' -> Script.ARABIC
        c in '֐'..'׿' -> Script.HEBREW
        c in 'Ͱ'..'Ͽ' || c in 'ἀ'..'῿' -> Script.GREEK
        c in '฀'..'๿' -> Script.THAI
        c in 'ঀ'..'৿' -> Script.BENGALI
        c in '஀'..'௿' -> Script.TAMIL
        c in 'ఀ'..'౿' -> Script.TELUGU
        c in 'ಀ'..'೿' -> Script.KANNADA
        c in '઀'..'૿' -> Script.GUJARATI
        c in 'Ⴀ'..'ჿ' -> Script.GEORGIAN
        else -> null
    }

    /**
     * نظام الكتابة الغالب في النص:
     * أي حرف كانا ⇒ يابانية؛ هانغول غالب ⇒ كورية؛ حروف صينية بلا كانا ⇒ صينية؛
     * وإلا النظام الأكثر حروفاً (غير اللاتيني يفوز عند التساوي). null إن لم توجد حروف.
     */
    fun dominantScript(text: String): Script? {
        val counts = IntArray(Script.entries.size)
        for (c in text) scriptOfChar(c)?.let { counts[it.ordinal]++ }
        fun n(s: Script) = counts[s.ordinal]
        val latin = n(Script.LATIN)
        return when {
            n(Script.KOREAN) > 0 && n(Script.KOREAN) >= n(Script.JAPANESE) + n(Script.CHINESE) -> Script.KOREAN
            n(Script.JAPANESE) > 0 -> Script.JAPANESE
            n(Script.CHINESE) > 0 && n(Script.CHINESE) * 2 >= latin -> Script.CHINESE
            else -> {
                val other = Script.entries
                    .filter { it != Script.LATIN && n(it) > 0 }
                    .maxByOrNull { n(it) }
                when {
                    other != null && n(other) >= latin -> other
                    latin > 0 -> Script.LATIN
                    other != null -> other
                    else -> null
                }
            }
        }
    }

    /** عدد الحروف "الأصلية" غير اللاتينية. */
    fun nativeCount(text: String) = text.count { val s = scriptOfChar(it); s != null && s != Script.LATIN }

    /** هل يحتوي النص على حروف من نظام الكتابة المطلوب؟ (اليابانية تشمل الكانجي) */
    fun containsScript(text: String, script: Script): Boolean = text.any {
        val s = scriptOfChar(it)
        s == script || (script == Script.JAPANESE && s == Script.CHINESE)
    }

    /** يستبعد الأرقام والرموز والروابط وأسماء المستخدمين المنفردة. */
    fun isWorthTranslating(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val words = trimmed.split(Regex("\\s+"))
        if (words.size == 1) {
            val w = words[0]
            if (w.startsWith("@") || w.startsWith("#") || w.contains("://") || w.startsWith("www.")) return false
        }
        return trimmed.count { it.isLetter() } >= 2
    }
}
