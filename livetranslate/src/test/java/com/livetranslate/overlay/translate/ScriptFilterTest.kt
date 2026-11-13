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

class DominantScriptTest {
    @Test
    fun detectsDominantScript() {
        assertEquals(Script.JAPANESE, ScriptFilter.dominantScript("今日はいい天気ですね"))
        assertEquals(Script.CHINESE, ScriptFilter.dominantScript("今天天气很好"))
        assertEquals(Script.KOREAN, ScriptFilter.dominantScript("오늘 날씨 좋네요"))
        assertEquals(Script.DEVANAGARI, ScriptFilter.dominantScript("नमस्ते दुनिया"))
        assertEquals(Script.LATIN, ScriptFilter.dominantScript("Hello world"))
        assertEquals(Script.JAPANESE, ScriptFilter.dominantScript("NARUTO ナルト"))
        assertEquals(null, ScriptFilter.dominantScript("123 !!"))
        assertEquals(Script.CYRILLIC, ScriptFilter.dominantScript("Привет, как дела?"))
        assertEquals(Script.ARABIC, ScriptFilter.dominantScript("مرحبا بالعالم"))
        assertEquals(Script.HEBREW, ScriptFilter.dominantScript("שלום עולם"))
        assertEquals(Script.GREEK, ScriptFilter.dominantScript("Καλημέρα κόσμε"))
        assertEquals(Script.THAI, ScriptFilter.dominantScript("สวัสดีครับ"))
        assertEquals(Script.LATIN, ScriptFilter.dominantScript("Xin chào thế giới"))
    }

    @Test
    fun autoModePreloadsAsianPacks() {
        assertEquals(listOf("ar", "ja", "zh", "ko", "ru"), Languages.modelsFor(Languages.AUTO, "ar"))
        assertEquals(listOf("rus"), Languages.ocrPacksFor(Languages.AUTO))
        assertEquals(listOf("ara"), Languages.ocrPacksFor("ar"))
        assertEquals(emptyList<String>(), Languages.ocrPacksFor("ja"))
        assertEquals(listOf("ar", "fr"), Languages.modelsFor("fr", "ar"))
        assertEquals(listOf("ar"), Languages.modelsFor("en", "ar"))
    }
}

class LanguageTableTest {
    @Test
    fun coversAllTranslationLanguages() {
        assertEquals(59, Languages.all.size)
        assertEquals(Languages.all.size, Languages.all.map { it.code }.toSet().size)
        assertEquals("rus", Languages.tessOf("ru"))
        assertEquals(null, Languages.tessOf("ja"))
        assertEquals(Script.CYRILLIC, Languages.scriptOf("uk"))
    }

    @Test
    fun scriptCertainLanguages() {
        assertEquals("ko", Languages.languageOfScript(Script.KOREAN))
        assertEquals("he", Languages.languageOfScript(Script.HEBREW))
        assertEquals("hi", Languages.languageOfScript(Script.DEVANAGARI))
        assertEquals(null, Languages.languageOfScript(Script.CYRILLIC))
        assertEquals(listOf("ru", "uk", "be", "bg", "mk"), Languages.languagesOfScript(Script.CYRILLIC))
    }
}
