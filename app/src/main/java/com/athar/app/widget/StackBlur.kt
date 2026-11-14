package com.athar.app.widget

import kotlin.math.max
import kotlin.math.min

/**
 * Box-approximated Gaussian blur on ARGB pixels (three box passes), used to
 * make "frosted" widget backgrounds, since widgets cannot blur live content.
 * Pure Kotlin so it can be unit-tested.
 */
object StackBlur {
    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
        if (radius < 1 || width < 2 || height < 2) return pixels.copyOf()
        var src = pixels.copyOf()
        var dst = IntArray(src.size)
        repeat(3) {
            boxHorizontal(src, dst, width, height, radius)
            boxVertical(dst, src, width, height, radius)
        }
        dst = src
        return dst
    }

    private fun boxHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            var a = 0; var red = 0; var g = 0; var b = 0
            val row = y * w
            for (i in -r..r) {
                val p = src[row + min(w - 1, max(i, 0))]
                a += p ushr 24; red += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = ((a / div) shl 24) or ((red / div) shl 16) or ((g / div) shl 8) or (b / div)
                val pOut = src[row + max(x - r, 0)]
                val pIn = src[row + min(x + r + 1, w - 1)]
                a += (pIn ushr 24) - (pOut ushr 24)
                red += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                g += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                b += (pIn and 0xFF) - (pOut and 0xFF)
            }
        }
    }

    private fun boxVertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var a = 0; var red = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val p = src[min(h - 1, max(i, 0)) * w + x]
                a += p ushr 24; red += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = ((a / div) shl 24) or ((red / div) shl 16) or ((g / div) shl 8) or (b / div)
                val pOut = src[max(y - r, 0) * w + x]
                val pIn = src[min(y + r + 1, h - 1) * w + x]
                a += (pIn ushr 24) - (pOut ushr 24)
                red += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                g += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                b += (pIn and 0xFF) - (pOut and 0xFF)
            }
        }
    }
}
