package com.tvtools.floatingmenu.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.tvtools.floatingmenu.R
import com.tvtools.floatingmenu.data.Prefs
import kotlin.math.hypot
import kotlin.math.max

/**
 * الزر العائم الصغير. نافذته غير قابلة للتركيز (FLAG_NOT_FOCUSABLE) حتى لا يسرق أزرار الريموت
 * من التطبيق الذي تشاهده. يُنقر أو يُسحب بالماوس، ويلتصق بأقرب حافة بعد السحب.
 */
@SuppressLint("InflateParams")
class FloatingBubble(
    context: Context,
    private val windowManager: WindowManager,
    private val prefs: Prefs,
    private val onClick: () -> Unit,
) {
    private val view: View = LayoutInflater.from(context).inflate(R.layout.overlay_bubble, null)
    private val sizePx = context.resources.getDimensionPixelSize(R.dimen.bubble_size)
    private val marginPx = context.resources.getDimensionPixelSize(R.dimen.bubble_margin)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val params = WindowManager.LayoutParams(
        sizePx,
        sizePx,
        OverlayWindows.type,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        title = "FloatingMenuBubble"
    }

    var isShowing = false
        private set

    init {
        view.alpha = IDLE_ALPHA
        view.setOnClickListener { onClick() }
        view.setOnHoverListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER -> fadeTo(v, 1f)
                MotionEvent.ACTION_HOVER_EXIT -> fadeTo(v, IDLE_ALPHA)
            }
            false
        }
        view.setOnTouchListener(DragListener())
    }

    /** يعيد false إذا رفض النظام إضافة النافذة (مثلاً سُحبت الصلاحية). */
    fun show(): Boolean {
        if (isShowing) return true
        applySavedPosition()
        return try {
            windowManager.addView(view, params)
            isShowing = true
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    fun hide() {
        if (!isShowing) return
        try {
            windowManager.removeView(view)
        } catch (e: RuntimeException) {
            // النافذة أزيلت مسبقاً
        }
        isShowing = false
    }

    /** هل الزر في النصف الأيسر من الشاشة؟ لتفتح القائمة في نفس الجهة. */
    fun isOnLeftSide(): Boolean {
        val screen = OverlayWindows.screenSize(windowManager)
        if (!isShowing) applySavedPosition()
        return params.x + sizePx / 2 < screen.x / 2
    }

    fun onScreenChanged() {
        if (!isShowing) return
        applySavedPosition()
        windowManager.updateViewLayout(view, params)
    }

    private fun applySavedPosition() {
        val screen = OverlayWindows.screenSize(windowManager)
        params.x = prefs.bubbleX.takeIf { it >= 0 } ?: (screen.x - sizePx - marginPx)
        params.y = prefs.bubbleY.takeIf { it >= 0 } ?: (screen.y * DEFAULT_Y_RATIO).toInt()
        clamp(screen)
    }

    private fun clamp(screen: Point) {
        params.x = params.x.coerceIn(0, max(0, screen.x - sizePx))
        params.y = params.y.coerceIn(0, max(0, screen.y - sizePx))
    }

    private fun fadeTo(v: View, alpha: Float) {
        v.animate().alpha(alpha).setDuration(150).start()
    }

    private inner class DragListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    v.isPressed = true
                    v.alpha = 1f
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && hypot(dx, dy) > touchSlop) {
                        dragging = true
                        v.isPressed = false
                    }
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        clamp(OverlayWindows.screenSize(windowManager))
                        windowManager.updateViewLayout(view, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    if (dragging) snapToEdge() else v.performClick()
                    fadeTo(v, IDLE_ALPHA)
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    if (dragging) snapToEdge()
                    fadeTo(v, IDLE_ALPHA)
                }
            }
            return true
        }

        private fun snapToEdge() {
            val screen = OverlayWindows.screenSize(windowManager)
            params.x = if (params.x + sizePx / 2 < screen.x / 2) marginPx else screen.x - sizePx - marginPx
            clamp(screen)
            if (isShowing) windowManager.updateViewLayout(view, params)
            prefs.bubbleX = params.x
            prefs.bubbleY = params.y
        }
    }

    private companion object {
        const val IDLE_ALPHA = 0.45f
        const val DEFAULT_Y_RATIO = 0.12f
    }
}
