package com.livetranslate.overlay.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptFilterTest {

    @Test
    fun detectsScripts() {
        assertTrue(ScriptFilter.containsScript("겨울철 피부 보습 관리법", Script.KOREAN))
        assertFalse(ScriptFilter.containsScript("@SeoulfulSkincare", Script.KOREAN))
        assertTrue(ScriptFilter.containsScript("ありがとう", Script.JAPANESE))
        assertTrue(ScriptFilter.containsScript("你好世界", Script.CHINESE))
        assertTrue(ScriptFilter.containsScript("नमस्ते", Script.DEVANAGARI))
        assertTrue(ScriptFilter.containsScript("Bonjour", Script.LATIN))
    }

    @Test
    fun skipsNoise() {
        assertFalse(ScriptFilter.isWorthTranslating("9:30"))
        assertFalse(ScriptFilter.isWorthTranslating("@queenshighway88"))
        assertFalse(ScriptFilter.isWorthTranslating("#서울"))
        assertFalse(ScriptFilter.isWorthTranslating("https://example.com"))
        assertFalse(ScriptFilter.isWorthTranslating("  "))
        assertTrue(ScriptFilter.isWorthTranslating("오예!"))
        assertTrue(ScriptFilter.isWorthTranslating("From Korea with Love"))
    }

    @Test
    fun mapsSourceToRecognizer() {
        assertEquals(Script.KOREAN, Languages.scriptOf("ko"))
        assertEquals(Script.LATIN, Languages.scriptOf(Languages.AUTO))
        assertEquals(Script.LATIN, Languages.scriptOf("fr"))
        assertTrue(Languages.joinsWithoutSpaces(Script.JAPANESE))
        assertFalse(Languages.joinsWithoutSpaces(Script.KOREAN))
    }
}
