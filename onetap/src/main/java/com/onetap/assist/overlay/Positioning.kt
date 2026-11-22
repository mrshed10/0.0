package com.onetap.assist.overlay

import kotlin.math.roundToInt

/** Pure positioning math for the floating button (unit-tested, no Android deps). */
object Positioning {

    /** Usable area for the button's top-left corner. */
    data class Area(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = (right - left).coerceAtLeast(0)
        val height get() = (bottom - top).coerceAtLeast(0)
    }

    fun area(screenW: Int, screenH: Int, insetTop: Int, insetBottom: Int, size: Int, margin: Int) = Area(
        left = margin,
        top = insetTop + margin,
        right = (screenW - size - margin).coerceAtLeast(margin),
        bottom = (screenH - insetBottom - size - margin).coerceAtLeast(insetTop + margin),
    )

    /** The x of the nearest vertical edge for a button whose left is [x]. */
    fun snapX(x: Int, size: Int, screenW: Int, area: Area): Int =
        if (x + size / 2 < screenW / 2) area.left else area.right

    fun clampX(x: Int, area: Area) = x.coerceIn(area.left, area.right)
    fun clampY(y: Int, area: Area) = y.coerceIn(area.top, area.bottom)

    fun toFracX(x: Int, area: Area): Float =
        if (area.width == 0) 0f else (clampX(x, area) - area.left).toFloat() / area.width

    fun toFracY(y: Int, area: Area): Float =
        if (area.height == 0) 0f else (clampY(y, area) - area.top).toFloat() / area.height

    fun fromFracX(f: Float, area: Area): Int = area.left + (f.coerceIn(0f, 1f) * area.width).roundToInt()
    fun fromFracY(f: Float, area: Area): Int = area.top + (f.coerceIn(0f, 1f) * area.height).roundToInt()
}
