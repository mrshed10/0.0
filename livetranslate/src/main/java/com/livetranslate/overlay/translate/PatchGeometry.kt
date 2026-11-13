package com.livetranslate.overlay.translate

/** مستطيل بإحداثيات عشرية (منطق خالص قابل للاختبار). */
data class RectF4(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
}

/** حسابات أماكن رسم الترجمة. */
object PatchGeometry {

    /**
     * زاوية الرسم: النص المائل قليلاً (حتى 45°، مثل الكتابة داخل الصور) يُرسم بنفس الميل؛
     * أما الأكثر ميلاً (عمودي/جانبي/مقلوب) فيُرسم أفقياً مستقيماً حتى يُقرأ بوضوح.
     */
    fun drawAngle(angle: Float): Float {
        var a = angle % 360f
        if (a > 180f) a -= 360f
        if (a < -180f) a += 360f
        return if (a in -45f..45f && (a > 2f || a < -2f)) a else 0f
    }

    /** هل يُعامل النص كعمودي (يُرسم أفقياً في صندوق موسّع)؟ */
    fun isUprightVertical(angle: Float, width: Float, height: Float): Boolean {
        var a = angle % 180f
        if (a < 0f) a += 180f
        val sideways = a in 45f..135f
        return sideways && height > width * 1.2f
    }
}
