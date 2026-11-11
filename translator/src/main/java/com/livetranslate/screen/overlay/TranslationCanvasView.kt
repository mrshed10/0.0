package com.livetranslate.screen.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.View
import com.livetranslate.screen.core.Box
import com.livetranslate.screen.core.Caption
import com.livetranslate.screen.core.RenderedBlock
import kotlin.math.max
import kotlin.math.min

/**
 * طبقة الرسم الديناميكية (Dynamic Canvas Overlay): نافذة بملء الشاشة لا تستقبل اللمس،
 * ترسم الإطار الزجاجي للمربع العائم + الترجمات فوق أماكن النصوص الأصلية + شريط الترجمة المرئية.
 * كل الإحداثيات المستلمة بإحداثيات الشاشة، وتُحوَّل هنا حسب موضع النافذة الفعلي.
 */
@SuppressLint("ViewConstructor")
class TranslationCanvasView(context: Context) : View(context) {

    private val dp = context.resources.displayMetrics.density
    private val sp = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 1f, context.resources.displayMetrics)

    private class Item(
        val rect: RectF,
        val bg: Int,
        val layout: StaticLayout,
        val padX: Float,
        val padY: Float,
    )

    private var items: List<Item> = emptyList()
    private var captionItem: Item? = null
    var bubble: Box? = null
        set(value) { field = value; invalidate() }

    private val origin = IntArray(2)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f * dp; color = 0xE6FFFFFF.toInt()
    }
    private val frameShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f * dp; color = 0x55000000
    }
    private val glassFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1A9EC5FF }
    private val glassHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f * dp; color = 0x66BBD6FF
    }

    /** موضع هذه النافذة على الشاشة (لتحويل إحداثيات الشاشة إلى إحداثيات النافذة). */
    val windowOrigin: Pair<Int, Int>
        get() { getLocationOnScreen(origin); return origin[0] to origin[1] }

    fun setContent(blocks: List<RenderedBlock>, caption: Caption?, opacity: Float) {
        val alpha = (opacity.coerceIn(0.3f, 1f) * 255).toInt()
        items = blocks.map { buildItem(it, alpha) }
        captionItem = caption?.let { buildCaption(it) }
        invalidate()
    }

    fun clearContent() {
        if (items.isEmpty() && captionItem == null) return
        items = emptyList(); captionItem = null
        invalidate()
    }

    val hasContent: Boolean get() = items.isNotEmpty() || captionItem != null

    /** المستطيلات المرسومة حالياً (بإحداثيات الشاشة) لاستبعادها من اكتشاف التغيّر. */
    fun occupiedRects(): List<Box> =
        (items + listOfNotNull(captionItem)).map {
            Box(it.rect.left.toInt(), it.rect.top.toInt(), it.rect.right.toInt() + 1, it.rect.bottom.toInt() + 1)
        }

    private fun buildItem(b: RenderedBlock, alpha: Int): Item {
        val bgBase = b.backgroundColor ?: 0xFFF8FAFD.toInt()
        val bg = if (b.backgroundColor != null) bgBase or (0xFF shl 24) else (bgBase and 0x00FFFFFF) or (alpha shl 24)
        val fg = if (luminance(bgBase) > 0.55f) 0xFF111418.toInt() else Color.WHITE
        val padX = 3f * dp
        val padY = 1.5f * dp
        val width = max(b.box.width - (2 * padX).toInt(), (24 * dp).toInt())
        val maxH = max(b.box.height - (2 * padY).toInt(), (10 * dp).toInt())
        // أكبر خط يناسب ارتفاع السطر الأصلي، بحد أعلى 20sp وأدنى 8sp
        val maxPx = min(20f * sp, max(9f * sp, b.box.height * 0.72f))
        val layout = fit(b.text, width, maxH, maxPx, 8f * sp, fg, b.rtl, b.centered)
        val h = max(b.box.height.toFloat(), layout.height + 2 * padY)
        val rect = RectF(b.box.left.toFloat(), b.box.top.toFloat(), b.box.right.toFloat(), b.box.top + h)
        return Item(rect, bg, layout, padX, padY)
    }

    private fun buildCaption(c: Caption): Item {
        val screenW = resources.displayMetrics.widthPixels
        val width = max(c.anchor.width, (screenW * 0.6f).toInt()).coerceAtMost(screenW - (16 * dp).toInt())
        val padX = 12f * dp
        val padY = 8f * dp
        val layout = fit(c.text, width - (2 * padX).toInt(), (140 * dp).toInt(), 18f * sp, 12f * sp, Color.WHITE, c.rtl, true)
        val h = layout.height + 2 * padY
        val left = (c.anchor.centerX - width / 2f).coerceIn(8 * dp, screenW - width - 8 * dp)
        val gap = 8f * dp
        // فوق منطقة الالتقاط إن توفرت مساحة، وإلا تحتها — حتى لا تُغطّى الترجمة المرئية الأصلية
        val top = if (c.anchor.top - h - gap > 24 * dp) c.anchor.top - h - gap else c.anchor.bottom + gap
        return Item(RectF(left, top, left + width, top + h), 0xE6101418.toInt(), layout, padX, padY)
    }

    private fun fit(
        text: String, width: Int, maxHeight: Int, maxPx: Float, minPx: Float,
        color: Int, rtl: Boolean, centered: Boolean,
    ): StaticLayout {
        fun build(size: Float): StaticLayout {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color }
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
                .setAlignment(if (centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
                .setIncludePad(false)
                .setLineSpacing(0f, 1f)
                .build()
        }
        var lo = minPx
        var hi = max(minPx, maxPx)
        var best = build(lo)
        if (best.height > maxHeight) return best // لا يتسع حتى بأصغر خط ← يتمدد الصندوق للأسفل
        repeat(6) {
            val mid = (lo + hi) / 2f
            val l = build(mid)
            if (l.height <= maxHeight) { best = l; lo = mid } else hi = mid
        }
        return best
    }

    override fun onDraw(canvas: Canvas) {
        val (ox, oy) = windowOrigin
        canvas.save()
        canvas.translate(-ox.toFloat(), -oy.toFloat())

        val frame = bubble?.let { RectF(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
        val radius = 18f * dp
        if (frame != null) {
            canvas.drawRoundRect(frame, radius, radius, glassFill)
            canvas.drawRoundRect(frame, radius, radius, frameShadow)
            canvas.drawRoundRect(frame, radius, radius, framePaint)
            val inner = RectF(frame).apply { inset(4 * dp, 4 * dp) }
            canvas.drawRoundRect(inner, radius - 4 * dp, radius - 4 * dp, glassHighlight)
            canvas.save()
            canvas.clipRect(frame)
        }
        for (it in items) drawItem(canvas, it, 6f * dp)
        if (frame != null) canvas.restore()
        captionItem?.let { drawItem(canvas, it, 14f * dp) }
        canvas.restore()
    }

    private fun drawItem(canvas: Canvas, it: Item, r: Float) {
        bgPaint.color = it.bg
        canvas.drawRoundRect(it.rect, r, r, bgPaint)
        canvas.save()
        val textTop = it.rect.top + it.padY + max(0f, (it.rect.height() - 2 * it.padY - it.layout.height) / 2f)
        canvas.translate(it.rect.left + it.padX, textTop)
        it.layout.draw(canvas)
        canvas.restore()
    }

    private fun luminance(c: Int): Float =
        (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
}
