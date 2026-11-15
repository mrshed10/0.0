package com.athar.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Bitmap drawings for widgets that need precise layout or custom fonts
 * (widgets cannot draw these themselves). Sizes are in pixels.
 */
object WidgetRenderers {
    private fun bitmap(w: Int, h: Int) = Bitmap.createBitmap(max(8, w), max(8, h), Bitmap.Config.ARGB_8888)

    private fun withAlpha(color: Int, alpha: Float) = Color.argb((Color.alpha(color) * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    /** Largest text size (≤ [maxSize]) that fits [text] in [w]×[h]. */
    private fun fit(paint: TextPaint, text: String, w: Float, h: Float, maxSize: Float): Float {
        var size = maxSize
        while (size > 6f) {
            paint.textSize = size
            val fm = paint.fontMetrics
            if (paint.measureText(text) <= w && (fm.descent - fm.ascent) <= h) return size
            size *= 0.92f
        }
        return size
    }

    /** [filled] of [total] dots (days of the year or month) in a grid that fills the box. */
    fun dots(w: Int, h: Int, filled: Int, total: Int, on: Int): Bitmap {
        val bmp = bitmap(w, h)
        val canvas = Canvas(bmp)
        val cols = max(1, ceil(sqrt(total * w.toFloat() / h)).toInt())
        val rows = ceil(total / cols.toFloat()).toInt()
        val cellW = w.toFloat() / cols
        val cellH = h.toFloat() / rows
        val r = min(cellW, cellH) * 0.30f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until total) {
            val cx = (i % cols) * cellW + cellW / 2
            val cy = (i / cols) * cellH + cellH / 2
            when {
                i < filled -> { paint.style = Paint.Style.FILL; paint.color = on }
                i == filled -> { paint.style = Paint.Style.FILL; paint.color = withAlpha(on, 0.55f) }
                else -> { paint.style = Paint.Style.FILL; paint.color = withAlpha(on, 0.18f) }
            }
            canvas.drawCircle(cx, cy, r, paint)
        }
        return bmp
    }

    /** Month grid with weekday header; today highlighted, other months dimmed. */
    fun calendar(
        w: Int,
        h: Int,
        month: YearMonth,
        today: LocalDate,
        startSunday: Boolean,
        weekdayNames: List<String>,
        typeface: Typeface?,
        text: Int,
        accent: Int,
        rtl: Boolean,
    ): Bitmap {
        val bmp = bitmap(w, h)
        val canvas = Canvas(bmp)
        val rows = 7 // header + 6 weeks
        val cellW = w / 7f
        val cellH = h / rows.toFloat()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface ?: Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        fun colX(col: Int) = (if (rtl) 6 - col else col) * cellW + cellW / 2

        paint.color = withAlpha(text, 0.6f)
        paint.textSize = min(cellH * 0.38f, cellW * 0.32f)
        weekdayNames.forEachIndexed { col, name -> canvas.drawText(name, colX(col), cellH * 0.62f, paint) }

        val first = month.atDay(1)
        val offset = if (startSunday) first.dayOfWeek.value % 7 else (first.dayOfWeek.value + 1) % 7
        val start = first.minusDays(offset.toLong())
        paint.textSize = min(cellH * 0.45f, cellW * 0.42f)
        val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 0.35f) }
        for (i in 0 until 42) {
            val day = start.plusDays(i.toLong())
            val col = i % 7
            val row = i / 7 + 1
            val cx = colX(col)
            val cy = row * cellH + cellH / 2
            if (day == today) {
                val rw = cellW * 0.42f
                val rh = cellH * 0.42f
                canvas.drawRoundRect(RectF(cx - rw, cy - rh, cx + rw, cy + rh), rh, rh, highlight)
            }
            paint.color = if (YearMonth.from(day) == month) text else withAlpha(text, 0.3f)
            paint.isFakeBoldText = day == today
            canvas.drawText(day.dayOfMonth.toString(), cx, cy - (paint.descent() + paint.ascent()) / 2, paint)
        }
        return bmp
    }

    /**
     * A calligraphy name (day/month) centred over a large faded number,
     * optionally with a small line underneath.
     */
    fun calligraphy(
        w: Int,
        h: Int,
        main: String,
        number: String?,
        sub: String?,
        typeface: Typeface?,
        text: Int,
        accent: Int,
        latin: Boolean,
    ): Bitmap {
        val bmp = bitmap(w, h)
        val canvas = Canvas(bmp)
        val subH = if (sub.isNullOrBlank()) 0f else h * 0.18f
        val area = h - subH
        if (!number.isNullOrBlank()) {
            val np = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = withAlpha(text, 0.16f)
                textAlign = Paint.Align.CENTER
            }
            fit(np, number, w * 0.9f, area * 1.05f, area * 1.1f)
            canvas.drawText(number, w / 2f, area / 2f - (np.descent() + np.ascent()) / 2, np)
        }
        val mp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface ?: Typeface.DEFAULT
            color = text
            textAlign = Paint.Align.CENTER
            if (latin) letterSpacing = 0.22f
            setShadowLayer(6f, 0f, 2f, withAlpha(Color.BLACK, 0.35f))
        }
        fit(mp, main, w * 0.86f, area * 0.8f, area * 0.75f)
        canvas.drawText(main, w / 2f, area / 2f - (mp.descent() + mp.ascent()) / 2, mp)
        if (!sub.isNullOrBlank()) {
            val sp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
                textAlign = Paint.Align.CENTER
            }
            fit(sp, sub, w * 0.8f, subH * 0.9f, subH * 0.8f)
            canvas.drawText(sub, w / 2f, area + subH / 2 - (sp.descent() + sp.ascent()) / 2, sp)
        }
        return bmp
    }

    /** Big clock digits in a custom font; one line per entry of [lines]. */
    fun bigClock(w: Int, h: Int, lines: List<String>, typeface: Typeface?, color: Int, accent: Int): Bitmap {
        val bmp = bitmap(w, h)
        val canvas = Canvas(bmp)
        val lineH = h / lines.size.toFloat()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface ?: Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val longest = lines.maxByOrNull { it.length } ?: ""
        val size = fit(paint, longest, w * 0.94f, lineH * 1.05f, lineH * 1.2f)
        lines.forEachIndexed { i, line ->
            paint.textSize = size
            paint.color = if (lines.size > 1 && i == lines.lastIndex) accent else color
            val cy = i * lineH + lineH / 2
            canvas.drawText(line, w / 2f, cy - (paint.descent() + paint.ascent()) / 2, paint)
        }
        return bmp
    }
}
