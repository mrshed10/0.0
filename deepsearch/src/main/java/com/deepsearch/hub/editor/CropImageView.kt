package com.deepsearch.hub.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** عارض صورة مع إطار قص قابل للسحب من الزوايا والحواف، ومعاينة حيّة لفلاتر الألوان. */
class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val dp = resources.displayMetrics.density
    private val padding = 20 * dp
    private val touchSlop = 28 * dp
    private val minSize = 48 * dp
    private val handleLen = 18 * dp

    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint().apply { color = 0x99000000.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f * dp; color = Color.WHITE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f * dp; color = 0x80FFFFFF.toInt()
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4 * dp; color = Color.WHITE; strokeCap = Paint.Cap.ROUND
    }

    private val imageRect = RectF()
    private val crop = RectF()
    private var mode = 0
    private var lastX = 0f
    private var lastY = 0f

    var bitmap: Bitmap? = null
        set(value) {
            field = value
            layoutImage()
            resetCrop()
        }

    fun setColorMatrix(matrix: ColorMatrix?) {
        imagePaint.colorFilter = matrix?.let { ColorMatrixColorFilter(it) }
        invalidate()
    }

    fun resetCrop() {
        crop.set(imageRect)
        invalidate()
    }

    /** مستطيل القص بإحداثيات البكسل في الصورة الأصلية. */
    fun cropRectInBitmap(): Rect {
        val bmp = bitmap ?: return Rect()
        if (imageRect.width() <= 0f) return Rect(0, 0, bmp.width, bmp.height)
        val s = bmp.width / imageRect.width()
        val l = ((crop.left - imageRect.left) * s).roundToInt().coerceIn(0, bmp.width - 1)
        val t = ((crop.top - imageRect.top) * s).roundToInt().coerceIn(0, bmp.height - 1)
        val r = ((crop.right - imageRect.left) * s).roundToInt().coerceIn(l + 1, bmp.width)
        val b = ((crop.bottom - imageRect.top) * s).roundToInt().coerceIn(t + 1, bmp.height)
        return Rect(l, t, r, b)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutImage()
        resetCrop()
    }

    private fun layoutImage() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return
        val s = min((width - 2 * padding) / bmp.width, (height - 2 * padding) / bmp.height)
        val w = bmp.width * s
        val h = bmp.height * s
        imageRect.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        canvas.drawBitmap(bmp, null, imageRect, imagePaint)

        // تعتيم ما خارج منطقة القص
        canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, crop.top, dimPaint)
        canvas.drawRect(imageRect.left, crop.bottom, imageRect.right, imageRect.bottom, dimPaint)
        canvas.drawRect(imageRect.left, crop.top, crop.left, crop.bottom, dimPaint)
        canvas.drawRect(crop.right, crop.top, imageRect.right, crop.bottom, dimPaint)

        // شبكة الأثلاث
        for (i in 1..2) {
            val x = crop.left + crop.width() * i / 3
            val y = crop.top + crop.height() * i / 3
            canvas.drawLine(x, crop.top, x, crop.bottom, gridPaint)
            canvas.drawLine(crop.left, y, crop.right, y, gridPaint)
        }
        canvas.drawRect(crop, borderPaint)

        corner(canvas, crop.left, crop.top, 1, 1)
        corner(canvas, crop.right, crop.top, -1, 1)
        corner(canvas, crop.left, crop.bottom, 1, -1)
        corner(canvas, crop.right, crop.bottom, -1, -1)
    }

    private fun corner(c: Canvas, x: Float, y: Float, dx: Int, dy: Int) {
        c.drawLine(x, y, x + dx * handleLen, y, handlePaint)
        c.drawLine(x, y, x, y + dy * handleLen, handlePaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (bitmap == null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = hitTest(e.x, e.y)
                lastX = e.x
                lastY = e.y
                if (mode != 0) parent?.requestDisallowInterceptTouchEvent(true)
                return mode != 0
            }
            MotionEvent.ACTION_MOVE -> {
                drag(e.x - lastX, e.y - lastY)
                lastX = e.x
                lastY = e.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mode = 0
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun hitTest(x: Float, y: Float): Int {
        var f = 0
        val inY = y in (crop.top - touchSlop)..(crop.bottom + touchSlop)
        val inX = x in (crop.left - touchSlop)..(crop.right + touchSlop)
        val dl = abs(x - crop.left)
        val dr = abs(x - crop.right)
        val dt = abs(y - crop.top)
        val db = abs(y - crop.bottom)
        if (inY && min(dl, dr) < touchSlop) f = f or (if (dl <= dr) LEFT else RIGHT)
        if (inX && min(dt, db) < touchSlop) f = f or (if (dt <= db) TOP else BOTTOM)
        if (f == 0 && crop.contains(x, y)) f = MOVE
        return f
    }

    private fun drag(dx: Float, dy: Float) {
        if (mode == MOVE) {
            crop.offset(
                clamp(dx, imageRect.left - crop.left, imageRect.right - crop.right),
                clamp(dy, imageRect.top - crop.top, imageRect.bottom - crop.bottom),
            )
            return
        }
        if (mode and LEFT != 0) crop.left = clamp(crop.left + dx, imageRect.left, crop.right - minSize)
        if (mode and RIGHT != 0) crop.right = clamp(crop.right + dx, crop.left + minSize, imageRect.right)
        if (mode and TOP != 0) crop.top = clamp(crop.top + dy, imageRect.top, crop.bottom - minSize)
        if (mode and BOTTOM != 0) crop.bottom = clamp(crop.bottom + dy, crop.top + minSize, imageRect.bottom)
    }

    /** لا يرمي استثناء إذا كان lo > hi (صور صغيرة جداً). */
    private fun clamp(v: Float, lo: Float, hi: Float) = max(lo, min(v, hi))

    private companion object {
        const val LEFT = 1
        const val TOP = 2
        const val RIGHT = 4
        const val BOTTOM = 8
        const val MOVE = 16
    }
}
