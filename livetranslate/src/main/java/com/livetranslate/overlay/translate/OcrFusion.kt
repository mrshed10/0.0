package com.livetranslate.overlay.translate

import kotlin.math.max
import kotlin.math.min

/**
 * دمج نتائج عدة نماذج OCR تعمل على نفس الشاشة (لاتيني، ياباني، كوري، صيني، ولقطة مُدارة للنص العمودي).
 *
 * كل نموذج يقرأ كل شيء على الشاشة، لكن بجودة مختلفة: النموذج اللاتيني يُخرج حروفاً عشوائية فوق
 * النص الياباني، والقراءة الأفقية لنص عمودي تُخرج حرفاً واحداً لكل سطر. لذلك تُقيّم كل كتلة
 * بدرجة جودة، ثم تُختار الأفضل لكل منطقة دون تداخل.
 */
object OcrFusion {

    /** درجة الجودة: عدد الحروف المقروءة × الثقة، مع وزن أعلى للحروف الآسيوية ووزن منخفض للأسطر ذات الحرف الواحد. */
    fun score(block: OcrBlock): Float {
        var total = 0f
        for (line in block.lines) {
            val text = line.text
            val native = ScriptFilter.nativeCount(text)
            val latin = text.count { it.isLetterOrDigit() } - native
            // الحرف الآسيوي الواحد يحمل معنى كلمة تقريباً
            val chars = native * 2.5f + latin
            val meaningful = text.count { it.isLetterOrDigit() }
            val lineWeight = if (meaningful <= 1) 0.3f else 1f
            total += chars * lineWeight * line.confidence.coerceIn(0.05f, 1f)
        }
        // مكافأة للنموذج المتخصص في نظام الكتابة الغالب على نصه
        val dominant = ScriptFilter.dominantScript(block.text)
        if (dominant != null && servesScript(block.script, dominant)) total *= 1.25f
        return total
    }

    private fun servesScript(model: Script, text: Script) = when (model) {
        Script.JAPANESE -> text == Script.JAPANESE || text == Script.CHINESE
        Script.CHINESE -> text == Script.CHINESE
        else -> model == text
    }

    /** نسبة التداخل إلى مساحة الكتلة الأصغر. */
    fun overlap(a: OcrBlock, b: OcrBlock): Float {
        val w = min(a.right, b.right) - max(a.left, b.left)
        val h = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (w <= 0f || h <= 0f) return 0f
        val smaller = min(a.width * a.height, b.width * b.height)
        return if (smaller <= 0f) 0f else (w * h) / smaller
    }

    /**
     * يختار أفضل الكتل دون تداخل: الكتل التي تحتوي حروفاً آسيوية حقيقية (≥2) تتقدم على الكتل
     * اللاتينية فوق نفس المنطقة، ثم الترتيب حسب [score].
     */
    fun fuse(candidates: List<OcrBlock>, maxOverlap: Float = 0.35f): List<OcrBlock> {
        val ranked = candidates
            .filter { it.lines.isNotEmpty() }
            .map { Triple(it, if (ScriptFilter.nativeCount(it.text) >= 2) 1 else 0, score(it)) }
            .sortedWith(compareByDescending<Triple<OcrBlock, Int, Float>> { it.second }.thenByDescending { it.third })
        val accepted = mutableListOf<OcrBlock>()
        for ((block, _, s) in ranked) {
            if (s <= 0f) continue
            if (accepted.none { overlap(it, block) > maxOverlap }) accepted += block
        }
        return accepted.sortedWith(compareBy({ it.top }, { it.left }))
    }

    /**
     * يعيد كتلة قُرئت من لقطة مُدارة 90° عكس عقارب الساعة إلى إحداثيات الشاشة.
     * التدوير يحوّل العمود (من الأعلى للأسفل) إلى سطر (من اليسار لليمين)، وترتيب الأسطر من الأعلى للأسفل
     * يقابل ترتيب الأعمدة من اليمين لليسار — وهو ترتيب القراءة الصحيح للنص العمودي.
     *
     * (x', y') في اللقطة المُدارة ⇐ (x = W − y', y = x') في الشاشة، حيث W عرض الشاشة.
     */
    fun unrotate(block: OcrBlock, screenWidth: Float): OcrBlock = OcrBlock(
        lines = block.lines.map { l ->
            OcrLine(
                text = l.text,
                left = screenWidth - l.bottom,
                top = l.left,
                right = screenWidth - l.top,
                bottom = l.right,
                confidence = l.confidence,
            )
        },
        script = block.script,
        vertical = true,
    )
}
