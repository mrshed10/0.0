package com.livetranslate.overlay.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrFusionTest {

    private fun block(text: String, l: Float, t: Float, r: Float, b: Float, script: Script, conf: Float = 0.9f, vertical: Boolean = false) =
        OcrBlock(listOf(OcrLine(text, l, t, r, b, conf)), script = script, vertical = vertical)

    @Test
    fun japaneseModelWinsOverLatinGarbageOnJapaneseText() {
        val latinGarbage = block("Tlo LLz FA", 100f, 100f, 400f, 140f, Script.LATIN, conf = 0.4f)
        val japanese = block("ありがとうございます", 100f, 100f, 400f, 140f, Script.JAPANESE)
        val fused = OcrFusion.fuse(listOf(latinGarbage, japanese))
        assertEquals(listOf(japanese), fused)
    }

    @Test
    fun latinModelWinsOnEnglishText() {
        val latin = block("From Korea with Love", 100f, 100f, 400f, 140f, Script.LATIN)
        val viaJapanese = block("From Korea with Love", 100f, 100f, 400f, 140f, Script.JAPANESE, conf = 0.85f)
        assertEquals(listOf(latin), OcrFusion.fuse(listOf(viaJapanese, latin)))
    }

    @Test
    fun keepsTextFromDifferentScriptsInDifferentPlaces() {
        val korean = block("겨울철 피부 보습", 100f, 100f, 400f, 140f, Script.KOREAN)
        val english = block("@SeoulfulSkincare", 100f, 300f, 400f, 340f, Script.LATIN)
        val chinese = block("你好世界", 100f, 500f, 300f, 540f, Script.CHINESE)
        assertEquals(3, OcrFusion.fuse(listOf(korean, english, chinese)).size)
    }

    @Test
    fun rotatedReadingBeatsCharacterByCharacterReadingOfVerticalText() {
        // قراءة أفقية لعمود: حرف واحد في كل سطر
        val stacked = OcrBlock(
            "こんにちは".map { ch -> OcrLine(ch.toString(), 500f, 0f, 540f, 40f, 0.8f) }
                .mapIndexed { i, l -> l.copy(top = 100f + i * 40f, bottom = 140f + i * 40f) },
            script = Script.JAPANESE,
        )
        val rotated = block("こんにちは", 500f, 100f, 540f, 300f, Script.JAPANESE, vertical = true)
        val fused = OcrFusion.fuse(listOf(stacked, rotated))
        assertEquals(1, fused.size)
        assertTrue(fused[0].vertical)
    }

    @Test
    fun unrotateMapsColumnsBackToScreenInReadingOrder() {
        val screenWidth = 1000f
        // في اللقطة المُدارة: السطر الأول (الأعلى) = العمود الأيمن في الشاشة
        val rotatedBlock = OcrBlock(
            listOf(
                OcrLine("第一列", left = 200f, top = 50f, right = 500f, bottom = 90f),
                OcrLine("第二列", left = 200f, top = 100f, right = 500f, bottom = 140f),
            ),
            script = Script.JAPANESE,
        )
        val screen = OcrFusion.unrotate(rotatedBlock, screenWidth)
        val first = screen.lines[0]
        val second = screen.lines[1]
        assertEquals(OcrLine("第一列", 910f, 200f, 950f, 500f), first)
        assertEquals(OcrLine("第二列", 860f, 200f, 900f, 500f), second)
        assertTrue("العمود الأول على اليمين", first.left > second.left)
        assertTrue(screen.vertical)
    }
}
