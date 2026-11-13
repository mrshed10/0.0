package com.livetranslate.overlay.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.View
import com.livetranslate.overlay.capture.Box
import com.livetranslate.overlay.translate.TextPatch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * طبقة شفافة تماماً بملء الشاشة لا تستقبل اللمس، بلا أي إطار أو حواف أو خلفية.
 * لا ترسم شيئاً سوى النصوص المترجمة فوق النص الأصلي مباشرةً: صندوق بلون خلفية النص
 * الأصلي (مأخوذ من الصورة) يغطيه بالكامل، ثم الترجمة بنفس الموضع والزاوية.
 */
@SuppressLint("ViewConstructor")
class StageView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val location = IntArray(2)
    private val boxRect = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nudgePaint = Paint().apply { color = 0x01000000 }
    private var nudgeToggle = false
    private val typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private class Prepared(val patch: TextPatch, val layout: StaticLayout, val drawnHeight: Float)

    private var prepared: List<Prepared> = emptyList()
    private var boxes: List<Box> = emptyList()
    private var visible = false

    /** المناطق التي تغطيها الترجمة الظاهرة الآن (بإحداثيات الشاشة)، لاستبعادها من كشف الحركة. */
    fun visibleBoxes(): List<Box> = if (visible) boxes else emptyList()

    /** إظهار/إخفاء فوري (بلا حركة) حتى لا يتأخر ظهور الترجمة ولا تظهر في اللقطة التالية. */
    fun setPatchesVisible(show: Boolean) {
        if (visible == show) return
        visible = show
        invalidate()
    }

    fun setPatches(patches: List<TextPatch>) {
        prepared = patches.map { p ->
            val layout = fitText(p)
            Prepared(p, layout, max(p.height, layout.height.toFloat()))
        }
        boxes = prepared.map { boundsOf(it) }
        invalidate()
    }

    fun clearPatches() {
        prepared = emptyList()
        boxes = emptyList()
        visible = false
        invalidate()
    }

    /**
     * يغيّر بكسلاً واحداً شبه شفاف لإجبار النظام على إنتاج إطار جديد
     * عندما تكون الشاشة ساكنة ونحتاج لقطة فوراً.
     */
    fun nudge() {
        nudgeToggle = !nudgeToggle
        invalidate()
    }

    private fun pad() = 2f * density

    private fun boundsOf(p: Prepared): Box {
        val patch = p.patch
        val hw = patch.width / 2f + pad()
        val hh = p.drawnHeight / 2f + pad()
        val rad = Math.toRadians(patch.angle.toDouble())
        val c = abs(cos(rad)).toFloat()
        val s = abs(sin(rad)).toFloat()
        val ex = hw * c + hh * s
        val ey = hw * s + hh * c
        return Box(
            (patch.centerX - ex).toInt(), (patch.centerY - ey).toInt(),
            (patch.centerX + ex).toInt() + 1, (patch.centerY + ey).toInt() + 1,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visible && prepared.isNotEmpty()) {
            // نرسم بإحداثيات الشاشة الكاملة حتى لو لم تبدأ النافذة من (0,0)
            getLocationOnScreen(location)
            canvas.save()
            canvas.translate(-location[0].toFloat(), -location[1].toFloat())
            drawPatches(canvas)
            canvas.restore()
        }
        if (nudgeToggle) canvas.drawPoint(0f, 0f, nudgePaint)
    }

    private fun drawPatches(canvas: Canvas) {
        val pad = pad()
        val radius = 4f * density
        for (p in prepared) {
            val patch = p.patch
            val w = patch.width
            val h = p.drawnHeight
            canvas.save()
            canvas.rotate(patch.angle, patch.centerX, patch.centerY)
            boxRect.set(
                patch.centerX - w / 2f - pad, patch.centerY - h / 2f - pad,
                patch.centerX + w / 2f + pad, patch.centerY + h / 2f + pad,
            )
            bgPaint.color = patch.background // معتم بالكامل: يخفي النص الأصلي
            canvas.drawRoundRect(boxRect, radius, radius, bgPaint)
            canvas.translate(patch.centerX - w / 2f, patch.centerY - p.layout.height / 2f)
            p.layout.draw(canvas)
            canvas.restore()
        }
    }

    /** أكبر حجم خط يجعل الترجمة تتسع داخل صندوق النص الأصلي. */
    private fun fitText(patch: TextPatch): StaticLayout {
        val width = max(1, patch.width.toInt())
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = patch.textColor
            typeface = this@StageView.typeface
        }
        val minSize = 8f * density
        val maxSize = max(minSize, min(patch.height / max(1, patch.lineCount) * 0.85f, 40f * density))

        fun build(size: Float): StaticLayout {
            paint.textSize = size
            return StaticLayout.Builder.obtain(patch.translated, 0, patch.translated.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setLineSpacing(0f, 0.95f)
                .setIncludePad(false)
                .build()
        }

        var lo = minSize
        var hi = maxSize
        if (build(hi).height <= patch.height) return build(hi)
        repeat(6) {
            val mid = (lo + hi) / 2f
            if (build(mid).height <= patch.height) lo = mid else hi = mid
        }
        return build(lo)
    }
}
