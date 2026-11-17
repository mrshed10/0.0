package com.tvbrowser.aosp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/** Draws the mouse pointer over the page. Never takes focus or touches. */
class CursorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var cursorX = 0f
        private set
    var cursorY = 0f
        private set
    var pressed = false
        set(v) {
            field = v
            invalidate()
        }
    private var initialized = false

    private val scale = resources.displayMetrics.density * 1.4f
    private val arrow = Path().apply {
        moveTo(0f, 0f)
        lineTo(0f, 17f)
        lineTo(4.5f, 13f)
        lineTo(7.5f, 20f)
        lineTo(10f, 19f)
        lineTo(7f, 12.5f)
        lineTo(13f, 12.5f)
        close()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.4f; strokeJoin = Paint.Join.ROUND
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x8842A5F5.toInt(); style = Paint.Style.FILL
    }

    init {
        isFocusable = false
        isClickable = false
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (!initialized && w > 0 && h > 0) {
            cursorX = w / 2f
            cursorY = h / 2f
            initialized = true
        } else {
            moveTo(cursorX, cursorY)
        }
    }

    fun moveTo(x: Float, y: Float) {
        cursorX = x.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
        cursorY = y.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (pressed) canvas.drawCircle(cursorX, cursorY, 14f * scale / 1.4f, ring)
        canvas.save()
        canvas.translate(cursorX, cursorY)
        canvas.scale(scale, scale)
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, stroke)
        canvas.restore()
    }
}
