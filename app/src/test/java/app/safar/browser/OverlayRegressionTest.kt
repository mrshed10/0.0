package app.safar.browser

import app.safar.browser.translate.ScriptDetector
import app.safar.browser.translate.ocr.Box
import app.safar.browser.translate.ocr.BubbleAnalyzer
import app.safar.browser.translate.ocr.OcrBlock
import app.safar.browser.translate.ocr.OcrPostProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** حالات من لقطات الشاشة الفعلية: رموز OCR الناتجة عن قراءة الترجمة العربية أو خطوط الرسم. */
class OverlayRegressionTest {
    private fun block(t: String, h: Float = 30f) = OcrBlock(t, Box(0f, 0f, 120f, h))

    @Test fun garbageFromScreenshotsIsDropped() {
        listOf("iYYOC Alas hiibiN", "JQvWAY", "HVB1LJYs", "S'sbgyányičpssss", "algqyji Jgls", "l I | i", "€", "!?", "t00sAC").forEach {
            assertTrue("should drop: $it", OcrPostProcessor.isGarbage(block(it)))
        }
    }

    @Test fun realMangaDialogueIsKept() {
        listOf(
            "WHAT ARE YOU DOING?", "YOU ARE A TEAM!", "NARUTO TRIED TO DO SINGLE-HANDEDLY",
            "Instead...", "I'm hungry", "なにっ!?", "뭐 하는 거야",
        ).forEach { assertFalse("should keep: $it", OcrPostProcessor.isGarbage(block(it))) }
    }

    @Test fun tinyBlocksDropped() = assertTrue(OcrPostProcessor.isGarbage(OcrBlock("HELLO", Box(0f, 0f, 40f, 5f))))

    @Test fun strokesReadAsKanaAreNotJapanese() {
        // صفحة إنجليزية فيها خطوط رسم قُرئت ككانا مفردة
        assertNotEquals("ja", ScriptDetector.guess("YOU ARE A TEAM LEARN TO ACT AS ONE ノ ー"))
        assertEquals("en", ScriptDetector.guess("YOU ARE A TEAM LEARN TO ACT AS ONE ノ ー"))
    }

    /** فقاعة بيضاء مزدحمة بأسطر منفصلة: يجب أن تُعد موحّدة وتُجمّع أسطرها في فقاعة واحدة. */
    @Test fun denseBubbleLinesGroupAndClean() {
        val W = 400; val H = 320
        val px = IntArray(W * H) { 0xFF808080.toInt() }
        val black = 0xFF000000.toInt(); val white = 0xFFFFFFFF.toInt()
        for (y in 0 until H) for (x in 0 until W) {
            val d = ((x - 200.0) / 150).let { it * it } + ((y - 160.0) / 120).let { it * it }
            if (d <= 1.0) px[y * W + x] = white
            if (d in 0.94..1.06) px[y * W + x] = black
        }
        val lines = listOf(Box(130f, 100f, 270f, 122f), Box(120f, 128f, 280f, 150f), Box(125f, 156f, 275f, 178f), Box(140f, 184f, 260f, 206f))
        for (b in lines) for (y in b.top.toInt() until b.bottom.toInt() step 3) for (x in b.left.toInt() until b.right.toInt()) if ((x / 3) % 2 == 0) px[y * W + x] = black
        // بكسل رمادي معزول خارج الفقاعة لا يهم؛ فقاعة ثانية بعيدة
        val bubbles = lines.mapIndexed { i, b -> BubbleAnalyzer.analyze(px, W, H, b, lines.filterIndexed { j, _ -> j != i }) }
        bubbles.forEach { assertTrue("uniform", it.uniform); assertNotNull(it.clean); assertNotNull(it.interior) }
        val groups = BubbleAnalyzer.groupByBubble(lines, bubbles)
        assertEquals(1, groups.size)
        assertEquals(4, groups[0].size)
    }

    @Test fun neighbouringBubblesAreNotMerged() {
        val W = 600; val H = 260
        val px = IntArray(W * H) { 0xFF808080.toInt() }
        val black = 0xFF000000.toInt(); val white = 0xFFFFFFFF.toInt()
        for ((cx, cy) in listOf(150.0 to 130.0, 450.0 to 130.0)) for (y in 0 until H) for (x in 0 until W) {
            val d = ((x - cx) / 130).let { it * it } + ((y - cy) / 100).let { it * it }
            if (d <= 1.0) px[y * W + x] = white
            if (d in 0.93..1.07) px[y * W + x] = black
        }
        val a = Box(100f, 115f, 200f, 145f); val b = Box(400f, 115f, 500f, 145f)
        for (bx in listOf(a, b)) for (y in bx.top.toInt() until bx.bottom.toInt() step 4) for (x in bx.left.toInt() until bx.right.toInt()) if (x % 4 == 0) px[y * W + x] = black
        val bubbles = listOf(BubbleAnalyzer.analyze(px, W, H, a, listOf(b)), BubbleAnalyzer.analyze(px, W, H, b, listOf(a)))
        assertEquals(2, BubbleAnalyzer.groupByBubble(listOf(a, b), bubbles).size)
    }
}
