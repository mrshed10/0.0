package com.livetranslate.overlay.translate

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** سطر مقروء بإحداثيات الشاشة. */
data class OcrLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float = 1f,
) {
    val height get() = bottom - top
}

/**
 * كتلة نص (فقرة/فقاعة كلام). [angle] بالدرجات، و[corners] (8 قيم x,y) للنص المائل.
 * [script]: نموذج الـ OCR الذي قرأها. [vertical]: نص عمودي قُرئ من لقطة مُدارة؛ أسطره أعمدة مرتبة للقراءة.
 */
data class OcrBlock(
    val lines: List<OcrLine>,
    val angle: Float = 0f,
    val corners: FloatArray? = null,
    val script: Script = Script.LATIN,
    val vertical: Boolean = false,
) {
    val left get() = lines.minOf { it.left }
    val top get() = lines.minOf { it.top }
    val right get() = lines.maxOf { it.right }
    val bottom get() = lines.maxOf { it.bottom }
    val width get() = right - left
    val lineHeight get() = lines.map { it.height }.average().toFloat()
    val confidence get() = lines.map { it.confidence }.average().toFloat()
    val height get() = bottom - top
    val isStraight get() = abs(angle) < 3f
    val text get() = lines.joinToString(" ") { it.text }

    /** نص متمركز (فقاعات الكلام): مراكز الأسطر متطابقة بينما الحواف اليسرى مختلفة. */
    val isCentered: Boolean
        get() {
            if (vertical) return true
            if (lines.size < 2) return false
            val cx = (left + right) / 2f
            val lh = lineHeight
            val centersAligned = lines.all { abs((it.left + it.right) / 2f - cx) < lh * 0.6f }
            val leftsDiffer = lines.maxOf { it.left } - lines.minOf { it.left } > lh * 0.3f
            return centersAligned && leftsDiffer
        }

    override fun equals(other: Any?) = other is OcrBlock && lines == other.lines && angle == other.angle &&
        script == other.script && vertical == other.vertical
    override fun hashCode() = ((lines.hashCode() * 31 + angle.hashCode()) * 31 + script.hashCode()) * 31 + vertical.hashCode()
}

/**
 * يدمج الكتل المتجاورة عمودياً التي تنتمي غالباً لنفس فقاعة الكلام.
 * ML Kit يقسم كثيراً نص الكوميكس المتمركز إلى عدة كتل صغيرة، فتُترجم أجزاء الجملة منفصلة
 * وتفقد معناها. الدمج يعيد الجملة كاملة للمترجم.
 */
object BlockMerger {

    fun merge(blocks: List<OcrBlock>): List<OcrBlock> {
        val result = blocks.filter { it.lines.isNotEmpty() }.sortedBy { it.top }.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            outer@ for (i in result.indices) {
                for (j in i + 1 until result.size) {
                    if (shouldMerge(result[i], result[j])) {
                        val merged = OcrBlock(
                            (result[i].lines + result[j].lines).sortedBy { it.top },
                            script = result[i].script,
                        )
                        result[i] = merged
                        result.removeAt(j)
                        changed = true
                        break@outer
                    }
                }
            }
        }
        return result
    }

    internal fun shouldMerge(a: OcrBlock, b: OcrBlock): Boolean {
        if (!a.isStraight || !b.isStraight || a.vertical || b.vertical || a.script != b.script) return false
        val (upper, lower) = if (a.top <= b.top) a to b else b to a
        val lh = (upper.lineHeight + lower.lineHeight) / 2f
        if (lh <= 0f) return false
        // أحجام خط متقاربة
        val ratio = upper.lineHeight / lower.lineHeight
        if (ratio < 0.6f || ratio > 1.6f) return false
        // فجوة عمودية صغيرة (أقل من سطر تقريباً)
        val gap = lower.top - upper.bottom
        if (gap > lh * 0.9f || gap < -lh * 0.5f) return false
        val overlap = min(upper.right, lower.right) - max(upper.left, lower.left)
        if (overlap < min(upper.width, lower.width) * 0.3f) return false

        // 1) نص فقاعة متمركز: المراكز متطابقة تقريباً بينما الحواف اليسرى مختلفة
        //    (قوائم الواجهات محاذاتها لليسار فلا تُدمج).
        val centersClose = abs((upper.left + upper.right) / 2f - (lower.left + lower.right) / 2f) < lh * 0.8f
        val leftsDiffer = abs(upper.left - lower.left) > lh * 0.3f
        if (centersClose && leftsDiffer) return true

        // 2) جملة مستمرة: الكتلة العليا لا تنتهي بعلامة نهاية، والسفلى تبدأ بحرف صغير أو تابعة لشرطة.
        val upperText = upper.lines.last().text.trimEnd()
        val lowerText = lower.lines.first().text.trimStart()
        val upperOpen = upperText.isNotEmpty() && upperText.last() !in ".!?:…\"”)"
        val continues = upperText.endsWith("-") || upperText.endsWith(",") ||
            (lowerText.firstOrNull()?.isLowerCase() == true)
        return upperOpen && continues
    }
}
