package app.safar.browser

import app.safar.browser.translate.ocr.Box
import app.safar.browser.translate.ocr.BubbleAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Test

/** لقطة الشاشة: فقاعتان طويلتان متلاصقتان جنباً إلى جنب ("WELL, SHE USED TO BE…" و"AS THE CLASS REP…"). */
class SideBySideBubblesTest {
    @Test fun adjacentTallBubblesStaySeparateForHorizontalScripts() {
        val W = 420; val H = 360
        val px = IntArray(W * H) { 0xFF909090.toInt() }
        val black = 0xFF000000.toInt(); val white = 0xFFFFFFFF.toInt()
        // فقاعتان بيضاويتان طويلتان تتلامسان تقريباً عند x≈210
        for ((cx) in listOf(125.0 to 0, 295.0 to 0)) for (y in 0 until H) for (x in 0 until W) {
            val d = ((x - cx) / 88).let { it * it } + ((y - 180.0) / 165).let { it * it }
            if (d <= 1.0) px[y * W + x] = white
            if (d in 0.94..1.06) px[y * W + x] = black
        }
        // كتلة نص طويلة وضيقة في كل فقاعة (تشبه "العمود" الياباني من حيث الشكل)
        val a = Box(85f, 60f, 165f, 300f)
        val b = Box(255f, 60f, 335f, 300f)
        for (bx in listOf(a, b)) for (y in bx.top.toInt() until bx.bottom.toInt() step 4) for (x in bx.left.toInt() until bx.right.toInt()) if (x % 3 == 0) px[y * W + x] = black
        val bubbles = listOf(BubbleAnalyzer.analyze(px, W, H, a, listOf(b)), BubbleAnalyzer.analyze(px, W, H, b, listOf(a)))
        // إنجليزي/عربي (بدون أعمدة): فقاعتان منفصلتان
        assertEquals(2, BubbleAnalyzer.groupByBubble(listOf(a, b), bubbles, px, W, H, allowColumns = false).size)
    }
}
