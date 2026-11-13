package com.livetranslate.overlay.capture

import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

/** مستطيل بسيط بإحداثيات الشاشة (بدلاً من android.graphics.Rect ليبقى قابلاً للاختبار). */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun contains(x: Int, y: Int) = x in left until right && y in top until bottom
}

/**
 * "بصمة" رخيصة لإطار الشاشة: شبكة من قيم السطوع.
 * تُستخدم لمعرفة هل تحرك محتوى الشاشة (تمرير) أو استقر، دون تحويل الإطار إلى Bitmap.
 * شريطا الحالة والتنقل مستبعدان لأنهما يتغيران دون علاقة بالمحتوى.
 */
class FrameSignature(val samples: ByteArray, val width: Int, val height: Int) {

    /**
     * @param ignore نقاط الشبكة المغطّاة بالترجمة المعروضة حالياً (تُتجاهل حتى لا تُحسب ترجمتنا "تغيّراً").
     */
    fun differsFrom(
        other: FrameSignature?,
        ignore: BooleanArray? = null,
        pixelThreshold: Int = 12,
        changedRatio: Float = 0.006f,
    ): Boolean {
        if (other == null || other.samples.size != samples.size || other.width != width || other.height != height) {
            return true
        }
        val limit = max(1, (samples.size * changedRatio).toInt())
        var changed = 0
        for (i in samples.indices) {
            if (ignore != null && ignore[i]) continue
            val a = samples[i].toInt() and 0xFF
            val b = other.samples[i].toInt() and 0xFF
            if (abs(a - b) > pixelThreshold && ++changed > limit) return true
        }
        return false
    }

    companion object {
        const val COLS = 32
        const val ROWS = 64

        private fun sampleX(col: Int, width: Int): Int {
            val left = width * 0.02f
            val step = (width * 0.98f - left) / COLS
            return (left + (col + 0.5f) * step).toInt().coerceIn(0, width - 1)
        }

        private fun sampleY(row: Int, height: Int): Int {
            val top = height * 0.05f
            val step = (height * 0.96f - top) / ROWS
            return (top + (row + 0.5f) * step).toInt().coerceIn(0, height - 1)
        }

        /** يحسب البصمة مباشرة من مخزن RGBA_8888 الخاص بـ ImageReader. */
        fun compute(
            buffer: ByteBuffer,
            width: Int,
            height: Int,
            rowStride: Int,
            pixelStride: Int,
            exclude: Box? = null,
        ): FrameSignature {
            val out = ByteArray(COLS * ROWS)
            var i = 0
            for (row in 0 until ROWS) {
                val y = sampleY(row, height)
                for (col in 0 until COLS) {
                    val x = sampleX(col, width)
                    if (exclude != null && exclude.contains(x, y)) {
                        out[i++] = 0
                        continue
                    }
                    val off = y * rowStride + x * pixelStride
                    val r = buffer.get(off).toInt() and 0xFF
                    val g = buffer.get(off + 1).toInt() and 0xFF
                    val b = buffer.get(off + 2).toInt() and 0xFF
                    out[i++] = ((r * 3 + g * 6 + b) / 10).toByte()
                }
            }
            return FrameSignature(out, width, height)
        }

        /** قناع نقاط الشبكة الواقعة داخل [boxes] (موسّعة بـ [pad] بكسل)، أو null إن لم توجد. */
        fun maskFor(width: Int, height: Int, boxes: List<Box>, pad: Int = 6): BooleanArray? {
            if (boxes.isEmpty()) return null
            val mask = BooleanArray(COLS * ROWS)
            var any = false
            for (row in 0 until ROWS) {
                val y = sampleY(row, height)
                for (col in 0 until COLS) {
                    val x = sampleX(col, width)
                    if (boxes.any { x >= it.left - pad && x < it.right + pad && y >= it.top - pad && y < it.bottom + pad }) {
                        mask[row * COLS + col] = true
                        any = true
                    }
                }
            }
            return if (any) mask else null
        }
    }
}
