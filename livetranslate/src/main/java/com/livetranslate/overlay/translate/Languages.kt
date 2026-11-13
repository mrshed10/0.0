package com.livetranslate.overlay.translate

/** نظام الكتابة الذي يحدد أي نموذج OCR يُستخدم. */
enum class Script { LATIN, KOREAN, JAPANESE, CHINESE, DEVANAGARI }

data class Language(val code: String, val nameAr: String)

object Languages {
    const val AUTO = "auto"

    /** اللغات التي يمكن قراءتها من الشاشة (حدود ML Kit Text Recognition v2). */
    val sources = listOf(
        Language(AUTO, "تلقائي (كل اللغات)"),
        Language("en", "الإنجليزية"),
        Language("ko", "الكورية"),
        Language("ja", "اليابانية"),
        Language("zh", "الصينية"),
        Language("hi", "الهندية"),
        Language("fr", "الفرنسية"),
        Language("es", "الإسبانية"),
        Language("de", "الألمانية"),
        Language("it", "الإيطالية"),
        Language("pt", "البرتغالية"),
        Language("tr", "التركية"),
        Language("id", "الإندونيسية"),
        Language("nl", "الهولندية"),
        Language("ms", "الملايوية"),
    )

    /** اللغات التي يمكن الترجمة إليها (ML Kit Translation). */
    val targets = listOf(
        Language("ar", "العربية"),
        Language("en", "الإنجليزية"),
        Language("fr", "الفرنسية"),
        Language("es", "الإسبانية"),
        Language("de", "الألمانية"),
        Language("tr", "التركية"),
        Language("ur", "الأردية"),
        Language("fa", "الفارسية"),
        Language("hi", "الهندية"),
        Language("id", "الإندونيسية"),
        Language("ms", "الملايوية"),
        Language("ru", "الروسية"),
        Language("ko", "الكورية"),
        Language("ja", "اليابانية"),
        Language("zh", "الصينية"),
        Language("it", "الإيطالية"),
        Language("pt", "البرتغالية"),
    )

    private val all = (sources + targets).associateBy { it.code }

    fun nameOf(code: String): String = all[code]?.nameAr ?: code

    fun scriptOf(source: String): Script = when (source) {
        "ko" -> Script.KOREAN
        "ja" -> Script.JAPANESE
        "zh" -> Script.CHINESE
        "hi" -> Script.DEVANAGARI
        else -> Script.LATIN
    }

    /** اللغات التي لا تفصل الكلمات بمسافات؛ تُضم أسطرها بلا فاصل. */
    fun joinsWithoutSpaces(script: Script) = script == Script.JAPANESE || script == Script.CHINESE

    /** حزم تُنزَّل مسبقاً في الوضع التلقائي حتى تُترجم هذه اللغات فوراً عند ظهورها. */
    val autoPreload = listOf("ja", "zh", "ko")

    /** حزم الترجمة اللازمة لهذا الاختيار (الإنجليزية مضمّنة دائماً). */
    fun modelsFor(source: String, target: String): List<String> =
        (listOf(target) + if (source == AUTO) autoPreload else listOf(source))
            .distinct()
            .filter { it != "en" && it != AUTO }

    /** اللغة المؤكدة من نظام الكتابة وحده (الكورية والهندية واليابانية بالكانا)، أو null للحروف اللاتينية/الصينية. */
    fun languageOfScript(script: Script): String? = when (script) {
        Script.KOREAN -> "ko"
        Script.JAPANESE -> "ja"
        Script.DEVANAGARI -> "hi"
        Script.CHINESE, Script.LATIN -> null
    }
}

/** فحوص خفيفة على النص قبل إرساله للترجمة. */
object ScriptFilter {
    private fun Char.isHangul() = this in '가'..'힯' || this in 'ᄀ'..'ᇿ' || this in '㄰'..'㆏'
    private fun Char.isKana() = this in '぀'..'ヿ'
    private fun Char.isHan() = this in '一'..'鿿' || this in '㐀'..'䶿'
    private fun Char.isDevanagari() = this in 'ऀ'..'ॿ'
    private fun Char.isLatinLetter() = this in 'A'..'Z' || this in 'a'..'z' || this in 'À'..'ɏ'

    /** هل يحتوي النص على حروف من نظام الكتابة المطلوب؟ */
    /**
     * نظام الكتابة الغالب في النص:
     * أي حرف كانا ⇒ يابانية؛ هانغول ⇒ كورية؛ حروف صينية (هان) بلا كانا ⇒ صينية (أو يابانية بالكانجي فقط)؛
     * ديفاناغاري ⇒ هندية؛ وإلا لاتينية. null إن لم توجد حروف.
     */
    fun dominantScript(text: String): Script? {
        var hangul = 0; var kana = 0; var han = 0; var deva = 0; var latin = 0
        for (c in text) when {
            c.isHangul() -> hangul++
            c.isKana() -> kana++
            c.isHan() -> han++
            c.isDevanagari() -> deva++
            c.isLatinLetter() -> latin++
        }
        val cjk = hangul + kana + han
        return when {
            hangul > 0 && hangul >= kana + han -> Script.KOREAN
            kana > 0 -> Script.JAPANESE
            han > 0 && han * 2 >= latin -> Script.CHINESE
            deva > 0 && deva >= latin -> Script.DEVANAGARI
            latin > 0 -> Script.LATIN
            cjk > 0 -> Script.CHINESE
            else -> null
        }
    }

    /** عدد الحروف "الأصلية" غير اللاتينية (هانغول/كانا/هان/ديفاناغاري). */
    fun nativeCount(text: String) = text.count { it.isHangul() || it.isKana() || it.isHan() || it.isDevanagari() }

    fun containsScript(text: String, script: Script): Boolean = when (script) {
        Script.KOREAN -> text.any { it.isHangul() }
        Script.JAPANESE -> text.any { it.isKana() || it.isHan() }
        Script.CHINESE -> text.any { it.isHan() }
        Script.DEVANAGARI -> text.any { it.isDevanagari() }
        Script.LATIN -> text.any { it.isLatinLetter() }
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
