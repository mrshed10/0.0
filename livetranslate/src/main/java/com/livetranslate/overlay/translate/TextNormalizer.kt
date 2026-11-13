package com.livetranslate.overlay.translate

/**
 * تنظيف نص الـ OCR قبل الترجمة، مع تركيز على خطوط القصص المصوّرة والمانجا:
 * - ضم الأسطر المكسورة داخل فقاعة الكلام ومعالجة الكلمات المقسومة بشرطة ("SOME-" + "THING").
 * - تحويل النص المكتوب كله بحروف كبيرة (أسلوب الكوميكس) إلى حالة الجملة؛ المترجم يتعامل
 *   مع "HELLO THERE" كاختصارات وأسماء علم فيتركها دون ترجمة.
 * - إصلاح الخلط الشائع بين 0/O و 1/I و | داخل الكلمات بخطوط الكوميكس العريضة.
 */
object TextNormalizer {

    fun joinLines(lines: List<String>, joiner: String): String {
        val sb = StringBuilder()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (sb.isEmpty()) {
                sb.append(line)
                continue
            }
            val endsWithHyphen = sb.length >= 2 && sb.last() == '-' && sb[sb.length - 2].isLetter()
            if (joiner.isNotEmpty() && endsWithHyphen && line.first().isLetter()) {
                sb.setLength(sb.length - 1)
                sb.append(line)
            } else {
                sb.append(joiner).append(line)
            }
        }
        return sb.toString()
    }

    private val confusable = setOf('0', '1', '|')

    /** يستبدل 0→O و 1→I و |→I فقط عندما تقع داخل كلمة حروفها لاتينية. */
    fun fixOcrConfusions(text: String): String = Regex("\\S+").replace(text) { m -> fixToken(m.value) }

    private fun fixToken(token: String): String {
        if (token == "|") return "I"
        val letters = token.count { it.isLetter() }
        if (letters == 0 || token.none { it in confusable }) return token
        val otherDigits = token.count { it.isDigit() && it !in confusable }
        val chars = token.toCharArray()
        var i = 0
        while (i < chars.size) {
            if (chars[i] !in confusable) {
                i++
                continue
            }
            val start = i
            while (i < chars.size && chars[i] in confusable) i++
            val before = chars.getOrNull(start - 1)
            val after = chars.getOrNull(i)
            val letterBefore = before?.isLetter() == true
            val letterAfter = after?.isLetter() == true
            val bothSides = letterBefore && letterAfter
            // على طرف الكلمة: "G0"→"GO" يكفيه حرف واحد، أما 1/| فتحتاج كلمة أطول ("A1" تبقى كما هي)
            val onlyZeros = (start until i).all { chars[it] == '0' }
            val minLetters = if (onlyZeros) 1 else 3
            val edge = (letterBefore && after == null || letterAfter && before == null) &&
                letters >= minLetters && otherDigits == 0
            if (bothSides || edge) {
                for (k in start until i) chars[k] = if (chars[k] == '0') 'O' else 'I'
            }
        }
        return String(chars)
    }

    fun isShouting(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.length < 4) return false
        return letters.count { it.isUpperCase() }.toFloat() / letters.length >= 0.8f
    }

    fun sentenceCase(text: String): String {
        val lower = text.lowercase()
        val sb = StringBuilder(lower.length)
        var capitalizeNext = true
        for (c in lower) {
            if (capitalizeNext && c.isLetter()) {
                sb.append(c.uppercaseChar())
                capitalizeNext = false
            } else {
                sb.append(c)
            }
            if (c == '.' || c == '!' || c == '?' || c == '…') capitalizeNext = true
        }
        // الضمير الإنجليزي I يبقى كبيراً: i, i'm, i'll ...
        return Regex("(?<![\\p{L}'])i(?=$|[^\\p{L}])").replace(sb.toString(), "I")
    }

    /** الخطوات كلها بالترتيب. [latin] = هل النص بالحروف اللاتينية. */
    fun normalize(lines: List<String>, joiner: String, latin: Boolean): String {
        var text = joinLines(lines, joiner).replace(Regex("\\s+"), " ").trim()
        if (latin) {
            text = fixOcrConfusions(text)
            if (isShouting(text)) text = sentenceCase(text)
        }
        return text
    }
}
