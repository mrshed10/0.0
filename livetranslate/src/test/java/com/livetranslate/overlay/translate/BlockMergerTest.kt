package com.livetranslate.overlay.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockMergerTest {

    private fun line(text: String, l: Float, t: Float, r: Float, h: Float = 30f) = OcrLine(text, l, t, r, t + h)

    @Test
    fun mergesCenteredSpeechBubbleLines() {
        // نص فقاعة متمركز قسمه الـ OCR إلى ثلاث كتل
        val blocks = listOf(
            OcrBlock(listOf(line("I DON'T THINK", 100f, 100f, 300f))),
            OcrBlock(listOf(line("WE SHOULD GO", 110f, 135f, 290f))),
            OcrBlock(listOf(line("IN THERE...", 130f, 170f, 270f))),
        )
        val merged = BlockMerger.merge(blocks)
        assertEquals(1, merged.size)
        assertEquals(listOf("I DON'T THINK", "WE SHOULD GO", "IN THERE..."), merged[0].lines.map { it.text })
    }

    @Test
    fun keepsLeftAlignedMenuItemsSeparate() {
        val blocks = listOf(
            OcrBlock(listOf(line("Settings", 40f, 100f, 180f))),
            OcrBlock(listOf(line("Privacy", 40f, 140f, 170f))),
            OcrBlock(listOf(line("Help", 40f, 180f, 120f))),
        )
        assertEquals(3, BlockMerger.merge(blocks).size)
    }

    @Test
    fun mergesContinuingSentence() {
        val blocks = listOf(
            OcrBlock(listOf(line("These are my favorite Korean", 40f, 100f, 600f))),
            OcrBlock(listOf(line("skincare products this winter", 40f, 135f, 560f))),
        )
        assertEquals(1, BlockMerger.merge(blocks).size)
    }

    @Test
    fun keepsFarApartBubblesSeparate() {
        val blocks = listOf(
            OcrBlock(listOf(line("HEY!", 100f, 100f, 200f))),
            OcrBlock(listOf(line("WHAT?", 105f, 400f, 195f))),
        )
        assertEquals(2, BlockMerger.merge(blocks).size)
    }

    @Test
    fun neverMergesRotatedText() {
        val blocks = listOf(
            OcrBlock(listOf(line("FROM KOREA", 100f, 100f, 300f)), angle = -30f),
            OcrBlock(listOf(line("WITH LOVE", 110f, 135f, 290f)), angle = -30f),
        )
        assertEquals(2, BlockMerger.merge(blocks).size)
    }
}
