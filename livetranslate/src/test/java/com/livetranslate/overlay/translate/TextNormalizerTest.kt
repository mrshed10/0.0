package com.livetranslate.overlay.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun joinsBubbleLinesAndHyphenatedWords() {
        assertEquals("I can't believe it", TextNormalizer.joinLines(listOf("I can't", "believe it"), " "))
        assertEquals("SOMETHING HAPPENED", TextNormalizer.joinLines(listOf("SOME-", "THING HAPPENED"), " "))
        assertEquals("ありがとう", TextNormalizer.joinLines(listOf("あり", "がとう"), ""))
    }

    @Test
    fun fixesComicFontConfusions() {
        assertEquals("GOOD", TextNormalizer.fixOcrConfusions("G00D"))
        assertEquals("HELLO", TextNormalizer.fixOcrConfusions("HELL0"))
        assertEquals("I", TextNormalizer.fixOcrConfusions("|"))
        assertEquals("WILL", TextNormalizer.fixOcrConfusions("W1LL"))
        // أرقام حقيقية لا تُمس
        assertEquals("COVID-19", TextNormalizer.fixOcrConfusions("COVID-19"))
        assertEquals("365", TextNormalizer.fixOcrConfusions("365"))
        assertEquals("A1", TextNormalizer.fixOcrConfusions("A1"))
    }

    @Test
    fun convertsShoutingToSentenceCase() {
        assertTrue(TextNormalizer.isShouting("WHAT ARE YOU DOING?!"))
        assertFalse(TextNormalizer.isShouting("What are you doing?"))
        assertFalse(TextNormalizer.isShouting("OK"))
        assertEquals(
            "What are you doing?! I told you, I'm not going.",
            TextNormalizer.sentenceCase("WHAT ARE YOU DOING?! I TOLD YOU, I'M NOT GOING."),
        )
    }

    @Test
    fun fullPipelineForMangaBubble() {
        val text = TextNormalizer.normalize(listOf("I DON'T THINK", "WE SHOULD G0", "IN THERE..."), " ", latin = true)
        assertEquals("I don't think we should go in there...", text)
    }
}
