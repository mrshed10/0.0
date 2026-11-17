package com.deepsearch.hub.editor

import android.graphics.ColorMatrix

/** مصفوفة ألوان تجمع السطوع والتباين والتشبع. */
object ColorAdjust {
    /**
     * @param brightness -100..100
     * @param contrast   0.5..2 (1 = بدون تغيير)
     * @param saturation 0..2   (0 = أبيض وأسود)
     */
    fun matrix(brightness: Float, contrast: Float, saturation: Float): ColorMatrix {
        val m = ColorMatrix().apply { setSaturation(saturation) }
        val t = (1f - contrast) * 128f + brightness
        m.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, t,
                    0f, contrast, 0f, 0f, t,
                    0f, 0f, contrast, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
        return m
    }
}
