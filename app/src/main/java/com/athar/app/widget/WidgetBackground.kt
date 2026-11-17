package com.athar.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws a widget's background (colour/gradient/image, opacity, blur, veil,
 * rounded corners and glass rim) into a bitmap. Widgets cannot draw these
 * effects themselves, so the bitmap is used as the widget's backdrop.
 */
object WidgetBackground {
    /** Keeps bitmaps small enough for the launcher's widget memory budget. */
    private const val MAX_SIDE = 900

    fun render(context: Context, style: WidgetStyle, widthPx: Int, heightPx: Int): Bitmap {
        val scale = min(1f, MAX_SIDE.toFloat() / max(widthPx, heightPx).coerceAtLeast(1))
        val w = (widthPx * scale).roundToInt().coerceAtLeast(8)
        val h = (heightPx * scale).roundToInt().coerceAtLeast(8)
        val density = context.resources.displayMetrics.density * scale
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = style.cornerDp * density
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.clipPath(clip)
        val alpha = (style.opacity.coerceIn(0, 100) * 255 / 100)

        when (style.kind) {
            BackgroundKind.Color -> canvas.drawRect(rect, Paint().apply { color = style.color1; this.alpha = alpha })
            BackgroundKind.DotGrid -> {
                // Nothing-style: solid colour with a faint grid of dots.
                canvas.drawRect(rect, Paint().apply { color = style.color1; this.alpha = alpha })
                val step = 9f * density
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    // The user's unlit-dot colour, if chosen; otherwise a faint text colour.
                    val off = style.dotOff
                    if (off != null) {
                        color = off
                        this.alpha = (android.graphics.Color.alpha(off) * alpha / 255f * 0.6f).toInt().coerceAtLeast(if (alpha == 0) 0 else 6)
                    } else {
                        color = style.textColor
                        this.alpha = (alpha * 0.07f).toInt().coerceAtLeast(8)
                    }
                }
                var y = step / 2
                while (y < h) {
                    var x = step / 2
                    while (x < w) {
                        canvas.drawCircle(x, y, 1.1f * density, dot)
                        x += step
                    }
                    y += step
                }
            }
            BackgroundKind.GlowDots -> {
                // Base colour, then a dot matrix whose dots light up near a soft accent glow.
                canvas.drawRect(rect, Paint().apply { color = style.color1; this.alpha = alpha })
                val gx = w * 0.78f
                val gy = h * 0.22f
                val reach = max(w, h) * 0.85f
                canvas.drawCircle(
                    gx, gy, reach,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = android.graphics.RadialGradient(
                            gx, gy, reach,
                            (style.litDots(style.accentColor) and 0x00FFFFFF) or ((alpha * 0.22f).toInt() shl 24),
                            style.litDots(style.accentColor) and 0x00FFFFFF,
                            Shader.TileMode.CLAMP,
                        )
                    },
                )
                val step = 8f * density
                val dot = Paint(Paint.ANTI_ALIAS_FLAG)
                var y = step / 2
                while (y < h) {
                    var x = step / 2
                    while (x < w) {
                        val d = kotlin.math.hypot(x - gx, y - gy) / reach
                        val light = (1f - d).coerceIn(0f, 1f)
                        dot.color = if (light > 0.45f) style.litDots(style.accentColor) else style.dotOff ?: style.textColor
                        dot.alpha = ((0.06f + 0.5f * light * light) * alpha).toInt().coerceIn(if (alpha == 0) 0 else 6, 255)
                        canvas.drawCircle(x, y, (0.9f + 0.7f * light) * density, dot)
                        x += step
                    }
                    y += step
                }
            }
            BackgroundKind.Gradient -> canvas.drawRect(rect, gradientPaint(style, w, h, alpha))
            BackgroundKind.Image -> {
                val image = style.imagePath?.let { loadImage(it, w, h) }
                if (image != null) {
                    val blurred = if (style.blur > 0) blur(image, style.blur) else image
                    canvas.drawBitmap(blurred, null, rect, Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha })
                    val veil = (style.dim.coerceIn(0, 100) * 255 / 100)
                    canvas.drawRect(rect, Paint().apply { color = style.color1; this.alpha = veil })
                } else {
                    canvas.drawRect(rect, gradientPaint(style, w, h, alpha))
                }
            }
        }

        // The glass rim fades with the background, so a transparent widget has no visible edge.
        if (hasRim(style)) {
            val rimAlpha = (style.opacity.coerceIn(0, 100) / 100f).coerceAtLeast(0.35f)
            val stroke = 1.5f * density
            val inset = RectF(stroke / 2, stroke / 2, w - stroke / 2, h - stroke / 2)
            val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = stroke
                shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    ((0x55 * rimAlpha).toInt() shl 24) or 0xFFFFFF,
                    ((0x10 * rimAlpha).toInt() shl 24) or 0xFFFFFF,
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRoundRect(inset, radius, radius, rim)
        }
        return bitmap
    }

    /**
     * No rim on a (nearly) invisible background: otherwise a faint outline stays
     * on the home screen around a widget that should blend into the wallpaper.
     */
    fun hasRim(style: WidgetStyle): Boolean = style.border &&
        (style.opacity >= 8 || (style.kind == BackgroundKind.Image && style.imagePath != null))

    /** True when nothing at all is drawn behind the content. */
    fun isInvisible(style: WidgetStyle): Boolean = style.opacity == 0 && !hasRim(style) &&
        style.kind != BackgroundKind.Image

    private fun gradientPaint(style: WidgetStyle, w: Int, h: Int, alpha: Int): Paint {
        val rad = Math.toRadians(style.angle.toDouble())
        val cx = w / 2f
        val cy = h / 2f
        val half = max(w, h) / 2f
        val dx = (cos(rad) * half).toFloat()
        val dy = (sin(rad) * half).toFloat()
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, style.color1, style.color2, Shader.TileMode.CLAMP)
            this.alpha = alpha
        }
    }

    /** Decodes and centre-crops the image to the target aspect ratio. */
    private fun loadImage(path: String, w: Int, h: Int): Bitmap? = runCatching {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val full = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val targetRatio = w.toFloat() / h
        val srcRatio = full.width.toFloat() / full.height
        val crop = if (srcRatio > targetRatio) {
            val cw = (full.height * targetRatio).toInt()
            Rect((full.width - cw) / 2, 0, (full.width + cw) / 2, full.height)
        } else {
            val ch = (full.width / targetRatio).toInt()
            Rect(0, (full.height - ch) / 2, full.width, (full.height + ch) / 2)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(full, crop, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        out
    }.getOrNull()

    /** Downscale, blur, upscale: cheap and smooth. */
    private fun blur(source: Bitmap, radius: Int): Bitmap {
        val factor = 4
        val sw = (source.width / factor).coerceAtLeast(4)
        val sh = (source.height / factor).coerceAtLeast(4)
        val small = Bitmap.createScaledBitmap(source, sw, sh, true)
        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        val blurred = StackBlur.blur(pixels, sw, sh, radius.coerceIn(1, 25) / 2 + 1)
        small.setPixels(blurred, 0, sw, 0, 0, sw, sh)
        return Bitmap.createScaledBitmap(small, source.width, source.height, true)
    }

    /** Copies a picked image into app storage (max 1600 px) and returns its path. */
    fun importImage(context: Context, uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val dir = File(context.filesDir, "widget_bg").apply { mkdirs() }
        val file = File(dir, "bg_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        file.absolutePath
    }.getOrNull()
}
