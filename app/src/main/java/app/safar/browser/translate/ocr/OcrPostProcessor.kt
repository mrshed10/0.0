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
        merge(blocks, mergeGapPx)
            .map { it.copy(text = fixCommonErrors(joinLines(it.lines))) }
            .filterNot { isNoise(it.text) }

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
