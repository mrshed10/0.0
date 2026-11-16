package com.athar.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.athar.app.R
import com.athar.app.core.fonts.FontRegistry
import com.athar.app.core.fonts.TextBitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Animated next-prayer widgets. Widgets cannot run animations, so a few frames
 * of a glowing dot ring / dot bar are drawn ahead of time and the launcher
 * flips through them (ViewFlipper). The countdown is a Chronometer that ticks
 * on its own, so nothing wakes the app up.
 */
object PrayerPulse {
    const val FRAMES = 6

    /** Design index ([WidgetStyle.variant]) for the next-prayer widget. */
    const val CLASSIC = 0
    const val RING = 1
    const val BAR = 2

    fun isAnimated(data: WidgetData, style: WidgetStyle) =
        data is WidgetData.NextPrayer && (style.variant == RING || style.variant == BAR)

    private val frameIds = intArrayOf(
        R.id.prayer_frame_0, R.id.prayer_frame_1, R.id.prayer_frame_2,
        R.id.prayer_frame_3, R.id.prayer_frame_4, R.id.prayer_frame_5,
    )

    /** Views for a next-prayer widget whose content area is [wDp]×[hDp]. */
    fun build(context: Context, data: WidgetData.NextPrayer, ws: WidgetStyle, wDp: Float, hDp: Float): RemoteViews {
        val density = context.resources.displayMetrics.density
        val ring = ws.variant == RING
        val views = RemoteViews(context.packageName, if (ring) R.layout.widget_prayer_ring else R.layout.widget_prayer_bar)
        val side = min(wDp, hDp)
        val nameDp: Float
        val countDp: Float
        val timeDp: Float
        val frames: List<Bitmap>
        if (ring) {
            nameDp = (side * 0.12f).coerceIn(11f, 26f)
            countDp = (side * 0.15f).coerceIn(13f, 34f)
            timeDp = (side * 0.075f).coerceIn(9f, 16f)
            val px = (side * density).roundToInt().coerceIn(48, 260)
            frames = ringFrames(px, data.progress, ws.textColor, ws.accentColor)
        } else {
            nameDp = (hDp * 0.17f).coerceIn(12f, 26f)
            countDp = (hDp * 0.22f).coerceIn(14f, 36f)
            timeDp = (hDp * 0.1f).coerceIn(9f, 15f)
            val barH = (hDp * 0.3f).coerceIn(12f, 40f)
            val wPx = (wDp * density).roundToInt().coerceIn(48, 520)
            val hPx = (barH * density).roundToInt().coerceIn(10, 60)
            frames = barFrames(wPx, hPx, data.progress, ws.textColor, ws.accentColor)
        }
        frames.forEachIndexed { i, bitmap -> views.setImageViewBitmap(frameIds[i], bitmap) }

        // Name: a custom font is drawn into a bitmap (widgets only know system fonts).
        val typeface = ws.fontId?.let { FontRegistry.typeface(context, it) }
        if (typeface != null) {
            val bold = FontRegistry.boldTypeface(context, ws.fontId, typeface)
            val bmp = TextBitmap.render(
                context, data.name, bold, nameDp, ws.textColor,
                (wDp * density * (if (ring) 0.6f else 0.55f)).roundToInt().coerceAtLeast(40), 1, center = ring,
            )
            views.setImageViewBitmap(R.id.prayer_name_img, bmp)
            views.setViewVisibility(R.id.prayer_name_img, View.VISIBLE)
            views.setViewVisibility(R.id.prayer_name, View.GONE)
        } else {
            views.setTextViewText(R.id.prayer_name, data.name)
            views.setTextColor(R.id.prayer_name, ws.textColor)
            views.setTextViewTextSize(R.id.prayer_name, TypedValue.COMPLEX_UNIT_DIP, nameDp)
        }
        views.setTextViewText(R.id.prayer_time, data.time)
        views.setTextColor(R.id.prayer_time, ws.accentColor)
        views.setTextViewTextSize(R.id.prayer_time, TypedValue.COMPLEX_UNIT_DIP, timeDp)
        countdown(views, data.atMillis, ws.textColor, countDp)
        return views
    }

