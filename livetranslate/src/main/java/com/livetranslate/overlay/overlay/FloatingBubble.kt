package com.livetranslate.overlay.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.livetranslate.overlay.R
import com.livetranslate.overlay.capture.Box
import com.livetranslate.overlay.data.Prefs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * زر عائم صغير يلتصق بحافة الشاشة الجانبية ويختفي جزئياً خلفها حتى لا يعيق التصفح:
 * - نقرة: تشغيل/إيقاف مؤقت للترجمة (أول نقرة تبدأ الجلسة).
 * - ضغطة مطوّلة: إنهاء جلسة الترجمة (أو إخفاء الزر إن لم تكن هناك جلسة).
 * - سحب: يتحرك بحرية ثم ينجذب لأقرب حافة جانبية عند الإفلات.
 * بعد ثوانٍ من عدم اللمس يصبح شبه شفاف.
 */
class FloatingBubble(
    private val context: Context,
    private val wm: WindowManager,
    private val windowType: Int,
    private val prefs: Prefs,
    private val listener: Listener,
) {
    interface Listener {
        fun onBubbleTap()
        fun onBubbleLongPress()
        fun onBubbleMoved(bounds: Box)
    }

    enum class State { IDLE, ACTIVE, PAUSED, BUSY }

    private val density = context.resources.displayMetrics.density
    private val size = (44 * density).roundToInt()
    /** الجزء المخفي خلف الحافة. */
    private val hidden = (size * 0.38f).roundToInt()
    private val main = Handler(Looper.getMainLooper())
    private val view = BubbleView(context)
    private var snapAnimator: ValueAnimator? = null

    private val params = WindowManager.LayoutParams(
        size, size, windowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        windowAnimations = 0
        title = "LiveTranslateBubble"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    val isShown get() = view.isAttachedToWindow

    fun show() {
        if (view.isAttachedToWindow) return
        val m = ScreenMetrics.of(context)
        val y = prefs.bubbleY.takeIf { it >= 0 } ?: (m.height * 0.4f).roundToInt()
        params.x = edgeX(prefs.bubbleOnRight, m.width)
        params.y = y.coerceIn(0, m.height - size)
        runCatching { wm.addView(view, params) }
        scheduleFade()
        notifyMoved()
    }

    fun hide() {
        snapAnimator?.cancel()
        main.removeCallbacksAndMessages(null)
        if (view.isAttachedToWindow) runCatching { wm.removeViewImmediate(view) }
    }

    fun setState(state: State) {
        view.state = state
        view.invalidate()
    }

    /** إعادة الالتصاق بالحافة بعد تدوير الشاشة. */
    fun onScreenChanged() {
        if (!view.isAttachedToWindow) return
        val m = ScreenMetrics.of(context)
        params.x = edgeX(prefs.bubbleOnRight, m.width)
        params.y = params.y.coerceIn(0, (m.height - size).coerceAtLeast(0))
        runCatching { wm.updateViewLayout(view, params) }
        notifyMoved()
    }

    /** حدود الجزء الظاهر بإحداثيات الشاشة (لاستبعاده من القراءة وكشف الحركة). */
    fun bounds(): Box? {
        if (!view.isAttachedToWindow) return null
        val m = ScreenMetrics.of(context)
        return Box(params.x.coerceAtLeast(0), params.y, (params.x + size).coerceAtMost(m.width), params.y + size)
    }

    private fun edgeX(right: Boolean, screenWidth: Int) = if (right) screenWidth - size + hidden else -hidden

    private fun notifyMoved() {
        bounds()?.let(listener::onBubbleMoved)
    }

    private fun scheduleFade() {
        main.removeCallbacksAndMessages(FADE_TOKEN)
        view.animate().alpha(1f).setDuration(120).start()
        main.postAtTime({ view.animate().alpha(0.45f).setDuration(400).start() }, FADE_TOKEN,
            android.os.SystemClock.uptimeMillis() + 2500)
    }

    private fun snapToEdge() {
        val m = ScreenMetrics.of(context)
        val right = params.x + size / 2 > m.width / 2
        val target = edgeX(right, m.width)
        prefs.bubbleOnRight = right
        prefs.bubbleY = params.y
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(params.x, target).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                params.x = it.animatedValue as Int
                if (view.isAttachedToWindow) runCatching { wm.updateViewLayout(view, params) }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = notifyMoved()
            })
            start()
        }
    }

    @SuppressLint("ViewConstructor")
    private inner class BubbleView(context: Context) : View(context) {
        var state = State.IDLE
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        private val icon = ContextCompat.getDrawable(context, R.drawable.ic_stat_translate)!!.mutate()
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressed = false
        private val longPress = Runnable {
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener.onBubbleLongPress()
        }

        override fun onDraw(canvas: Canvas) {
            val r = width / 2f
            val (bg, fg) = when (state) {
                State.ACTIVE -> 0xF21A73E8.toInt() to Color.WHITE
                State.BUSY -> 0xF21A73E8.toInt() to Color.WHITE
                State.PAUSED -> 0xE6FFFFFF.toInt() to 0xFF1A73E8.toInt()
                State.IDLE -> 0xCC202124.toInt() to Color.WHITE
            }
            fill.color = bg
            canvas.drawCircle(r, r, r - 1f, fill)
            if (state == State.PAUSED || state == State.BUSY) {
                ring.color = if (state == State.BUSY) 0xFFFFFFFF.toInt() else 0xFF1A73E8.toInt()
                canvas.drawCircle(r, r, r - ring.strokeWidth, ring)
            }
            val inset = (width * 0.27f).roundToInt()
            icon.setTint(fg)
            icon.setBounds(inset, inset, width - inset, height - inset)
            icon.draw(canvas)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    longPressed = false
                    alpha = 1f
                    main.removeCallbacksAndMessages(FADE_TOKEN)
                    main.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && hypot(dx, dy) > slop && !longPressed) {
                        dragging = true
                        main.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        val m = ScreenMetrics.of(context)
                        params.x = (startX + dx).roundToInt().coerceIn(-hidden, m.width - size + hidden)
                        params.y = (startY + dy).roundToInt().coerceIn(0, m.height - size)
                        runCatching { wm.updateViewLayout(this, params) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(longPress)
                    if (dragging) {
                        snapToEdge()
                    } else if (!longPressed && event.actionMasked == MotionEvent.ACTION_UP) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        listener.onBubbleTap()
                    }
                    dragging = false
                    scheduleFade()
                }
            }
            return true
        }
    }

    private companion object {
        val FADE_TOKEN = Any()
    }
}
