package com.deepsearch.hub.browser

import android.net.Uri
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONTokener

/** يستخرج روابط الفيديو والصور والصفحات (بما فيها الروابط الأصلية المغلّفة داخل نتائج محركات البحث). */
object MediaLinkExtractor {

    enum class Kind { VIDEO, IMAGE, PAGE }

    data class Link(val url: String, val kind: Kind, val label: String) {
        val host: String get() = Uri.parse(url).host ?: ""
    }

    fun extract(webView: WebView, callback: (List<Link>) -> Unit) {
        val script = webView.context.assets.open("extract_links.js").bufferedReader().use { it.readText() }
        val pageHost = Uri.parse(webView.url ?: "").host.orEmpty()
        webView.evaluateJavascript(script) { raw -> callback(parse(raw, pageHost)) }
    }

    fun parse(raw: String?, pageHost: String): List<Link> {
        if (raw.isNullOrEmpty() || raw == "null") return emptyList()
        return try {
            // evaluateJavascript يعيد نصاً مُرمّزاً بـ JSON يحتوي بدوره على مصفوفة JSON
            val json = JSONTokener(raw).nextValue() as? String ?: return emptyList()
            val arr = JSONArray(json)
            val pageBase = baseDomain(pageHost)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val kind = when (o.optString("kind")) {
                    "video" -> Kind.VIDEO
                    "image" -> Kind.IMAGE
                    else -> Kind.PAGE
                }
                val link = Link(o.getString("url"), kind, o.optString("label"))
                // الصفحات الداخلية لمحرك البحث نفسه ليست "نتائج"
                if (kind == Kind.PAGE && baseDomain(link.host) == pageBase) null else link
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun baseDomain(host: String): String = host.split('.').takeLast(2).joinToString(".")
}
