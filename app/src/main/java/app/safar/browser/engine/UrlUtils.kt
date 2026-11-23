package app.safar.browser.engine

import java.net.URI
import java.net.URLEncoder

object UrlUtils {
    private val knownSchemes = listOf("http://", "https://", "about:", "file://", "data:", "moz-extension://", "view-source:")
    private val hostLike = Regex("""^(localhost|[\p{L}\p{N}-]+(\.[\p{L}\p{N}-]+)+|\d{1,3}(\.\d{1,3}){3}|\[[0-9a-fA-F:]+])(:\d{1,5})?([/?#].*)?$""")

    /** يحوّل مدخل شريط العنوان إلى رابط، أو إلى رابط بحث وفق قالب المحرك (%s). */
    fun toUrlOrSearch(input: String, searchTemplate: String): String {
        val t = input.trim()
        if (t.isEmpty()) return "about:blank"
        if (knownSchemes.any { t.startsWith(it, ignoreCase = true) } && !t.contains(' ')) return t
        if (!t.contains(' ') && hostLike.matches(t)) {
            val local = t.startsWith("localhost") || t.matches(Regex("""^(10|127|192\.168|172\.(1[6-9]|2\d|3[01]))\..*"""))
            return (if (local) "http://" else "https://") + t
        }
        return searchTemplate.replace("%s", URLEncoder.encode(t, "UTF-8"))
    }

    fun host(url: String?): String? = runCatching {
        URI(url ?: return null).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()

    /** عرض مختصر في شريط العنوان: النطاق فقط. */
    fun display(url: String): String = host(url) ?: url

    fun isHttps(url: String?) = url?.startsWith("https://", ignoreCase = true) == true

    /** هل المضيف مطابق لاستثناء (يشمل النطاقات الفرعية). */
    fun matchesException(host: String?, exceptions: Collection<String>): Boolean {
        if (host == null) return false
        return exceptions.any { ex -> host == ex || host.endsWith(".$ex") }
    }
}
