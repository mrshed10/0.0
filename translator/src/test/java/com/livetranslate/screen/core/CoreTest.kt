package com.livetranslate.screen.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageRouterTest {
    @Test fun autoSourceTranslatesToArabic() {
        assertEquals(Route("en", "ar"), LanguageRouter.route("en", "auto", "ar", "en", true))
        assertEquals(Route("ja", "ar"), LanguageRouter.route("ja", "auto", "ar", "en", true))
    }

    @Test fun arabicSourceIsReversedWhenBidirectional() {
        assertEquals(Route("ar", "en"), LanguageRouter.route("ar", "auto", "ar", "en", true))
        assertNull(LanguageRouter.route("ar", "auto", "ar", "en", false))
    }

    @Test fun unknownOrRomanizedIsSkipped() {
        assertNull(LanguageRouter.route("und", "auto", "ar", "en", true))
        assertNull(LanguageRouter.route("ja-Latn", "auto", "ar", "en", true))
        assertNull(LanguageRouter.route(null, "auto", "ar", "en", true))
    }

    @Test fun fixedSourceIgnoresDetection() {
        assertEquals(Route("fr", "ar"), LanguageRouter.route("en", "fr", "ar", "en", true))
    }

    @Test fun legacyCodesNormalized() {
        assertEquals("he", LanguageRouter.normalize("iw"))
        assertEquals("zh", LanguageRouter.normalize("zh-TW"))
    }
}

class ReadingOrderTest {
    private fun b(l: Int, t: Int, r: Int, bt: Int, s: String) = SourceBlock(s, Box(l, t, r, bt))

    @Test fun mangaRightToLeftThenTopToBottom() {
        val blocks = listOf(
            b(10, 10, 60, 60, "top-left"),
            b(200, 15, 260, 70, "top-right"),
            b(100, 300, 160, 350, "bottom"),
        )
        val order = ReadingOrder.sort(blocks, rtl = true) { it.box }.map { it.text }
        assertEquals(listOf("top-right", "top-left", "bottom"), order)
    }

    @Test fun comicLeftToRight() {
        val blocks = listOf(b(200, 15, 260, 70, "right"), b(10, 10, 60, 60, "left"))
        assertEquals(listOf("left", "right"), ReadingOrder.sort(blocks, rtl = false) { it.box }.map { it.text })
    }

    @Test fun nearbyLinesMergeIntoOneBubble() {
        val blocks = listOf(
            SourceBlock("HELLO", Box(100, 100, 200, 120), listOf(Box(100, 100, 200, 120))),
            SourceBlock("THERE", Box(105, 124, 195, 144), listOf(Box(105, 124, 195, 144))),
            SourceBlock("FAR", Box(600, 600, 660, 620), listOf(Box(600, 600, 660, 620))),
        )
        val grouped = BlockGrouper.group(blocks, rtl = false, cjk = false)
        assertEquals(2, grouped.size)
        val merged = grouped.first { it.text.contains("HELLO") }
        assertEquals("HELLO THERE", merged.text)
        assertEquals(Box(100, 100, 200, 144), merged.box)
    }
}

class TextToolsTest {
    @Test fun translatable() {
        assertTrue(TextTools.isTranslatable("Hello"))
        assertTrue(TextTools.isTranslatable("猫"))
        assertFalse(TextTools.isTranslatable("12:45"))
        assertFalse(TextTools.isTranslatable("  "))
    }

    @Test fun joinLinesHandlesHyphensAndCjk() {
        assertEquals("translation works", TextTools.joinLines(listOf("transla-", "tion works"), false))
        assertEquals("こんにちは世界", TextTools.joinLines(listOf("こんにちは", "世界"), true))
    }

    @Test fun rtl() {
        assertTrue(TextTools.isRtlLanguage("ar"))
        assertFalse(TextTools.isRtlLanguage("en"))
    }
}

class CacheAndSignatureTest {
    @Test fun lruEvictsOldest() {
        val c = TranslationCache(2)
        c.put("en", "ar", "a", "أ"); c.put("en", "ar", "b", "ب")
        assertNotNull(c.get("en", "ar", "a")) // يجعل "a" الأحدث استخداماً
        c.put("en", "ar", "c", "ج")
        assertNull(c.get("en", "ar", "b"))
        assertEquals("أ", c.get("en", "ar", "a"))
    }

    @Test fun signatureIgnoresMaskedCells() {
        val zone = Box(0, 0, 100, 100)
        val mask = listOf(Box(0, 0, 50, 100))
        val a = FrameSignature.compute(zone, 4, 4, mask) { _, _ -> 10 }
        val b = FrameSignature.compute(zone, 4, 4, mask) { x, _ -> if (x < 50) 250 else 10 }
        assertEquals(0f, FrameSignature.difference(a, b), 0.0001f)
        val c = FrameSignature.compute(zone, 4, 4, mask) { x, _ -> if (x >= 50) 250 else 10 }
        assertEquals(1f, FrameSignature.difference(a, c), 0.0001f)
    }

    @Test fun clampKeepsBubbleOnScreen() {
        val screen = Box(0, 0, 1000, 2000)
        assertEquals(Box(800, 1900, 1000, 2000), Box(950, 1950, 1150, 2050).clampInto(screen, 100, 100))
    }
}
