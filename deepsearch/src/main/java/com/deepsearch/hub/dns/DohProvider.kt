package com.deepsearch.hub.dns

import com.deepsearch.hub.Prefs
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * مزوّد DNS آمن.
 * @param url       عنوان DoH (RFC 8484).
 * @param bootstrap عناوين IP ثابتة لخادم DoH نفسه حتى لا نحتاج DNS النظام للوصول إليه.
 * @param dotHost   اسم المضيف لميزة "Private DNS" في إعدادات أندرويد (DNS-over-TLS).
 */
data class DohProvider(
    val id: String,
    val name: String,
    val url: String,
    val bootstrap: List<String>,
    val dotHost: String?,
    val note: String,
) {
    val host: String get() = url.toHttpUrlOrNull()?.host ?: ""
}

object DohProviders {
    const val CUSTOM_ID = "custom"

    val ALL = listOf(
        DohProvider(
            "cloudflare", "Cloudflare 1.1.1.1", "https://cloudflare-dns.com/dns-query",
            listOf("1.1.1.1", "1.0.0.1"), "one.one.one.one", "سريع جداً ولا يحتفظ بسجلات"
        ),
        DohProvider(
            "google", "Google Public DNS", "https://dns.google/dns-query",
            listOf("8.8.8.8", "8.8.4.4"), "dns.google", "موثوق ومتوفر عالمياً"
        ),
        DohProvider(
            "quad9", "Quad9", "https://dns.quad9.net/dns-query",
            listOf("9.9.9.9", "149.112.112.112"), "dns.quad9.net", "يحجب النطاقات الخبيثة والتصيّد"
        ),
        DohProvider(
            "adguard", "AdGuard DNS", "https://dns.adguard-dns.com/dns-query",
            listOf("94.140.14.14", "94.140.15.15"), "dns.adguard-dns.com", "يحجب الإعلانات والمتتبعات على مستوى DNS"
        ),
        DohProvider(
            "mullvad", "Mullvad DNS", "https://dns.mullvad.net/dns-query",
            listOf("194.242.2.2"), "dns.mullvad.net", "تركيز على الخصوصية"
        ),
    )

    fun byId(id: String): DohProvider? = ALL.firstOrNull { it.id == id }

    /** المزوّد الحالي من الإعدادات؛ يرجع إلى Cloudflare إذا كان الرابط المخصص غير صالح. */
    fun current(): DohProvider {
        val id = Prefs.dohProviderId
        if (id == CUSTOM_ID) {
            val custom = Prefs.customDohUrl
            val parsed = custom.toHttpUrlOrNull()
            if (parsed != null && parsed.isHttps) {
                return DohProvider(CUSTOM_ID, "Custom", custom, emptyList(), null, parsed.host)
            }
        }
        return byId(id) ?: ALL.first()
    }
}
