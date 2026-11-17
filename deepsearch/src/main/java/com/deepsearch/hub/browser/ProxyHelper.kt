package com.deepsearch.hub.browser

import androidx.core.content.ContextCompat
import android.content.Context
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature

/**
 * بروكسي اختياري لكل WebViews في التطبيق (يتجاوز الحجب القائم على IP/SNI وليس DNS فقط).
 * أمثلة: socks5://127.0.0.1:9050 (Orbot/Tor) — http://10.0.0.5:8080
 */
object ProxyHelper {

    val isSupported: Boolean get() = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    /** يعيد null عند النجاح أو رسالة الخطأ. */
    fun apply(context: Context, rule: String): String? {
        if (!isSupported) return if (rule.isBlank()) null else "PROXY_OVERRIDE not supported by this WebView"
        val executor = ContextCompat.getMainExecutor(context)
        return try {
            if (rule.isBlank()) {
                ProxyController.getInstance().clearProxyOverride(executor) {}
            } else {
                val config = ProxyConfig.Builder()
                    .addProxyRule(rule.trim())
                    .addDirect() // fallback إذا تعذر البروكسي
                    .build()
                ProxyController.getInstance().setProxyOverride(config, executor) {}
            }
            null
        } catch (e: IllegalArgumentException) {
            e.message ?: "Invalid proxy rule"
        }
    }
}
