package app.safar.browser

import app.safar.browser.data.BrowserSettings
import app.safar.browser.data.DnsProvider
import app.safar.browser.data.HistoryEntry
import app.safar.browser.data.HistoryLogic
import app.safar.browser.data.QuickAction
import app.safar.browser.data.SecureDnsMode
import app.safar.browser.data.dohUri
import app.safar.browser.data.effectiveQuickAction
import app.safar.browser.data.isValidDohUri
import app.safar.browser.extensions.ChromeStore
import app.safar.browser.extensions.ChromeStoreBridge
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ChromeFeaturesTest {
    private val id = "cjpalhdlnbpafiamejdnhcphjbkeiagm" // uBlock Origin

    @Test fun extractsIdFromNewAndOldStoreUrls() {
        assertEquals(id, ChromeStore.extensionId("https://chromewebstore.google.com/detail/ublock-origin/$id?hl=ar"))
        assertEquals(id, ChromeStore.extensionId("https://chrome.google.com/webstore/detail/ublock-origin/$id"))
        assertNull(ChromeStore.extensionId(ChromeStore.HOME_URL))
        assertNull(ChromeStore.extensionId("https://example.com/detail/x/$id"))
    }

    private fun zipWith(manifest: String): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z -> z.putNextEntry(ZipEntry("manifest.json")); z.write(manifest.toByteArray()); z.closeEntry() }
        return bos.toByteArray()
    }

    private fun crx3(zip: ByteArray, headerLen: Int = 37): ByteArray {
        val bb = ByteBuffer.allocate(12 + headerLen + zip.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("Cr24".toByteArray()).putInt(3).putInt(headerLen).put(ByteArray(headerLen)).put(zip)
        return bb.array()
    }

    private fun crx2(zip: ByteArray): ByteArray {
        val bb = ByteBuffer.allocate(16 + 10 + 20 + zip.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("Cr24".toByteArray()).putInt(2).putInt(10).putInt(20).put(ByteArray(30)).put(zip)
        return bb.array()
    }

    @Test fun readsManifestFromCrx3AndCrx2() {
        val m = """{ "name": "Test", "version": "1.2", "manifest_version": 3 }"""
        assertEquals("Test", ChromeStore.analyze(ChromeStore.readManifest(crx3(zipWith(m)))).name)
        assertEquals("1.2", ChromeStore.analyze(ChromeStore.readManifest(crx2(zipWith(m)))).version)
    }

    @Test fun manifestWithCommentsParses() {
        val m = "{\n // comment\n \"name\": \"A\", \"version\": \"1\", \"homepage_url\": \"https://x.y/z\" }"
        assertEquals("A", ChromeStore.analyze(ChromeStore.readManifest(crx3(zipWith(m)))).name)
    }

    private fun analyze(json: String) = ChromeStore.analyze(Json.parseToJsonElement(json).jsonObject)

    @Test fun compatibilityLevels() {
        assertEquals(ChromeStore.Level.COMPATIBLE, analyze("""{"name":"a","manifest_version":2,"permissions":["storage","tabs"]}""").level)
        assertEquals(ChromeStore.Level.PARTIAL, analyze("""{"name":"a","manifest_version":3,"permissions":["bookmarks"]}""").level)
        assertEquals(ChromeStore.Level.INCOMPATIBLE, analyze("""{"name":"a","manifest_version":3,"permissions":["offscreen"]}""").level)
        assertEquals(ChromeStore.Level.INCOMPATIBLE, analyze("""{"name":"a","manifest_version":3,"background":{"service_worker":"sw.js"}}""").level)
        assertEquals(ChromeStore.Level.COMPATIBLE, analyze("""{"name":"a","manifest_version":3,"background":{"service_worker":"sw.js","scripts":["bg.js"]}}""").level)
    }

    @Test fun localizedNameFallsBackToPageTitle() =
        assertEquals("uBlock Origin", ChromeStore.displayName("__MSG_extName__", "uBlock Origin - Chrome Web Store"))

    @Test fun parsesAmoSearch() {
        val body = """{"results":[{"slug":"ublock-origin","name":{"en-US":"uBlock Origin"},"average_daily_users":9000000}]}"""
        val r = ChromeStoreBridge.parseAmo(body)
        assertEquals(1, r.size)
        assertEquals("uBlock Origin", r[0].name)
        assertEquals("https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi", r[0].xpiUrl)
    }

    @Test fun secureDnsUris() {
        assertNull(BrowserSettings().dohUri())
        assertEquals(DnsProvider.CLOUDFLARE.uri, BrowserSettings(secureDns = SecureDnsMode.AUTOMATIC).dohUri())
        assertEquals(DnsProvider.GOOGLE.uri, BrowserSettings(secureDns = SecureDnsMode.PROVIDER, dnsProvider = DnsProvider.GOOGLE).dohUri())
        assertEquals("https://dns.example.net/dns-query",
            BrowserSettings(secureDns = SecureDnsMode.PROVIDER, dnsProvider = DnsProvider.CUSTOM, customDnsUri = "https://dns.example.net/dns-query").dohUri())
        assertNull(BrowserSettings(secureDns = SecureDnsMode.PROVIDER, dnsProvider = DnsProvider.CUSTOM, customDnsUri = "http://bad").dohUri())
        assertTrue(isValidDohUri("https://doh.example:8443/q"))
        assertFalse(isValidDohUri("dns.google"))
    }

    @Test fun quickActionByUsage() {
        assertEquals(QuickAction.NEW_TAB, BrowserSettings().effectiveQuickAction())
        assertEquals(QuickAction.TRANSLATE, BrowserSettings(actionUsage = mapOf("SHARE" to 2, "TRANSLATE" to 7)).effectiveQuickAction())
        assertEquals(QuickAction.SHARE, BrowserSettings(quickAction = QuickAction.SHARE, actionUsage = mapOf("TRANSLATE" to 7)).effectiveQuickAction())
    }

    @Test fun historyDedupesAndSearches() {
        var h = emptyList<HistoryEntry>()
        h = HistoryLogic.add(h, HistoryEntry("https://a.com/1", "A", 1))
        h = HistoryLogic.add(h, HistoryEntry("https://b.com/", "B", 2))
        h = HistoryLogic.add(h, HistoryEntry("https://a.com/1", "A again", 3))
        assertEquals(listOf("https://a.com/1", "https://b.com/"), h.map { it.url })
        assertEquals(1, HistoryLogic.search(h, "b.com").size)
        assertEquals("a.com", java.net.URI(HistoryLogic.topSites(h + HistoryEntry("https://a.com/2", "A2", 4), 1)[0].url).host)
    }
}
