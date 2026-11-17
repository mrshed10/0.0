package com.deepsearch.hub.dns

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** عميل DNS-over-HTTPS مشترك بين المتصفح وخدمة VPN-DNS. */
object DohClient {

    private val DNS_MESSAGE = "application/dns-message".toMediaType()

    /** عناوين محلولة مسبقاً لمضيفي DoH المخصصين (بدون bootstrap ثابت). */
    private val resolvedHosts = ConcurrentHashMap<String, List<InetAddress>>()
    private val clients = ConcurrentHashMap<String, OkHttpClient>()

    private val root: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** يحل اسم خادم DoH نفسه من bootstrap دون المرور بـ DNS النظام (يمنع الحلقات داخل VPN). */
    private class BootstrapDns(private val provider: DohProvider) : Dns {
        private val fixed: List<InetAddress> = provider.bootstrap.map { InetAddress.getByName(it) }

        override fun lookup(hostname: String): List<InetAddress> {
            if (hostname.equals(provider.host, ignoreCase = true)) {
                if (fixed.isNotEmpty()) return fixed
                resolvedHosts[hostname.lowercase()]?.let { return it }
            }
            return Dns.SYSTEM.lookup(hostname)
        }
    }

    /** عميل يصل لخادم DoH (يُستخدم لنقل رسائل DNS الخام). */
    private fun transport(p: DohProvider): OkHttpClient =
        clients.getOrPut("t|${p.url}") { root.newBuilder().dns(BootstrapDns(p)).build() }

    private fun doh(p: DohProvider): DnsOverHttps =
        DnsOverHttps.Builder()
            .client(transport(p))
            .url(p.url.toHttpUrl())
            .includeIPv6(false)
            .build()

    /**
     * عميل HTTP كل استعلامات DNS فيه تمر عبر DoH للمزوّد الحالي.
     * @param followRedirects false للإطار الرئيسي في المتصفح كي يبقى شريط العنوان صحيحاً.
     */
    fun httpClient(followRedirects: Boolean = true): OkHttpClient {
        val p = DohProviders.current()
        return clients.getOrPut("h|${p.url}|$followRedirects") {
            transport(p).newBuilder()
                .dns(doh(p))
                .followRedirects(followRedirects)
                .followSslRedirects(followRedirects)
                .build()
        }
    }

    /** لمزوّد مخصص بلا bootstrap: نحل اسمه مرة واحدة قبل تشغيل VPN. */
    fun prewarm(p: DohProvider) {
        if (p.bootstrap.isEmpty() && p.host.isNotEmpty()) {
            runCatching { resolvedHosts[p.host.lowercase()] = InetAddress.getAllByName(p.host).toList() }
        }
    }

    /** يرسل رسالة DNS خام (wire format) ويعيد الرد الخام — RFC 8484 POST. */
    @Throws(IOException::class)
    fun resolveWire(p: DohProvider, query: ByteArray): ByteArray {
        val req = Request.Builder()
            .url(p.url)
            .header("Accept", "application/dns-message")
            .post(query.toRequestBody(DNS_MESSAGE))
            .build()
        transport(p).newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("DoH HTTP ${r.code}")
            return r.body?.bytes() ?: throw IOException("Empty DoH body")
        }
    }

    data class TestResult(val addresses: List<String>, val millis: Long)

    /** اختبار المزوّد بحل اسم معروف. (استدعِها خارج الخيط الرئيسي) */
    fun test(p: DohProvider, host: String = "example.com"): TestResult {
        prewarm(p)
        val start = System.nanoTime()
        val result = doh(p).lookup(host)
        val ms = (System.nanoTime() - start) / 1_000_000
        return TestResult(result.mapNotNull { it.hostAddress }, ms)
    }
}
