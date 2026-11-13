package com.livetranslate.overlay.translate

/**
 * نص مترجم يُرسم فوق موضع النص الأصلي تماماً.
 * الإحداثيات بإحداثيات الشاشة الكاملة (نفس إحداثيات الإطار الملتقط).
 */
data class TextPatch(
    val translated: String,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    /** زاوية ميل النص الأصلي بالدرجات (نص مائل داخل صورة مثلاً). */
    val angle: Float,
    val lineCount: Int,
    val background: Int,
    val textColor: Int,
)
