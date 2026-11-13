package com.livetranslate.overlay.translate

import kotlin.math.max
import kotlin.math.min

/** مستطيل بإحداثيات عشرية (منطق خالص قابل للاختبار). */
data class RectF4(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
}

/** حسابات أماكن رسم الترجمة. */
object PatchGeometry {

    /**
     * النص العمودي يشغل عموداً ضيقاً وطويلاً؛ الترجمة الأفقية فيه ستكون حرفاً أو اثنين في كل سطر.
     * نوسّع الصندوق أفقياً حول مركزه (حتى ~75% من ارتفاعه أو [maxWidth]) دون أن يتجاوز
     * أي نص مجاور على نفس الارتفاع، ودون الخروج من الشاشة. الصندوق الناتج يغطي الأصل دائماً.
     */
    fun widenVertical(
        box: RectF4,
        neighbours: List<RectF4>,
        screenWidth: Float,
        maxWidth: Float,
        gap: Float,
    ): RectF4 {
        val desired = min(max(box.width, box.height * 0.75f), maxWidth)
        if (desired <= box.width) return box
        var left = box.centerX - desired / 2f
        var right = box.centerX + desired / 2f
        for (n in neighbours) {
            if (n == box) continue
            val sameRow = n.top < box.bottom && n.bottom > box.top
            if (!sameRow) continue
            if (n.right <= box.left) left = max(left, n.right + gap)
            else if (n.left >= box.right) right = min(right, n.left - gap)
        }
        left = min(max(left, 0f), box.left)
        right = max(min(right, screenWidth), box.right)
        return RectF4(left, box.top, right, box.bottom)
    }

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
