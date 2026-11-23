package app.safar.browser.translate.ocr

import android.graphics.Bitmap

/** مستطيل مستقل عن Android ليسهل اختباره. الإحداثيات بالبكسل. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun scaled(f: Float) = Box(left * f, top * f, right * f, bottom * f)
    fun offset(dx: Float, dy: Float) = Box(left + dx, top + dy, right + dx, bottom + dy)
    fun union(o: Box) = Box(minOf(left, o.left), minOf(top, o.top), maxOf(right, o.right), maxOf(bottom, o.bottom))
}

data class OcrBlock(val text: String, val box: Box, val lines: List<String> = listOf(text))

interface OcrEngine {
    /** @param langHint رمز لغة المصدر أو "auto" */
    suspend fun recognize(bitmap: Bitmap, langHint: String): List<OcrBlock>
}
