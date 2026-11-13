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
 * تُستخدم لمعرفة هل تحركت الشاشة (تمرير) أو استقرت، دون تحويل الإطار إلى Bitmap.
 * الهوامش مستبعدة لأن الإطار المضيء المتحرك وشريط الحالة يتغيران باستمرار.
 */
class FrameSignature(val samples: ByteArray) {

    fun differsFrom(other: FrameSignature?, pixelThreshold: Int = 12, changedRatio: Float = 0.006f): Boolean {
        if (other == null || other.samples.size != samples.size) return true
        val limit = max(1, (samples.size * changedRatio).toInt())
        var changed = 0
        for (i in samples.indices) {
            val a = samples[i].toInt() and 0xFF
            val b = other.samples[i].toInt() and 0xFF
            if (abs(a - b) > pixelThreshold && ++changed > limit) return true
        }
        return false
    }

    companion object {
        const val COLS = 32
        const val ROWS = 64

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
            val left = (width * 0.04f).toInt()
            val right = (width * 0.96f).toInt()
            val top = (height * 0.05f).toInt()
            val bottom = (height * 0.96f).toInt()
            val stepX = (right - left).toFloat() / COLS
            val stepY = (bottom - top).toFloat() / ROWS
            var i = 0
            for (row in 0 until ROWS) {
                val y = (top + (row + 0.5f) * stepY).toInt().coerceIn(0, height - 1)
                for (col in 0 until COLS) {
                    val x = (left + (col + 0.5f) * stepX).toInt().coerceIn(0, width - 1)
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
            return FrameSignature(out)
        }
    }
}