    /** The small live countdown line used by the classic design. */
    fun countdownLine(context: Context, data: WidgetData.NextPrayer, color: Int, sizeDp: Float): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_prayer_chrono).also { countdown(it, data.atMillis, color, sizeDp) }

    private fun countdown(views: RemoteViews, atMillis: Long, color: Int, sizeDp: Float) {
        val left = (atMillis - System.currentTimeMillis()).coerceAtLeast(0)
        views.setChronometer(R.id.prayer_countdown, SystemClock.elapsedRealtime() + left, null, true)
        views.setChronometerCountDown(R.id.prayer_countdown, true)
        views.setTextColor(R.id.prayer_countdown, color)
        views.setTextViewTextSize(R.id.prayer_countdown, TypedValue.COMPLEX_UNIT_DIP, sizeDp)
    }

    // ------------------------------------------------------------ frames

    /** How bright a lit dot is in a frame: a soft wave travels along the lit part. */
    internal fun shimmer(index: Int, lit: Int, frame: Int): Float {
        if (lit <= 0) return 0f
        val centre = (frame + 0.5f) / FRAMES * lit
        val d = abs(index + 0.5f - centre)
        return (1f - d / 3.5f).coerceIn(0f, 1f)
    }

    internal fun litCount(progress: Float, total: Int): Int = ceil(progress.coerceIn(0f, 1f) * total).toInt().coerceIn(1, total)

    fun ringFrames(sizePx: Int, progress: Float, base: Int, accent: Int): List<Bitmap> {
        val n = 48
        val lit = litCount(progress, n)
        val c = sizePx / 2f
        val dot = sizePx * 0.022f
        val radius = c - dot * 4f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        return List(FRAMES) { f ->
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val phase = f.toFloat() / FRAMES
            for (i in 0 until n) {
                val angle = -PI / 2 + 2 * PI * i / n
                val x = c + (cos(angle) * radius).toFloat()
                val y = c + (sin(angle) * radius).toFloat()
                if (i < lit) {
                    val boost = shimmer(i, lit, f)
                    paint.color = blend(accent, Color.WHITE, boost * 0.35f)
                    paint.alpha = (150 + 105 * boost).toInt()
                    canvas.drawCircle(x, y, dot * (1f + 0.3f * boost), paint)
                } else {
                    paint.color = base
                    paint.alpha = 40
                    canvas.drawCircle(x, y, dot * 0.75f, paint)
                }
            }
            // Breathing glow on the leading dot.
            val head = -PI / 2 + 2 * PI * (lit - 1) / n
            val hx = c + (cos(head) * radius).toFloat()
            val hy = c + (sin(head) * radius).toFloat()
            val pulse = 0.5f + 0.5f * sin(2 * PI * phase).toFloat()
            val glowR = dot * (3.2f + 1.8f * pulse)
            glow.shader = RadialGradient(hx, hy, glowR, withAlpha(accent, (110 + 90 * pulse).toInt()), withAlpha(accent, 0), Shader.TileMode.CLAMP)
            canvas.drawCircle(hx, hy, glowR, glow)
            paint.color = blend(accent, Color.WHITE, 0.55f)
            paint.alpha = 255
            canvas.drawCircle(hx, hy, dot * 1.35f, paint)
            bmp
        }
    }

    fun barFrames(wPx: Int, hPx: Int, progress: Float, base: Int, accent: Int): List<Bitmap> {
        val rows = 3
        val cell = hPx / rows.toFloat()
        val cols = max(8, (wPx / cell).toInt())
        val step = wPx / cols.toFloat()
        val dot = min(cell, step) * 0.32f
        val lit = litCount(progress, cols)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        return List(FRAMES) { f ->
            val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val phase = f.toFloat() / FRAMES
            for (col in 0 until cols) {
                val x = step * (col + 0.5f)
                val boost = if (col < lit) shimmer(col, lit, f) else 0f
                for (row in 0 until rows) {
                    val y = cell * (row + 0.5f)
                    if (col < lit) {
                        paint.color = blend(accent, Color.WHITE, boost * 0.35f)
                        paint.alpha = (140 + 115 * boost).toInt()
                        canvas.drawCircle(x, y, dot * (1f + 0.25f * boost), paint)
                    } else {
                        paint.color = base
                        paint.alpha = 38
                        canvas.drawCircle(x, y, dot * 0.8f, paint)
                    }
                }
            }
            val hx = step * (lit - 0.5f)
            val pulse = 0.5f + 0.5f * sin(2 * PI * phase).toFloat()
            val glowR = hPx * (0.55f + 0.3f * pulse)
            glow.shader = RadialGradient(hx, hPx / 2f, glowR, withAlpha(accent, (90 + 80 * pulse).toInt()), withAlpha(accent, 0), Shader.TileMode.CLAMP)
            canvas.drawCircle(hx, hPx / 2f, glowR, glow)
            bmp
        }
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun blend(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - k) + ((b shr shift) and 0xFF) * k).roundToInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
