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
                    color = style.textColor
                    this.alpha = (alpha * 0.07f).toInt().coerceAtLeast(8)
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

        if (style.border) {
            val stroke = 1.5f * density
            val inset = RectF(stroke / 2, stroke / 2, w - stroke / 2, h - stroke / 2)
            val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = stroke
                shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0x55FFFFFF, 0x10FFFFFF, Shader.TileMode.CLAMP)
            }
            canvas.drawRoundRect(inset, radius, radius, rim)
        }
        return bitmap
    }

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
