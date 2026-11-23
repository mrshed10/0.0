package app.safar.browser

import app.safar.browser.translate.ocr.Box
import app.safar.browser.translate.ocr.BubbleAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** حالة لقطة الشاشة: فقاعة كبيرة مقصوصة بالحافة اليسرى للشاشة، أسطرها متباعدة قليلاً. */
class EdgeBubbleTest {
    private val W = 360; private val H = 420
    private val BLACK = 0xFF000000.toInt(); private val WHITE = 0xFFFFFFFF.toInt(); private val GRAY = 0xFF707070.toInt()

    private fun page(): Pair<IntArray, List<Box>> {
        val px = IntArray(W * H) { GRAY }
        // بيضاوي مركزه خارج الحافة اليسرى قليلاً ⇒ الفقاعة مقصوصة عند x=0
        val cx = 120.0; val cy = 210.0; val rx = 200.0; val ry = 190.0
        for (y in 0 until H) for (x in 0 until W) {
            val d = ((x - cx) / rx).let { it * it } + ((y - cy) / ry).let { it * it }
            if (d <= 1.0) px[y * W + x] = WHITE
            if (d in 0.96..1.04) px[y * W + x] = BLACK
        }
        // 6 أسطر نص بمسافة سطر كاملة تقريباً بينها (مثل "YOU ARE A" / "TEAM!" / "LEARN" / ...)
        val lines = (0 until 6).map { i -> Box(60f, 90f + i * 40f, 220f, 112f + i * 40f) }
        for (b in lines) for (y in b.top.toInt() until b.bottom.toInt() step 3) for (x in b.left.toInt() until b.right.toInt()) if ((x / 3) % 2 == 0) px[y * W + x] = BLACK
        return px to lines
    }

    @Test fun edgeCutBubbleKeepsInteriorAndGroupsAllLines() {
        val (px, lines) = page()
        val bubbles = lines.mapIndexed { i, b -> BubbleAnalyzer.analyze(px, W, H, b, lines.filterIndexed { j, _ -> j != i }) }
        bubbles.forEach { assertTrue(it.uniform); assertNotNull(it.clean) }
        val groups = BubbleAnalyzer.groupByBubble(lines, bubbles, px, W, H)
        assertEquals("all lines in one bubble", 1, groups.size)
    }

    @Test fun stackedBubblesSeparatedByOutlineStayApart() {
        val px = IntArray(W * H) { GRAY }
        for ((cy) in listOf(110.0 to 0, 300.0 to 0)) for (y in 0 until H) for (x in 0 until W) {
            val d = ((x - 180.0) / 150).let { it * it } + ((y - cy) / 85).let { it * it }
            if (d <= 1.0) px[y * W + x] = WHITE
            if (d in 0.93..1.07) px[y * W + x] = BLACK
        }
        val a = Box(120f, 160f, 240f, 182f) // آخر سطر في الفقاعة العليا
        val b = Box(120f, 222f, 240f, 244f) // أول سطر في الفقاعة السفلى
        for (bx in listOf(a, b)) for (y in bx.top.toInt() until bx.bottom.toInt() step 3) for (x in bx.left.toInt() until bx.right.toInt()) if (x % 4 == 0) px[y * W + x] = BLACK
        val bubbles = listOf(BubbleAnalyzer.analyze(px, W, H, a, listOf(b)), BubbleAnalyzer.analyze(px, W, H, b, listOf(a)))
        assertEquals(2, BubbleAnalyzer.groupByBubble(listOf(a, b), bubbles, px, W, H).size)
    }
}
