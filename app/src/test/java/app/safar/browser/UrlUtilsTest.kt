package app.safar.browser

import app.safar.browser.engine.UrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {
    private val ddg = "https://duckduckgo.com/?q=%s"

    @Test fun `domain becomes https url`() = assertEquals("https://example.com", UrlUtils.toUrlOrSearch("example.com", ddg))
    @Test fun `domain with path`() = assertEquals("https://example.com/a?b=1", UrlUtils.toUrlOrSearch("example.com/a?b=1", ddg))
    @Test fun `full url kept`() = assertEquals("http://x.org/p", UrlUtils.toUrlOrSearch("http://x.org/p", ddg))
    @Test fun `localhost uses http`() = assertEquals("http://localhost:8080", UrlUtils.toUrlOrSearch("localhost:8080", ddg))
    @Test fun `lan ip uses http`() = assertEquals("http://192.168.1.1", UrlUtils.toUrlOrSearch("192.168.1.1", ddg))
    @Test fun `words become search`() = assertEquals("https://duckduckgo.com/?q=hello+world", UrlUtils.toUrlOrSearch("hello world", ddg))
    @Test fun `arabic query encoded`() = assertTrue(UrlUtils.toUrlOrSearch("ترجمة مانغا", ddg).startsWith("https://duckduckgo.com/?q=%D8"))
    @Test fun `single word is search`() = assertEquals("https://duckduckgo.com/?q=kotlin", UrlUtils.toUrlOrSearch("kotlin", ddg))
    @Test fun `host strips www`() = assertEquals("example.com", UrlUtils.host("https://www.example.com/x"))

    @Test fun `exceptions match subdomains`() {
        assertTrue(UrlUtils.matchesException("m.example.com", setOf("example.com")))
        assertTrue(UrlUtils.matchesException("example.com", setOf("example.com")))
        assertFalse(UrlUtils.matchesException("badexample.com", setOf("example.com")))
    }
}
