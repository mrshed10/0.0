package com.livetranslate.overlay.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.hypot

/**
 * حاوية جذر النافذة العائمة:
 * - تسمح بسحب النافذة من أي نقطة فيها؛ اللمسة تبقى "نقرة" للأزرار ما لم تتجاوز مسافة touchSlop.
 * - تعتمد على rawX/rawY (إحداثيات الشاشة) لأن النافذة نفسها تتحرك أثناء السحب.
 * - تستقبل ACTION_OUTSIDE (بفضل FLAG_WATCH_OUTSIDE_TOUCH) لمعرفة أن المستخدم بدأ يلمس/يمرّر
 *   التطبيق الموجود تحت النافذة.
 */
@SuppressLint("ViewConstructor")
class DraggableFrame(
    context: Context,
    private val onDrag: (dx: Float, dy: Float) -> Unit,
    private val onDragEnd: () -> Unit,
    private val onOutsideTouch: () -> Unit,
) : FrameLayout(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> startGesture(ev)
            MotionEvent.ACTION_MOVE -> if (!dragging && passedSlop(ev)) beginDrag(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> startGesture(ev)
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && passedSlop(ev)) beginDrag(ev)
                if (dragging) {
                    onDrag(ev.rawX - lastX, ev.rawY - lastY)
                    lastX = ev.rawX
                    lastY = ev.rawY
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) onDragEnd()
                dragging = false
            }
        }
        return true
    }

    private fun startGesture(ev: MotionEvent) {
        downX = ev.rawX
        downY = ev.rawY
        lastX = ev.rawX
        lastY = ev.rawY
        dragging = false
    }

    private fun passedSlop(ev: MotionEvent) = hypot(ev.rawX - downX, ev.rawY - downY) > touchSlop

    private fun beginDrag(ev: MotionEvent) {
        dragging = true
        lastX = ev.rawX
        lastY = ev.rawY
        parent?.requestDisallowInterceptTouchEvent(true)
    }
}
