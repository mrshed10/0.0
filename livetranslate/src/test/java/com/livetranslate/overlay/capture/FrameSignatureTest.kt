package com.livetranslate.overlay.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class FrameSignatureTest {

    private fun frame(w: Int, h: Int, fill: (x: Int, y: Int) -> Int): ByteBuffer {
        val buf = ByteBuffer.allocate(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) {
            val v = fill(x, y).toByte()
            buf.put(v); buf.put(v); buf.put(v); buf.put(0xFF.toByte())
        }
        return buf
    }

    private fun sig(buf: ByteBuffer, w: Int, h: Int, exclude: Box? = null) =
        FrameSignature.compute(buf, w, h, w * 4, 4, exclude)

    @Test
    fun identicalFramesAreStable() {
        val a = sig(frame(200, 400) { x, y -> (x + y) % 256 }, 200, 400)
        val b = sig(frame(200, 400) { x, y -> (x + y) % 256 }, 200, 400)
        assertFalse(a.differsFrom(b))
    }

    @Test
    fun scrolledFrameIsDetected() {
        val a = sig(frame(200, 400) { _, y -> if ((y / 20) % 2 == 0) 0 else 255 }, 200, 400)
        val b = sig(frame(200, 400) { _, y -> if (((y + 10) / 20) % 2 == 0) 0 else 255 }, 200, 400)
        assertTrue(a.differsFrom(b))
    }

    @Test
    fun systemBarChangesAreIgnored() {
        // تغيّر في شريط الحالة/التنقل والحواف فقط
        val a = sig(frame(200, 400) { _, _ -> 120 }, 200, 400)
        val b = sig(frame(200, 400) { x, y -> if (x < 4 || x > 195 || y < 10 || y > 390) 0 else 120 }, 200, 400)
        assertFalse(a.differsFrom(b))
    }

    @Test
    fun excludedPillAreaIsIgnored() {
        val pill = Box(60, 300, 140, 360)
        val a = sig(frame(200, 400) { _, _ -> 120 }, 200, 400, pill)
        val b = sig(frame(200, 400) { x, y -> if (pill.contains(x, y)) (x * 7) % 256 else 120 }, 200, 400, pill)
        assertFalse(a.differsFrom(b))
    }

    @Test
    fun maskIgnoresOurOwnTranslation() {
        val patch = Box(40, 100, 160, 200)
        val clean = sig(frame(200, 400) { _, y -> (y * 3) % 256 }, 200, 400)
        val withPatch = sig(frame(200, 400) { x, y -> if (patch.contains(x, y)) 255 else (y * 3) % 256 }, 200, 400)
        assertTrue(withPatch.differsFrom(clean))
        val mask = FrameSignature.maskFor(200, 400, listOf(patch))
        assertFalse(withPatch.differsFrom(clean, mask))
        // تمرير حقيقي ما زال يُكتشف مع القناع
        val scrolled = sig(frame(200, 400) { x, y -> if (patch.contains(x, y)) 255 else ((y + 30) * 3) % 256 }, 200, 400)
        assertTrue(scrolled.differsFrom(clean, mask))
    }

    @Test
    fun nullBaselineAlwaysDiffers() {
        val a = sig(frame(10, 10) { _, _ -> 0 }, 10, 10)
        assertTrue(a.differsFrom(null))
    }
}
