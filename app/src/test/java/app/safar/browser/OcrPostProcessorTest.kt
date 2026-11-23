package app.safar.browser

import app.safar.browser.translate.ocr.Box
import app.safar.browser.translate.ocr.OcrBlock
import app.safar.browser.translate.ocr.OcrPostProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPostProcessorTest {
    @Test fun joinsLatinLinesWithSpace() = assertEquals("I can't believe it", OcrPostProcessor.joinLines(listOf("I can't", "believe it")))
    @Test fun dehyphenates() = assertEquals("example text", OcrPostProcessor.joinLines(listOf("exam-", "ple text")))
    @Test fun joinsCjkWithoutSpace() = assertEquals("お前は何を言っている", OcrPostProcessor.joinLines(listOf("お前は", "何を言っている")))

    @Test fun fixesCommonErrors() {
        assertEquals("I said go!", OcrPostProcessor.fixCommonErrors("| said go !"))
        assertEquals("good", OcrPostProcessor.fixCommonErrors("g0od"))
    }

    @Test fun noiseDetection() {
        assertTrue(OcrPostProcessor.isNoise("..."))
        assertTrue(OcrPostProcessor.isNoise(" 1 "))
        assertFalse(OcrPostProcessor.isNoise("ド")) // مؤثر صوتي ياباني
        assertFalse(OcrPostProcessor.isNoise("Hi!"))
    }

    @Test fun mergesLinesOfSameBubble() {
        val blocks = listOf(
            OcrBlock("WHAT ARE", Box(100f, 100f, 220f, 125f)),
            OcrBlock("YOU DOING?", Box(95f, 130f, 230f, 155f)),
            OcrBlock("Far away", Box(600f, 900f, 700f, 930f)),
        )
        val out = OcrPostProcessor.process(blocks)
        assertEquals(2, out.size)
        assertEquals("WHAT ARE YOU DOING?", out[0].text)
        assertEquals(Box(95f, 100f, 230f, 155f), out[0].box)
    }
}
