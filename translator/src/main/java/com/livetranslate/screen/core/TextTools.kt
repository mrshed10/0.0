package com.livetranslate.screen.core

object TextTools {

    private val RTL_LANGS = setOf("ar", "fa", "ur", "he", "iw", "ps", "yi", "sd", "ug", "ckb")

    fun isRtlLanguage(code: String): Boolean = code.substringBefore('-').lowercase() in RTL_LANGS

    /** هل يستحق النص الترجمة؟ (يحتوي على حروف، وليس أرقاماً/رموزاً فقط) */
    fun isTranslatable(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        var letters = 0
        var cjk = false
        var i = 0
        while (i < t.length) {
            val cp = t.codePointAt(i)
            if (Character.isLetter(cp)) {
                letters++
                if (isCjk(cp)) cjk = true
            }
            i += Character.charCount(cp)
        }
        return letters >= 2 || (cjk && letters >= 1)
    }

    fun isCjk(cp: Int): Boolean {
        val block = Character.UnicodeScript.of(cp)
        return block == Character.UnicodeScript.HAN ||
            block == Character.UnicodeScript.HIRAGANA ||
            block == Character.UnicodeScript.KATAKANA ||
            block == Character.UnicodeScript.HANGUL
    }

    /** دمج أسطر OCR: إزالة الواصلة عند نهاية السطر، ودمج بدون مسافات للغات CJK. */
    fun joinLines(lines: List<String>, cjk: Boolean): String {
        val sb = StringBuilder()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (sb.isEmpty()) { sb.append(line); continue }
            when {
                cjk -> sb.append(line)
                sb.endsWith("-") && sb.length > 1 && sb[sb.length - 2].isLetter() -> {
                    sb.setLength(sb.length - 1); sb.append(line)
                }
                else -> sb.append(' ').append(line)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    /**
     * نصوص المانغا والكوميكس تُكتب غالباً بحروف كبيرة كلها ("YO TE CONOZCO")، ومحركات الترجمة
     * تتعامل معها كاختصارات فتسيء ترجمتها. نحوّلها إلى صيغة الجملة العادية قبل الاكتشاف والترجمة.
     */
    fun normalizeCase(text: String): String {
        var upper = 0
        var letters = 0
        for (ch in text) {
            if (ch.isLetter() && ch.isUpperCase() != ch.isLowerCase()) {
                letters++
                if (ch.isUpperCase()) upper++
            }
        }
        if (letters < 4 || upper < letters * 0.8f) return text
        val sb = StringBuilder(text.length)
        var capitalizeNext = true
        for (ch in text.lowercase()) {
            if (capitalizeNext && ch.isLetter()) {
                sb.append(ch.uppercaseChar()); capitalizeNext = false
            } else {
                sb.append(ch)
            }
            if (ch == '.' || ch == '!' || ch == '?' || ch == '¡' || ch == '¿' || ch == '…') capitalizeNext = true
        }
        return sb.toString()
    }

    /** بصمة ثابتة لمجموعة كتل لتجنب إعادة الترجمة/الرسم إذا لم يتغير شيء. */
    fun signature(blocks: List<SourceBlock>, quantum: Int = 8): Int {
        var h = 17
        for (b in blocks) {
            h = h * 31 + b.text.hashCode()
            h = h * 31 + b.box.left / quantum
            h = h * 31 + b.box.top / quantum
            h = h * 31 + b.box.width / quantum
        }
        return h * 31 + blocks.size
    }
}
