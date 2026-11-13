package com.livetranslate.overlay.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.RoundedCorner
import android.view.View
import android.view.animation.LinearInterpolator
import com.livetranslate.overlay.translate.TextPatch
import kotlin.math.max
import kotlin.math.min

/**
 * طبقة شفافة بملء الشاشة لا تستقبل اللمس (FLAG_NOT_TOUCHABLE):
 * 1) الإطار المضيء حول حواف الشاشة.
 * 2) النصوص المترجمة مرسومة فوق النص الأصلي بنفس الموضع والزاوية ولون خلفية مأخوذ من الصورة.
 *
 * الطبقتان في نافذة واحدة عمداً: Android 12+ يحجب اللمس إذا تجاوز مجموع شفافية
 * نوافذ التطبيق نفسه فوق نقطة اللمس 0.8، ونافذة واحدة بشفافية 0.8 تُبقي اللمس يمر للتطبيق تحتها.
 */
@SuppressLint("ViewConstructor")
class StageView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val glow = EdgeGlowPainter(density)
    private val screenRect = RectF()
    private val location = IntArray(2)
    private val boxRect = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nudgePaint = Paint().apply { color = 0x01000000 }
    private var nudgeToggle = false

    private class Prepared(val patch: TextPatch, val layout: StaticLayout)
    private var prepared: List<Prepared> = emptyList()

    private var glowRotation = 0f
    private var glowIntensity = 0f
    private var patchAlpha = 0f
    private var glowTarget = 0f
    private var patchesTarget = 0f

    var glowAnimated = true
        set(value) {
            field = value
            updateRotationAnimator()
        }

    private val rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 6000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            glowRotation = it.animatedValue as Float
            invalidate()
        }
    }

    private var glowFade: ValueAnimator? = null
    private var patchFade: ValueAnimator? = null

    fun setGlowVisible(visible: Boolean) {
        val target = if (visible) 1f else 0f
        if (target == glowTarget) return
        glowTarget = target
        glowFade?.cancel()
        glowFade = ValueAnimator.ofFloat(glowIntensity, target).apply {
            duration = 350
            addUpdateListener {
                glowIntensity = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        updateRotationAnimator()
    }

    /** يخفي/يُظهر الترجمة. الإخفاء فوري حتى لا تظهر في الإطار الملتقط التالي. */
    fun setPatchesVisible(visible: Boolean) {
        val target = if (visible) 1f else 0f
        patchesTarget = target
        patchFade?.cancel()
        if (!visible) {
            patchAlpha = 0f
            invalidate()
            return
        }
        patchFade = ValueAnimator.ofFloat(patchAlpha, 1f).apply {
            duration = 160
            addUpdateListener {
                patchAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setPatches(patches: List<TextPatch>) {
        prepared = patches.map { Prepared(it, fitText(it)) }
        invalidate()
    }

    fun clearPatches() {
        prepared = emptyList()
        setPatchesVisible(false)
    }

    /**
     * يغيّر بكسلاً واحداً شبه شفاف لإجبار النظام على إنتاج إطار جديد
     * عندما تكون الشاشة ساكنة تماماً ونحتاج لقطة.
     */
    fun nudge() {
        nudgeToggle = !nudgeToggle
        invalidate()
    }

    private fun updateRotationAnimator() {
        val shouldRun = glowAnimated && glowTarget > 0f && isAttachedToWindow
        if (shouldRun && !rotationAnimator.isStarted) rotationAnimator.start()
        if (!shouldRun && rotationAnimator.isStarted) rotationAnimator.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateRotationAnimator()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val m = ScreenMetrics.of(context)
        screenRect.set(0f, 0f, m.width.toFloat(), m.height.toFloat())
    }

    override fun onDetachedFromWindow() {
        rotationAnimator.cancel()
        glowFade?.cancel()
        patchFade?.cancel()
        super.onDetachedFromWindow()
    }

    private fun cornerRadius(): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val corner = rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
            if (corner != null && corner.radius > 0) return corner.radius.toFloat()
        }
        return 28f * density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // نرسم بإحداثيات الشاشة الكاملة حتى لو لم تبدأ النافذة من (0,0)
        getLocationOnScreen(location)
        canvas.save()
        canvas.translate(-location[0].toFloat(), -location[1].toFloat())

        if (patchAlpha > 0f) drawPatches(canvas)

        if (glowIntensity > 0f) {
            glow.draw(canvas, screenRect, cornerRadius(), glowRotation, glowIntensity)
        }
        canvas.restore()

        if (nudgeToggle) canvas.drawPoint(0f, 0f, nudgePaint)
    }

    private fun drawPatches(canvas: Canvas) {
        val pad = 3f * density
        for (p in prepared) {
            val patch = p.patch
            val w = patch.width
            val h = max(patch.height, p.layout.height.toFloat())
            canvas.save()
            canvas.rotate(patch.angle, patch.centerX, patch.centerY)
            boxRect.set(
                patch.centerX - w / 2f - pad, patch.centerY - h / 2f - pad,
                patch.centerX + w / 2f + pad, patch.centerY + h / 2f + pad,
            )
            bgPaint.color = patch.background
            bgPaint.alpha = (255 * patchAlpha).toInt()
            canvas.drawRoundRect(boxRect, 6f * density, 6f * density, bgPaint)

            p.layout.paint.alpha = (255 * patchAlpha).toInt()
            canvas.translate(patch.centerX - w / 2f, patch.centerY - p.layout.height / 2f)
            p.layout.draw(canvas)
            canvas.restore()
        }
    }

    /** أكبر حجم خط يجعل الترجمة تتسع داخل صندوق النص الأصلي. */
    private fun fitText(patch: TextPatch): StaticLayout {
        val width = max(1, patch.width.toInt())
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = patch.textColor }
        val minSize = 9f * density
        val maxSize = max(minSize, min(patch.height / max(1, patch.lineCount) * 0.8f, 40f * density))

        fun build(size: Float): StaticLayout {
            paint.textSize = size
            return StaticLayout.Builder.obtain(patch.translated, 0, patch.translated.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
        }

        var lo = minSize
        var hi = maxSize
        repeat(7) {
            val mid = (lo + hi) / 2f
            if (build(mid).height <= patch.height) lo = mid else hi = mid
        }
        return build(lo)
    }
}
