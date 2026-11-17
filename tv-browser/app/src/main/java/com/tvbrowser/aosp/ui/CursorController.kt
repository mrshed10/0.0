package com.tvbrowser.aosp.ui

import android.os.SystemClock
import android.view.Choreographer
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.tvbrowser.aosp.util.Prefs
import org.mozilla.geckoview.GeckoSession

/**
 * Virtual mouse driven by the remote's D-pad.
 *
 *  - Arrows move the pointer; holding accelerates smoothly (frame-based, not key-repeat based,
 *    so it feels the same on remotes with slow repeat rates).
 *  - OK / Enter = left click (press and release are forwarded separately → drag & long-press work).
 *  - Pushing against an edge scrolls the element under the pointer with real mouse-wheel events
 *    (works for inner scrollable panels, not just the page).
 *  - Pointer movement sends hover events, so desktop drop-down menus open as on a PC.
 *  - Pushing UP at the top edge while the page is scrolled to the top calls [onTopEdge]
 *    (MainActivity moves focus to the toolbar).
 */
class CursorController(
    private val target: View,
    private val cursor: CursorView,
    private val prefs: Prefs,
    private val session: () -> GeckoSession?,
    private val pageScrollY: () -> Int = { 0 },
    private val onTopEdge: () -> Unit = {},
) : Choreographer.FrameCallback {

    private val pressedDirs = LinkedHashSet<Int>()
    private var holdStart = 0L
    private var lastFrameNanos = 0L
    private var running = false
    private var lastWheel = 0L
    private var downTime = 0L
    private var buttonDown = false
    private val density = target.resources.displayMetrics.density

    var enabled = true
        set(v) {
            field = v
            cursor.visibility = if (v) View.VISIBLE else View.GONE
            if (!v) {
                pressedDirs.clear()
                if (buttonDown) release()
            }
        }

    fun handleKey(event: KeyEvent): Boolean {
        if (!enabled) return false
        val code = event.keyCode
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) {
                        if (code == KeyEvent.KEYCODE_DPAD_UP && cursor.cursorY <= edge() && pageScrollY() <= 0) {
                            onTopEdge()
                            return true
                        }
                        if (pressedDirs.isEmpty()) holdStart = SystemClock.uptimeMillis()
                        pressedDirs += code
                        step(code, 6f * density) // immediate small nudge = precise positioning
                        start()
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    pressedDirs -= code
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) press()
                else if (event.action == KeyEvent.ACTION_UP) release()
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) wheel(0f, 5f)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) wheel(0f, -5f)
                return true
            }
        }
        return false
    }

    private fun edge() = 2f * density

    private fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (pressedDirs.isEmpty() || !enabled) {
            running = false
            return
        }
        val dt = if (lastFrameNanos == 0L) 1f / 60f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastFrameNanos = frameTimeNanos
        val held = SystemClock.uptimeMillis() - holdStart
        if (held > 120) {
            // px per second: starts slow for precision, ramps up to cross a 1080p screen in ~1s.
            val base = target.width * (0.10f + 0.05f * prefs.cursorSpeed)
            val accel = 1f + (held / 400f).coerceAtMost(4f)
            val dist = base * accel * dt
            pressedDirs.forEach { step(it, dist) }
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun step(code: Int, dist: Float) {
        var dx = 0f
        var dy = 0f
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> dy = -dist
            KeyEvent.KEYCODE_DPAD_DOWN -> dy = dist
            KeyEvent.KEYCODE_DPAD_LEFT -> dx = -dist
            KeyEvent.KEYCODE_DPAD_RIGHT -> dx = dist
        }
        val nx = cursor.cursorX + dx
        val ny = cursor.cursorY + dy
        cursor.moveTo(nx, ny)

        // Pushing against an edge = scroll (wheel ticks, throttled to ~16/s).
        val maxX = target.width - 1f
        val maxY = target.height - 1f
        val now = SystemClock.uptimeMillis()
        if (now - lastWheel > 60) {
            val wheelY = when {
                ny < 0f -> 1f
                ny > maxY -> -1f
                else -> 0f
            }
            val wheelX = when {
                nx < 0f -> -1f
                nx > maxX -> 1f
                else -> 0f
            }
            if (wheelX != 0f || wheelY != 0f) {
                lastWheel = now
                wheel(wheelX, wheelY)
            }
        }
        sendMouse(if (buttonDown) MotionEvent.ACTION_MOVE else MotionEvent.ACTION_HOVER_MOVE)
    }

    private fun press() {
        buttonDown = true
        downTime = SystemClock.uptimeMillis()
        cursor.clicking = true
        target.requestFocus()
        sendMouse(MotionEvent.ACTION_DOWN)
    }

    private fun release() {
        if (!buttonDown) return
        buttonDown = false
        cursor.clicking = false
        sendMouse(MotionEvent.ACTION_UP)
    }

    /** Mouse wheel at the pointer position; vertical > 0 scrolls up (Android convention). */
    fun wheel(horizontal: Float, vertical: Float) {
        val s = session() ?: return
        val ev = obtain(MotionEvent.ACTION_SCROLL, 0, hScroll = horizontal, vScroll = vertical)
        s.panZoomController.onMotionEvent(ev)
        ev.recycle()
    }

    private fun sendMouse(action: Int) {
        val s = session() ?: return
        if (prefs.clickMode == Prefs.CLICK_TOUCH) {
            if (action == MotionEvent.ACTION_HOVER_MOVE) return
            val ev = obtain(action, 0, touch = true)
            s.panZoomController.onTouchEvent(ev)
            ev.recycle()
            return
        }
        val buttons = if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            MotionEvent.BUTTON_PRIMARY
        } else 0
        val ev = obtain(action, buttons)
        if (action == MotionEvent.ACTION_HOVER_MOVE) s.panZoomController.onMotionEvent(ev)
        else s.panZoomController.onMouseEvent(ev)
        ev.recycle()
    }

    private fun obtain(
        action: Int,
        buttonState: Int,
        hScroll: Float = 0f,
        vScroll: Float = 0f,
        touch: Boolean = false,
    ): MotionEvent {
        val now = SystemClock.uptimeMillis()
        val props = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = if (touch) MotionEvent.TOOL_TYPE_FINGER else MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().apply {
            x = cursor.cursorX
            y = cursor.cursorY
            pressure = 1f
            size = 1f
            setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll)
            setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
        }
        val source = if (touch) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_MOUSE
        return MotionEvent.obtain(
            if (downTime == 0L) now else downTime, now, action, 1,
            arrayOf(props), arrayOf(coords), 0, buttonState, 1f, 1f, 0, 0, source, 0,
        )
    }
}
