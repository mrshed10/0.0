package app.safar.browser

import app.safar.browser.data.ContentKind
import app.safar.browser.data.ProviderId
import app.safar.browser.translate.ProviderException
import app.safar.browser.translate.ProviderRouter
import app.safar.browser.translate.TranslationProvider
import app.safar.browser.translate.TranslationRequest
import app.safar.browser.translate.TranslationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderRouterTest {
    private class Fake(
        override val id: ProviderId,
        val configured: Boolean = true,
        val fail: Boolean = false,
        val langs: Set<String>? = null,
    ) : TranslationProvider {
        var calls = 0
        override fun isConfigured() = configured
        override fun supports(source: String, target: String) = langs == null || target in langs
        override suspend fun translate(req: TranslationRequest): TranslationResult {
            calls++
            if (fail) throw ProviderException("boom")
            return TranslationResult(req.texts.map { "[$id]$it" }, "en", id)
        }
    }

    private fun router(vararg p: Fake) = ProviderRouter(p.associateBy { it.id })
    private fun all() = arrayOf(
        Fake(ProviderId.ON_DEVICE), Fake(ProviderId.GOOGLE), Fake(ProviderId.DEEPL, langs = setOf("ar", "en")),
        Fake(ProviderId.CLAUDE), Fake(ProviderId.GEMINI),
    )

    private fun req(texts: List<String>, kind: ContentKind = ContentKind.TEXT, target: String = "ar") =
        TranslationRequest(texts, "auto", target, kind)

    @Test fun shortUiTextPrefersGoogle() {
        assertEquals(ProviderId.GOOGLE, router(*all()).plan(req(listOf("Home", "Settings", "Log in")), ProviderId.AUTO).first())
    }

    @Test fun longParagraphsPreferDeepL() {
        val p = "This is a fairly long paragraph of article text that should be translated with careful phrasing by a high quality engine."
        assertEquals(ProviderId.DEEPL, router(*all()).plan(req(listOf(p, p)), ProviderId.AUTO).first())
    }

    @Test fun deeplSkippedForUnsupportedTarget() {
        val p = "This is a fairly long paragraph of article text that should be translated with careful phrasing by a high quality engine."
        assertEquals(ProviderId.GOOGLE, router(*all()).plan(req(listOf(p), target = "ur"), ProviderId.AUTO).first())
    }

    @Test fun dialogueGoesToLlm() {
        val lines = listOf("\"Where do you think you're going, huh?!\"", "\"None of your business, man…\"", "— Seriously?!")
        assertEquals(ProviderId.CLAUDE, router(*all()).plan(req(lines), ProviderId.AUTO).first())
    }

    @Test fun mangaGoesToLlm() {
        assertEquals(ProviderId.CLAUDE, router(*all()).plan(req(listOf("なにっ!?"), ContentKind.MANGA), ProviderId.AUTO).first())
    }

    @Test fun unconfiguredProvidersSkipped() {
        val r = router(Fake(ProviderId.ON_DEVICE), Fake(ProviderId.CLAUDE, configured = false), Fake(ProviderId.GOOGLE, configured = false))
        assertEquals(listOf(ProviderId.ON_DEVICE), r.plan(req(listOf("x y"), ContentKind.MANGA), ProviderId.AUTO))
    }

    @Test fun explicitPreferenceFirst() {
        assertEquals(ProviderId.GEMINI, router(*all()).plan(req(listOf("Home")), ProviderId.GEMINI).first())
    }

    @Test fun fallsBackOnFailure() = runTest {
        val claude = Fake(ProviderId.CLAUDE, fail = true)
        val gemini = Fake(ProviderId.GEMINI)
        val res = router(claude, gemini, Fake(ProviderId.ON_DEVICE)).translate(req(listOf("やめろ!"), ContentKind.MANGA), ProviderId.AUTO)
        assertEquals(ProviderId.GEMINI, res.provider)
        assertEquals(1, claude.calls)
    }

    @Test(expected = ProviderException::class)
    fun allFail() = runTest {
        router(Fake(ProviderId.ON_DEVICE, fail = true)).translate(req(listOf("a b")), ProviderId.AUTO)
    }
}
