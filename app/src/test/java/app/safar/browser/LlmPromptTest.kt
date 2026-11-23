package app.safar.browser

import app.safar.browser.data.ContentKind
import app.safar.browser.translate.LlmPrompt
import app.safar.browser.translate.ProviderException
import app.safar.browser.translate.TranslationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmPromptTest {
    private val orig = listOf("Hello", "World", "Bye")

    @Test fun parsesPlainArray() = assertEquals(listOf("مرحبا", "عالم", "وداعا"), LlmPrompt.parse("""["مرحبا","عالم","وداعا"]""", orig))

    @Test fun parsesFencedArrayWithChatter() {
        val raw = "Here you go:\n```json\n[\"أ\", \"ب\", \"ج\"]\n```"
        assertEquals(listOf("أ", "ب", "ج"), LlmPrompt.parse(raw, orig))
    }

    @Test fun padsMissingWithOriginals() = assertEquals(listOf("أ", "World", "Bye"), LlmPrompt.parse("""["أ"]""", orig))

    @Test fun truncatesExtra() = assertEquals(3, LlmPrompt.parse("""["1","2","3","4"]""", orig).size)

    @Test(expected = ProviderException::class)
    fun rejectsNoArray() { LlmPrompt.parse("sorry", orig) }

    @Test fun mangaSystemPromptMentionsSfxAndCount() {
        val sys = LlmPrompt.system(TranslationRequest(orig, "ja", "ar", ContentKind.MANGA, instructions = "Luffy=لوفي"))
        assertTrue(sys.contains("Sound effects"))
        assertTrue(sys.contains("exactly 3 items"))
        assertTrue(sys.contains("Luffy=لوفي"))
    }

    @Test fun userPayloadIsJson() {
        val u = LlmPrompt.user(TranslationRequest(listOf("a\"b"), "auto", "ar", ContentKind.TEXT, context = "ctx"))
        assertTrue(u.startsWith("{") && u.contains("\"segments\"") && u.contains("a\\\"b"))
    }
}
