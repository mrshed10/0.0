package com.deepsearch.hub.browser

import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.deepsearch.hub.dns.DohClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * يجلب طلبات GET الخاصة بالـ WebView عبر OkHttp مع DNS-over-HTTPS،
 * فيتجاوز فلترة/تسميم DNS حتى بدون تفعيل VPN-DNS.
 * يعيد null عند الفشل ليكمل الـ WebView التحميل بطريقته العادية.
 */
object DohFetcher {

    private val SKIP_REQUEST_HEADERS = setOf("accept-encoding", "cookie", "host", "content-length")
    private val SKIP_RESPONSE_HEADERS = setOf("content-encoding", "content-length", "set-cookie", "transfer-encoding", "connection")

    fun fetch(request: WebResourceRequest): WebResourceResponse? = try {
        val url = request.url.toString()
        val builder = Request.Builder().url(url).get()
        request.requestHeaders.forEach { (k, v) ->
            if (k.lowercase() !in SKIP_REQUEST_HEADERS) builder.header(k, v)
        }
        val cookies = CookieManager.getInstance()
        cookies.getCookie(url)?.let { builder.header("Cookie", it) }

        // الإطار الرئيسي لا يتبع التحويلات تلقائياً كي يتحدث شريط العنوان
        val client = DohClient.httpClient(followRedirects = !request.isForMainFrame)
        val resp = client.newCall(builder.build()).execute()
        resp.headers("Set-Cookie").forEach { cookies.setCookie(url, it) }

        if (resp.isRedirect) {
            val location = resp.header("Location")
            val target = location?.let { resp.request.url.resolve(it)?.toString() }
            resp.close()
            target?.let { redirectPage(it) }
        } else {
            val body = resp.body
            if (body == null) {
                resp.close()
                null
            } else {
                val ct = body.contentType()
                val mime = ct?.let { "${it.type}/${it.subtype}" } ?: guessMime(url)
                val charset = ct?.charset()?.name()
                val headers = HashMap<String, String>()
                resp.headers.names().forEach { name ->
                    if (name.lowercase() !in SKIP_RESPONSE_HEADERS) headers[name] = resp.headers(name).joinToString(", ")
                }
                val reason = resp.message.ifBlank { "OK" }
                WebResourceResponse(mime, charset, resp.code, reason, headers, body.byteStream())
            }
        }
    } catch (e: Exception) {
        null
    }

    /** WebResourceResponse لا يقبل أكواد 3xx، لذلك نحول بصفحة صغيرة تستبدل العنوان. */
    private fun redirectPage(target: String): WebResourceResponse {
        val q = JSONObject.quote(target)
        val html = "<html><head><meta name=\"referrer\" content=\"no-referrer\"><script>location.replace($q);</script></head><body></body></html>"
        return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray()))
    }

    private fun guessMime(url: String): String {
        val ext = MimeTypeMap.getFileExtensionFromUrl(url)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
