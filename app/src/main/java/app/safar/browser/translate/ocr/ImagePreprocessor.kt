package app.safar.browser.translate.ocr

import android.graphics.Bitmap

/**
 * تحسين الصورة قبل OCR لتقليل أخطاء الحروف في المانغا والخطوط المائلة/الصغيرة:
 * 1) تكبير الصور الصغيرة (النص الصغير < ~20px يضعف التعرف)
 * 2) تحويل لرمادي + مط التباين (يزيل أثر الـ screentone والخلفيات الباهتة)
 */
object ImagePreprocessor {
    data class Prepared(val bitmap: Bitmap, val scale: Float)

    fun prepare(src: Bitmap, minWidth: Int = 1080): Prepared {
        val scale = if (src.width < minWidth) (minWidth.toFloat() / src.width).coerceAtMost(2.5f) else 1f
        val scaled = if (scale > 1f) {
            Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
        } else src.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(scaled.width * scaled.height)
        scaled.getPixels(px, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        stretchContrast(px)
        val out = if (scaled.isMutable) scaled else scaled.copy(Bitmap.Config.ARGB_8888, true)
        out.setPixels(px, 0, out.width, 0, 0, out.width, out.height)
        return Prepared(out, scale)
    }

    /** دالة نقية (قابلة للاختبار): رمادي + مط التباين بين المئين 2 و 98. */
    fun stretchContrast(px: IntArray) {
        if (px.isEmpty()) return
        val hist = IntArray(256)
        val gray = IntArray(px.size)
        for (i in px.indices) {
            val c = px[i]
            val g = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
            gray[i] = g; hist[g]++
        }
        val lowCut = px.size * 0.02; val highCut = px.size * 0.98
        var acc = 0; var lo = 0; var hi = 255
        for (v in 0..255) { acc += hist[v]; if (acc >= lowCut) { lo = v; break } }
        acc = 0
        for (v in 0..255) { acc += hist[v]; if (acc >= highCut) { hi = v; break } }
        val range = (hi - lo).coerceAtLeast(1)
        for (i in px.indices) {
            val v = ((gray[i] - lo) * 255 / range).coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }
}
