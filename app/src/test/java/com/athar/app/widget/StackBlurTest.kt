package com.athar.app.widget

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StackBlurTest {
    @Test
    fun uniformImageStaysTheSame() {
        val px = IntArray(20 * 10) { 0xFF336699.toInt() }
        assertArrayEquals(px, StackBlur.blur(px, 20, 10, 4))
    }

    @Test
    fun radiusZeroIsCopy() {
        val px = IntArray(16) { it * 1000 }
        val out = StackBlur.blur(px, 4, 4, 0)
        assertArrayEquals(px, out)
        assertTrue(out !== px)
    }

    @Test
    fun sharpEdgeIsSoftened() {
        // Left half black, right half white (opaque)
        val w = 20
        val h = 4
        val px = IntArray(w * h) { i -> if (i % w < w / 2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val out = StackBlur.blur(px, w, h, 3)
        val edge = out[w / 2] and 0xFF
        assertTrue("edge blue channel $edge should be mid-grey", edge in 40..215)
        // Alpha stays opaque
        assertEquals(0xFF, out[0] ushr 24)
    }
}
