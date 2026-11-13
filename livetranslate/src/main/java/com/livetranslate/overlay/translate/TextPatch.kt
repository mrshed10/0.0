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
    /** زاوية الرسم بالدرجات (0 للنص الأفقي والعمودي؛ ميل حتى ±45° للنص المائل داخل الصور). */
    val angle: Float,
    /** حجم حرف النص الأصلي بالبكسل (ارتفاع السطر، أو عرض العمود في النص العمودي): أقصى حجم للترجمة. */
    val glyphSize: Float,
    /** حشوة تُضاف حول الصندوق لتغطية أطراف الحروف الأصلية بالكامل. */
    val padding: Float,
    /** محاذاة في الوسط (فقاعات الكلام والنص العمودي) بدلاً من بداية السطر. */
    val centered: Boolean,
    val background: Int,
    val textColor: Int,
)
