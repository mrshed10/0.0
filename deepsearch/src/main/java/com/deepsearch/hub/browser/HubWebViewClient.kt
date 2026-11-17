package com.deepsearch.hub.browser

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.deepsearch.hub.Prefs
import com.deepsearch.hub.dns.DnsVpnService
import java.io.ByteArrayInputStream

/** عميل WebView مشترك: حجب الإعلانات + DoH + فتح الروابط غير الويب في التطبيقات المناسبة. */
open class HubWebViewClient(
    private val onStarted: (String) -> Unit = {},
    private val onFinished: (String) -> Unit = {},
    private val onHistory: () -> Unit = {},
    private val onAdBlocked: () -> Unit = {},
) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url
        val scheme = uri.scheme ?: return null
        if (scheme != "http" && scheme != "https") return null

        if (Prefs.adBlockEnabled && !request.isForMainFrame && AdBlocker.isBlocked(uri.host)) {
            view.post { onAdBlocked() }
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }
        // إذا كان VPN-DNS يعمل فالنظام كله يستخدم DoH أصلاً — لا داعي للاعتراض.
        if (!Prefs.browserUseDoh || DnsVpnService.isRunning || request.method != "GET") return null
        return DohFetcher.fetch(request)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        return when (uri.scheme) {
            "http", "https", "about", "data", "blob", "javascript" -> false
            else -> {
                openExternal(view, uri)
                true
            }
        }
    }

    private fun openExternal(view: WebView, uri: Uri) {
        val ctx = view.context
        try {
            val intent = if (uri.scheme == "intent") {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                    selector = null
                }
            } else Intent(Intent.ACTION_VIEW, uri)
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // رابط intent:// مع fallback لصفحة ويب
            if (uri.scheme == "intent") {
                runCatching {
                    Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                        .getStringExtra("browser_fallback_url")
                }.getOrNull()?.let { view.loadUrl(it) }
            }
        } catch (_: Exception) {
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = onStarted(url)

    override fun onPageFinished(view: WebView, url: String) = onFinished(url)

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) = onHistory()
}
