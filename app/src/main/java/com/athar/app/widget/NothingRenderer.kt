package com.athar.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** What a Nothing-style widget shows. */
data class NothingSpec(
    /** Small label at the top, after a red dot. */
    val top: String?,
    /** Big dot-matrix text (time, number, percent). */
    val big: String,
    /** Indices in [big] drawn in the accent colour (e.g. the colon). */
    val bigAccent: Set<Int> = emptySet(),
    /** Small label at the bottom. */
    val bottom: String?,
    /** 0..1: circular glyph ring of dots around the big text. */
    val ring: Float? = null,
    /** 0..1: row of dots above the bottom label. */
    val bar: Float? = null,
)

/**
 * Nothing-inspired design: deep black, white dots, one red accent.
 * Text that the dot font cannot draw (e.g. Arabic) falls back to [fallback].
 */
object NothingRenderer {
    private fun alpha(color: Int, a: Float) = Color.argb((255 * a).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    fun render(w: Int, h: Int, spec: NothingSpec, text: Int, accent: Int, fallback: Typeface?): Bitmap {
        val bmp = Bitmap.createBitmap(max(8, w), max(8, h), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val ghost = alpha(text, 0.07f)
        if (spec.ring != null) {
            drawRing(canvas, w, h, spec, text, accent, ghost, fallback)
            return bmp
        }
        val labelH = h * 0.13f
        var top = 0f
        spec.top?.let {
            drawLabel(canvas, it, 0f, 0f, w.toFloat(), labelH, text, accent, fallback, bullet = true)
            top = labelH * 1.5f
        }
        var bottom = h.toFloat()
        spec.bottom?.let {
            drawLabel(canvas, it, 0f, h - labelH, w.toFloat(), labelH, alpha(text, 0.75f), accent, fallback, bullet = false)
            bottom = h - labelH * 1.5f
        }
        spec.bar?.let { p ->
            val barH = labelH * 0.6f
            drawBar(canvas, 0f, bottom - barH, w.toFloat(), barH, p, text, accent, ghost)
            bottom -= barH * 1.8f
        }
        drawBig(canvas, spec.big, spec.bigAccent, 0f, top, w.toFloat(), bottom - top, text, accent, ghost, fallback)
        return bmp
    }

    private fun drawBig(
        canvas: Canvas, s: String, accentIdx: Set<Int>, x: Float, y: Float, w: Float, h: Float,
        text: Int, accent: Int, ghost: Int, fallback: Typeface?,
    ) {
        if (h <= 4f) return
        if (DotMatrix.supports(s)) {
            val cell = min(w / DotMatrix.columns(s), h / DotMatrix.ROWS)
            val tw = DotMatrix.columns(s) * cell
            val th = DotMatrix.ROWS * cell
            DotMatrix.draw(canvas, s, x + (w - tw) / 2, y + (h - th) / 2, cell, text, accent, accentIdx, ghost)
        } else {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = text
                typeface = fallback ?: Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
                textSize = h * 0.6f
                while (measureText(s) > w * 0.95f && textSize > 8f) textSize *= 0.9f
            }
            canvas.drawText(s, x + w / 2, y + h / 2 - (p.descent() + p.ascent()) / 2, p)
        }
    }

    private fun drawLabel(
        canvas: Canvas, s: String, x: Float, y: Float, w: Float, h: Float,
        color: Int, accent: Int, fallback: Typeface?, bullet: Boolean,
    ) {
        var cx = x
        if (bullet) {
            val r = h * 0.22f
            canvas.drawCircle(cx + r, y + h / 2, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = accent })
            cx += r * 2 + h * 0.4f
        }
        val upper = s.uppercase()
        if (DotMatrix.supports(upper)) {
            val cell = min(h / DotMatrix.ROWS, (w - (cx - x)) / DotMatrix.columns(upper).coerceAtLeast(1))
            DotMatrix.draw(canvas, upper, cx, y + (h - DotMatrix.ROWS * cell) / 2, cell, color)
        } else {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                typeface = fallback ?: Typeface.DEFAULT
                textSize = h * 0.95f
                while (measureText(s) > w - (cx - x) && textSize > 8f) textSize *= 0.9f
            }
            canvas.drawText(s, cx, y + h / 2 - (p.descent() + p.ascent()) / 2, p)
        }
    }

    private fun drawBar(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, progress: Float, text: Int, accent: Int, ghost: Int) {
        val count = max(8, (w / (h * 1.6f)).toInt())
        val step = w / count
        val lit = (progress.coerceIn(0f, 1f) * count).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until count) {
            paint.color = when {
                i < lit -> text
                i == lit -> accent
                else -> alpha(text, 0.18f)
            }
            canvas.drawCircle(x + i * step + step / 2, y + h / 2, h * 0.38f, paint)
        }
    }

    private fun drawRing(canvas: Canvas, w: Int, h: Int, spec: NothingSpec, text: Int, accent: Int, ghost: Int, fallback: Typeface?) {
        val cx = w / 2f
        val cy = h / 2f
        val radius = min(w, h) / 2f * 0.9f
        val dots = 60
        val dotR = radius * 0.035f
        val lit = ((spec.ring ?: 0f).coerceIn(0f, 1f) * dots).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until dots) {
            val a = Math.toRadians(i * 360.0 / dots - 90)
            paint.color = when {
                i < lit -> text
                i == lit -> accent
                else -> alpha(text, 0.18f)
            }
            canvas.drawCircle(cx + (radius * cos(a)).toFloat(), cy + (radius * sin(a)).toFloat(), if (i % 5 == 0) dotR * 1.3f else dotR, paint)
        }
        val inner = radius * 1.25f
        val labelH = inner * 0.16f
        spec.top?.let { drawLabel(canvas, it, cx - inner / 2, cy - inner * 0.42f, inner, labelH, alpha(text, 0.8f), accent, fallback, bullet = true) }
        drawBig(canvas, spec.big, spec.bigAccent, cx - inner / 2, cy - inner * 0.2f, inner, inner * 0.4f, text, accent, ghost, fallback)
        spec.bottom?.let { drawLabel(canvas, it, cx - inner / 2, cy + inner * 0.26f, inner, labelH, alpha(text, 0.7f), accent, fallback, bullet = false) }
    }
}
