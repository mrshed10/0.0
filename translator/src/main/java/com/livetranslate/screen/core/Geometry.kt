package com.livetranslate.screen.core

import kotlin.math.max
import kotlin.math.min

/** مستطيل بإحداثيات الشاشة (بكسل). مستقل عن Android ليسهل اختباره. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val isEmpty: Boolean get() = width <= 0 || height <= 0
    val area: Long get() = if (isEmpty) 0L else width.toLong() * height.toLong()

    fun intersects(o: Box): Boolean =
        left < o.right && o.left < right && top < o.bottom && o.top < bottom

    fun intersect(o: Box): Box? {
        val b = Box(max(left, o.left), max(top, o.top), min(right, o.right), min(bottom, o.bottom))
        return if (b.isEmpty) null else b
    }

    fun union(o: Box): Box = Box(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))

    fun offset(dx: Int, dy: Int): Box = Box(left + dx, top + dy, right + dx, bottom + dy)

    fun inflate(d: Int): Box = Box(left - d, top - d, right + d, bottom + d)

    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom

    fun contains(o: Box): Boolean = o.left >= left && o.top >= top && o.right <= right && o.bottom <= bottom

    /** نسبة التداخل إلى مساحة هذا المستطيل. */
    fun coverageBy(o: Box): Float {
        val i = intersect(o) ?: return 0f
        return if (area == 0L) 0f else i.area.toFloat() / area.toFloat()
    }

    /** طول التداخل الأفقي (قد يكون سالباً = فجوة). */
    fun horizontalOverlap(o: Box): Int = min(right, o.right) - max(left, o.left)

    /** طول التداخل الرأسي (قد يكون سالباً = فجوة). */
    fun verticalOverlap(o: Box): Int = min(bottom, o.bottom) - max(top, o.top)

    /**
     * توسيع الصندوق أفقياً حول مركزه حتى نسبة عرض/ارتفاع دنيا (فقاعات المانغا الرأسية الضيقة)
     * كي تبقى الترجمة العربية مقروءة بدل أن تُضغط في عمود رفيع.
     */
    fun widenToAspect(minAspect: Float, bounds: Box): Box {
        val wanted = (height * minAspect).toInt()
        if (width >= wanted) return this
        val w = minOf(wanted, bounds.width)
        val l = (centerX - w / 2).coerceIn(bounds.left, bounds.right - w)
        return Box(l, top, l + w, bottom)
    }

    /** حصر المستطيل داخل حدود معينة مع الحفاظ على الحجم قدر الإمكان. */
    fun clampInto(bounds: Box, minW: Int, minH: Int): Box {
        val w = width.coerceIn(min(minW, bounds.width), bounds.width)
        val h = height.coerceIn(min(minH, bounds.height), bounds.height)
        val l = left.coerceIn(bounds.left, bounds.right - w)
        val t = top.coerceIn(bounds.top, bounds.bottom - h)
        return Box(l, t, l + w, t + h)
    }
}

enum class DisplayMode { FULL_SCREEN, BUBBLE }

/** وضع المحتوى (المبدّل السريع): يحدد محرك الاستخراج. */
enum class ContentMode {
    /** قراءة مباشرة لشجرة عناصر الواجهة عبر خدمة إمكانية الوصول. */
    TEXT,
    /** OCR سريع على الإطارات لالتقاط الترجمات المرئية (Subtitles). */
    VIDEO,
    /** OCR مع تجميع الفقاعات وترتيب القراءة وأخذ لون الخلفية. */
    MANGA;

    val usesOcr: Boolean get() = this != TEXT
}

/** محركات التعرف الضوئي المتاحة في ML Kit. */
enum class OcrScript {
    LATIN, CHINESE, JAPANESE, KOREAN, DEVANAGARI;

    val isCjk: Boolean get() = this == CHINESE || this == JAPANESE || this == KOREAN

    companion object {
        fun forLanguage(code: String): OcrScript? = when (code.substringBefore('-').lowercase()) {
            "ja" -> JAPANESE
            "zh" -> CHINESE
            "ko" -> KOREAN
            "hi", "mr", "ne", "sa" -> DEVANAGARI
            "auto", "" -> null
            else -> LATIN
        }
    }
}

/** كتلة نصية مصدرية (من شجرة الواجهة أو من OCR) بإحداثيات الشاشة. */
data class SourceBlock(
    val text: String,
    val box: Box,
    val lines: List<Box> = emptyList(),
    /** لون الخلفية المأخوذ من الصورة (وضع المانغا)، أو null. */
    val backgroundColor: Int? = null,
)

/** كتلة مترجمة جاهزة للرسم. */
data class RenderedBlock(
    val box: Box,
    val text: String,
    val rtl: Boolean,
    val backgroundColor: Int? = null,
    val centered: Boolean = false,
)

/** شريط الترجمة المرئية (وضع الفيديو) يُرسم خارج منطقة الالتقاط كي لا يغطيها. */
data class Caption(val text: String, val rtl: Boolean, val anchor: Box)
