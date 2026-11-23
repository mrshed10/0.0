package app.safar.browser.translate.ocr

/**
 * تنظيف مخرجات OCR قبل الترجمة (دوال نقية قابلة للاختبار):
 * - دمج الكتل المتقاربة (نفس فقاعة الكلام)
 * - ضم الأسطر: بلا مسافة للغات CJK، بمسافة للاتينية، وإزالة الواصلة عند كسر الكلمة
 * - تصحيح أخطاء شائعة (| -> I، 0 بين حرفين -> O ...)
 * - حذف الضجيج (رموز منفردة)
 */
object OcrPostProcessor {
    private val cjk = Regex("""[\p{IsHan}\p{IsHiragana}\p{IsKatakana}\p{IsHangul}]""")
    private val noise = Regex("""^[\p{P}\p{S}\s\d]{0,3}$""")

    fun process(blocks: List<OcrBlock>, mergeGapPx: Float = 18f): List<OcrBlock> =
        merge(blocks.filterNot { isGarbage(it) }, mergeGapPx)
            .map { it.copy(text = fixCommonErrors(joinLines(it.lines))) }
            .filterNot { isGarbage(it) }

    private val letter = Regex("""\p{L}""")
    private val okSingle = setOf("I", "a", "A", "O", "o")

    /**
     * رموز عشوائية ناتجة عن خطوط الرسم: أحرف مفردة، رموز، كتل صغيرة جداً،
     * أو سلاسل أغلبها أحرف منفصلة ("l I | i ,"). الحرف الياباني/الكوري المفرد يُقبل فقط إن كان كبيراً (مؤثر صوتي).
     */
    fun isGarbage(b: OcrBlock): Boolean {
        val s = b.text.trim()
        if (isNoise(s)) return true
        if (b.box.height < 7f || b.box.width < 7f) return true
        val compact = s.filterNot { it.isWhitespace() }
        val letters = letter.findAll(compact).count()
        if (letters == 0) return true
        if (cjk.containsMatchIn(s)) {
            return letters == 1 && b.box.height < 18f
        }
        if (letters < 2) return true
        if (letters.toDouble() / compact.length < 0.5) return true
        val tokens = s.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (tokens.size >= 2) {
            val singles = tokens.count { t -> letter.findAll(t).count() <= 1 && t !in okSingle }
            if (singles.toDouble() / tokens.size > 0.6) return true
        }
        // كلمات "مشوهة": ما يخرجه OCR اللاتيني من خطوط الرسم أو من حروف غير لاتينية (مثل "hiibiN" و"JQvWAY")
        val words = tokens.map { it.filter { c -> c.isLetterOrDigit() } }.filter { it.length >= 3 }
        if (words.isNotEmpty() && words.count(::isWeirdWord).toDouble() / words.size > 0.4) return true
        return false
    }

    private val vowels = Regex("""[aeiouyAEIOUY]""")

    /** كلمة لاتينية غير طبيعية: تبديل حالة الأحرف داخلها، أو أرقام مختلطة بأحرف، أو بلا حروف علة. */
    fun isWeirdWord(w: String): Boolean {
        if (!w.all { it.code < 0x250 }) return false // ليست لاتينية
        val letters = w.filter { it.isLetter() }
        if (letters.isEmpty()) return false
        if (w.any { it.isDigit() } && letters.length >= 2) return true
        // حرف صغير يليه كبير داخل الكلمة (hiibiN, iYYOC, JQvWAY) لا يحدث في الحوار الحقيقي
        for (i in 1 until letters.length) if (letters[i - 1].isLowerCase() && letters[i].isUpperCase()) return true
        if (letters.length >= 4 && !vowels.containsMatchIn(letters)) return true
        // 6 حروف ساكنة متتالية أو أكثر ("čpssss") لا تكاد توجد في كلمات حقيقية
        var run = 0
        for (c in letters) { run = if (vowels.matches(c.toString())) 0 else run + 1; if (run >= 6) return true }
        return false
    }

    fun isNoise(t: String): Boolean {
        val s = t.trim()
        if (s.isEmpty()) return true
        if (cjk.containsMatchIn(s)) return false // حرف CJK واحد قد يكون مؤثراً صوتياً مهماً
        return noise.matches(s)
    }

    fun joinLines(lines: List<String>): String {
        val sb = StringBuilder()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (sb.isEmpty()) { sb.append(line); continue }
            val last = sb.last()
            when {
                last == '-' && line.first().isLowerCase() -> { sb.setLength(sb.length - 1); sb.append(line) }
                cjk.containsMatchIn(last.toString()) || cjk.containsMatchIn(line.first().toString()) -> sb.append(line)
                else -> sb.append(' ').append(line)
            }
        }
        return sb.toString()
    }

    fun fixCommonErrors(t: String): String = t
        .replace(Regex("""(?<=\s|^)\|(?=\s|$)"""), "I")
        .replace(Regex("""(?<=[A-Za-z])0(?=[A-Za-z])"""), "o")
        .replace(Regex("""(?<=[a-z])1(?=[a-z])"""), "l")
        .replace(Regex("""\s+([,.!?;:])"""), "$1")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()

    /** دمج الكتل المتداخلة أفقياً والمتقاربة عمودياً (أو العكس للنص العمودي). */
    fun merge(blocks: List<OcrBlock>, gap: Float): List<OcrBlock> {
        val result = blocks.sortedWith(compareBy({ it.box.top }, { it.box.left })).toMutableList()
        var changed = true
        while (changed) {
            changed = false
            loop@ for (i in result.indices) for (j in i + 1 until result.size) {
                if (close(result[i].box, result[j].box, gap)) {
                    val a = result[i]; val b = result[j]
                    result[i] = OcrBlock("", a.box.union(b.box), a.lines + b.lines)
                    result.removeAt(j)
                    changed = true
                    break@loop
                }
            }
        }
        return result.map { it.copy(text = it.lines.joinToString("\n")) }
    }

    private fun close(a: Box, b: Box, gap: Float): Boolean {
        val hOverlap = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val vOverlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        val vGap = maxOf(a.top, b.top) - minOf(a.bottom, b.bottom)
        val hGap = maxOf(a.left, b.left) - minOf(a.right, b.right)
        val minW = minOf(a.width, b.width).coerceAtLeast(1f)
        val minH = minOf(a.height, b.height).coerceAtLeast(1f)
        return (hOverlap > 0.3f * minW && vGap < gap) || (vOverlap > 0.5f * minH && hGap < gap * 0.6f)
    }
}
