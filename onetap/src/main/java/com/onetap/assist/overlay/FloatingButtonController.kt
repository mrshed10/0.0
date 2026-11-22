package com.onetap.assist.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.onetap.assist.Prefs
import com.onetap.assist.R
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Owns the floating button window and the "drag here to close" target.
 * Plain Views (no Compose) keep the overlay process light.
 */
class FloatingButtonController(
    service: Context,
    private val prefs: Prefs,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onDismiss: () -> Unit,
) {
    /** On Android 11+ overlay views must live in a window context. */
    private val ctx: Context = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val display = service.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        service.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    } else service

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val density = ctx.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())
    private fun dp(v: Number) = (v.toFloat() * density).roundToInt()

    private val button = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_assist_glyph)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = ctx.getString(R.string.cd_floating_button)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accentColor())
            setStroke(dp(1), 0x66FFFFFF)
        }
    }

    private val params = WindowManager.LayoutParams(
        0, 0,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private val closeTarget = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_close_glyph)
        val pad = dp(14)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(ctx, R.color.close_target))
        }
    }

    private val closeParams = WindowManager.LayoutParams(
        dp(CLOSE_SIZE_DP), dp(CLOSE_SIZE_DP),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = dp(56)
    }

    private var attached = false
    private var closeShown = false
    private var snapAnimator: ValueAnimator? = null

    val isShowing get() = attached

    init {
        button.setOnTouchListener(TouchHandler())
    }

    fun show() {
        if (attached) return
        applyAppearance(updateWindow = false)
        restorePosition()
        runCatching { wm.addView(button, params) }.onSuccess { attached = true }
    }

    fun hide() {
        snapAnimator?.cancel()
        hideCloseTarget()
        if (!attached) return
        runCatching { wm.removeViewImmediate(button) }
        attached = false
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        hide()
    }

    /** Size / opacity / snapping changed in settings. */
    fun applyAppearance(updateWindow: Boolean = true) {
        val size = dp(prefs.sizeDp)
        params.width = size
        params.height = size
        val pad = (size * 0.24f).roundToInt()
        button.setPadding(pad, pad, pad, pad)
        button.alpha = prefs.opacity
        if (updateWindow && attached) {
            restorePosition()
            runCatching { wm.updateViewLayout(button, params) }
        }
    }

    /** Rotation / resolution change: keep the same relative spot. */
    fun onScreenChanged() {
        if (!attached) return
        restorePosition()
        runCatching { wm.updateViewLayout(button, params) }
    }

    // ---- geometry ----------------------------------------------------------------------------

    private fun screenSize(): Point {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            Point().also {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealSize(it)
            }
        }
    }

    private fun area(): Positioning.Area {
        val screen = screenSize()
        var top = 0
        var bottom = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = wm.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            top = insets.top
            bottom = insets.bottom
        } else {
            val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) top = ctx.resources.getDimensionPixelSize(id)
        }
        return Positioning.area(screen.x, screen.y, top, bottom, params.width, dp(EDGE_MARGIN_DP))
    }

    private fun restorePosition() {
        val area = area()
        params.x = Positioning.fromFracX(prefs.posXFrac, area)
        params.y = Positioning.fromFracY(prefs.posYFrac, area)
        if (prefs.snapToEdge) params.x = Positioning.snapX(params.x, params.width, screenSize().x, area)
    }

    private fun savePosition() {
        val area = area()
        prefs.posXFrac = Positioning.toFracX(params.x, area)
        prefs.posYFrac = Positioning.toFracY(params.y, area)
    }

    private fun updateLayout() {
        if (attached) runCatching { wm.updateViewLayout(button, params) }
    }

    private fun settle() {
        val area = area()
        params.y = Positioning.clampY(params.y, area)
        val targetX = if (prefs.snapToEdge) {
            Positioning.snapX(params.x, params.width, screenSize().x, area)
        } else {
            Positioning.clampX(params.x, area)
        }
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                params.x = it.animatedValue as Int
                updateLayout()
            }
            start()
        }
        params.x = targetX // the persisted value is the final one
        savePosition()
    }

    // ---- close target ------------------------------------------------------------------------

    private fun showCloseTarget() {
        if (closeShown) return
        closeTarget.scaleX = 1f; closeTarget.scaleY = 1f
        runCatching { wm.addView(closeTarget, closeParams) }.onSuccess { closeShown = true }
    }

    private fun hideCloseTarget() {
        if (!closeShown) return
        runCatching { wm.removeViewImmediate(closeTarget) }
        closeShown = false
    }

    private fun isOverCloseTarget(): Boolean {
        if (!closeShown || !closeTarget.isAttachedToWindow) return false
        val a = IntArray(2).also { button.getLocationOnScreen(it) }
        val b = IntArray(2).also { closeTarget.getLocationOnScreen(it) }
        val dx = (a[0] + button.width / 2f) - (b[0] + closeTarget.width / 2f)
        val dy = (a[1] + button.height / 2f) - (b[1] + closeTarget.height / 2f)
        return hypot(dx, dy) < closeTarget.width
    }

    // ---- touch -------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private inner class TouchHandler : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressed = false
        private var overClose = false

        private val longPress = Runnable {
            longPressed = true
            if (prefs.haptics) button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress()
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    downRawX = e.rawX; downRawY = e.rawY
                    startX = params.x; startY = params.y
                    dragging = false; longPressed = false; overClose = false
                    button.alpha = 1f
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }

                MotionEvent.ACTION_MOVE -> {
                    if (longPressed) return true
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                        showCloseTarget()
                    }
                    if (dragging) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        updateLayout()
                        val over = isOverCloseTarget()
                        if (over != overClose) {
                            overClose = over
                            val s = if (over) 1.25f else 1f
                            closeTarget.animate().scaleX(s).scaleY(s).setDuration(120).start()
                            if (over && prefs.haptics) button.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    button.alpha = prefs.opacity
                    val wasOverClose = dragging && isOverCloseTarget()
                    hideCloseTarget()
                    when {
                        wasOverClose -> onDismiss()
                        dragging -> settle()
                        longPressed -> Unit
                        e.actionMasked == MotionEvent.ACTION_UP -> {
                            if (prefs.haptics) button.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onTap()
                        }
                    }
                    dragging = false
                }
            }
            return true
        }
    }

    private fun accentColor(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.getColor(ctx, android.R.color.system_accent1_600)
        } else {
            ContextCompat.getColor(ctx, R.color.brand)
        }

    companion object {
        private const val CLOSE_SIZE_DP = 56
        private const val EDGE_MARGIN_DP = 4
    }
}
