package app.safar.browser

import app.safar.browser.translate.ocr.Box
import app.safar.browser.translate.ocr.BubbleAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BubbleAnalyzerTest {
    private val W = 400; private val H = 300
    private val BLACK = 0xFF000000.toInt(); private val WHITE = 0xFFFFFFFF.toInt(); private val GRAY = 0xFF808080.toInt()

    /** صفحة رمادية + فقاعة بيضاوية بيضاء بحد أسود + نص أسود في الوسط. */
    private fun page(): IntArray {
        val px = IntArray(W * H) { GRAY }
        val cx = 200.0; val cy = 150.0; val rx = 120.0; val ry = 80.0
        for (y in 0 until H) for (x in 0 until W) {
            val d = ((x - cx) / rx).let { it * it } + ((y - cy) / ry).let { it * it }
            if (d <= 1.0) px[y * W + x] = WHITE
            if (d in 0.93..1.07) px[y * W + x] = BLACK // الحد الأسود
        }
        // "نص": خطوط سوداء داخل المستطيل 150..250 × 130..170
        for (y in 130 until 170 step 6) for (x in 150 until 250) if ((x / 4) % 2 == 0) { px[y * W + x] = BLACK; px[(y + 1) * W + x] = BLACK }
        return px
    }

    @Test fun detectsWhiteUniformBubble() {
        val b = BubbleAnalyzer.analyze(page(), W, H, Box(150f, 130f, 250f, 172f))
        assertTrue(b.uniform)
        assertEquals(0xFFFFFF, b.bg)
        assertTrue(b.darkText)
        assertNotNull(b.clean)
    }

    @Test fun textAreaStaysInsideBubbleOutline() {
        val b = BubbleAnalyzer.analyze(page(), W, H, Box(150f, 130f, 250f, 172f))
        // الفقاعة: x ∈ [80,320], y ∈ [70,230] — يجب أن تكون مساحة الكتابة داخلها وأكبر من النص الأصلي
        assertTrue("left ${b.textArea.left}", b.textArea.left >= 82f)
        assertTrue("right ${b.textArea.right}", b.textArea.right <= 318f)
        assertTrue("top ${b.textArea.top}", b.textArea.top >= 72f)
        assertTrue("bottom ${b.textArea.bottom}", b.textArea.bottom <= 228f)
        assertTrue(b.textArea.width >= 100f && b.textArea.height >= 42f)
    }

    @Test fun cleanRectDoesNotTouchOutline() {
        val px = page()
        val c = BubbleAnalyzer.analyze(px, W, H, Box(150f, 130f, 250f, 172f)).clean!!
        for (y in c.top.toInt() until c.bottom.toInt()) for (x in c.left.toInt() until c.right.toInt()) {
            val cx = 200.0; val cy = 150.0
            val d = ((x - cx) / 120.0).let { it * it } + ((y - cy) / 80.0).let { it * it }
            assertTrue("clean rect reaches outline at $x,$y", d < 0.93)
        }
    }

    @Test fun noisyArtIsNotCleaned() {
        val rnd = Random(1)
        val px = IntArray(W * H) { (0xFF shl 24) or (rnd.nextInt(256) * 0x010101) }
        val b = BubbleAnalyzer.analyze(px, W, H, Box(150f, 130f, 250f, 170f))
        assertFalse(b.uniform)
        assertNull(b.clean)
    }

    @Test fun darkBubbleUsesLightText() {
        val px = IntArray(W * H) { BLACK }
        for (y in 130 until 170) for (x in 150 until 250) if (x % 5 == 0) px[y * W + x] = WHITE
        val b = BubbleAnalyzer.analyze(px, W, H, Box(150f, 130f, 250f, 170f))
        assertTrue(b.uniform)
        assertFalse(b.darkText)
    }
}
