package com.athar.app.core.fonts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Renders text in a custom font to a bitmap (for widgets, which only support system fonts). */
object TextBitmap {
    fun render(
        context: Context,
        text: String,
        typeface: Typeface,
        sizeSp: Float,
        color: Int,
        maxWidthPx: Int,
        maxLines: Int,
        center: Boolean,
        underline: Boolean = false,
        alignEnd: Boolean = false,
    ): Bitmap {
        val metrics = context.resources.displayMetrics
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            this.color = color
            textSize = sizeSp * metrics.density * context.resources.configuration.fontScale
            isUnderlineText = underline
        }
        val content = text.ifEmpty { " " }
        val desired = ceil(StaticLayout.getDesiredWidth(content, paint)).toInt()
        val width = max(1, min(desired, maxWidthPx.coerceAtLeast(1)))
        val layout = StaticLayout.Builder.obtain(content, 0, content.length, paint, width)
            .setAlignment(
                when {
                    center -> Layout.Alignment.ALIGN_CENTER
                    alignEnd -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> Layout.Alignment.ALIGN_NORMAL
                },
            )
            .setMaxLines(maxLines.coerceAtLeast(1))
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
        val bitmap = Bitmap.createBitmap(width, max(1, layout.height), Bitmap.Config.ARGB_8888)
        bitmap.density = metrics.densityDpi
        layout.draw(Canvas(bitmap))
        return bitmap
    }
}
