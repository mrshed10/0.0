package com.livetranslate.screen.core

import kotlin.math.abs

/**
 * بصمة مصغّرة للإطار (شبكة إضاءة) لاكتشاف تغيّر المحتوى بتكلفة منخفضة جداً
 * دون تشغيل OCR على كل إطار. الخلايا التي تغطيها نوافذنا العائمة تُستبعد (-1)
 * حتى لا تُحسب ترجماتنا نفسها كتغيير في المحتوى.
 */
object FrameSignature {

    const val MASKED = -1

    inline fun compute(
        zone: Box,
        cols: Int,
        rows: Int,
        mask: List<Box>,
        crossinline luminance: (x: Int, y: Int) -> Int,
    ): IntArray {
        val out = IntArray(cols * rows)
        val cw = zone.width.toFloat() / cols
        val ch = zone.height.toFloat() / rows
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = Box(
                    zone.left + (c * cw).toInt(), zone.top + (r * ch).toInt(),
                    zone.left + ((c + 1) * cw).toInt(), zone.top + ((r + 1) * ch).toInt(),
                )
                if (cell.isEmpty || mask.any { it.intersects(cell) }) {
                    out[r * cols + c] = MASKED
                    continue
                }
                // 4 عينات داخل الخلية (عند الربع والثلاثة أرباع)
                val x1 = cell.left + cell.width / 4
                val x2 = cell.left + cell.width * 3 / 4
                val y1 = cell.top + cell.height / 4
                val y2 = cell.top + cell.height * 3 / 4
                out[r * cols + c] = (luminance(x1, y1) + luminance(x2, y1) + luminance(x1, y2) + luminance(x2, y2)) / 4
            }
        }
        return out
    }

    /** نسبة الخلايا المتغيرة (0..1) بين بصمتين، مع تجاهل الخلايا المحجوبة. */
    fun difference(a: IntArray?, b: IntArray?, threshold: Int = 18): Float {
        if (a == null || b == null || a.size != b.size) return 1f
        var compared = 0
        var changed = 0
        for (i in a.indices) {
            if (a[i] == MASKED || b[i] == MASKED) continue
            compared++
            if (abs(a[i] - b[i]) > threshold) changed++
        }
        return if (compared == 0) 0f else changed.toFloat() / compared
    }

    fun luminance(r: Int, g: Int, b: Int): Int = (r * 299 + g * 587 + b * 114) / 1000
}
