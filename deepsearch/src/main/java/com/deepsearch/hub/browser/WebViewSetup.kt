package com.deepsearch.hub.browser

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.deepsearch.hub.Prefs

object WebViewSetup {

    @SuppressLint("SetJavaScriptEnabled")
    fun apply(webView: WebView, thirdPartyCookies: Boolean = false) {
        with(webView.settings) {
            javaScriptEnabled = Prefs.javaScriptEnabled
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false) // target=_blank يفتح في نفس الصفحة
            allowFileAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = if (Prefs.desktopMode) desktopUserAgent(webView) else null
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, thirdPartyCookies)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, true)
        }
    }

    fun desktopUserAgent(webView: WebView): String {
        val default = WebSettings.getDefaultUserAgent(webView.context)
        return default
            .replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)")
            .replace(" Mobile", "")
            .replace("; wv", "")
    }
}
