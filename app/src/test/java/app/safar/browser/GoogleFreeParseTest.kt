package app.safar.browser

import app.safar.browser.translate.providers.GoogleFreeProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleFreeParseTest {
    @Test fun parsesSentencesAndLanguage() {
        val body = """[[["ماذا تفعل؟\n","What are you doing?\n",null,null,10],["لا شيء","Nothing",null,null,10]],null,"en",null,null,null,1.0]"""
        val (text, lang) = GoogleFreeProvider.parse(body)
        assertEquals("ماذا تفعل؟\nلا شيء", text)
        assertEquals("en", lang)
    }

    @Test fun chunksRespectLimit() {
        val chunks = GoogleFreeProvider.chunk(listOf("a".repeat(30), "b".repeat(30), "c".repeat(30)), 64)
        assertEquals(listOf(2, 1), chunks.map { it.size })
    }
}
