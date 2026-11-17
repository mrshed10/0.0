package com.tvbrowser.aosp.extensions

import android.net.Uri

/**
 * Understands every place an extension can come from and turns it into something installable.
 *
 * This is the GeckoView equivalent of a "custom WebViewClient for extensions":
 * TabManager.onLoadRequest / onNewSession call [interceptDownload] for every navigation,
 * and MainActivity calls [fromStorePage] to show an "Install" button on store pages.
 */
sealed class ExtensionSource {
    /** Firefox add-on (.xpi). Signed by Mozilla when it comes from AMO. */
    data class Xpi(val url: String) : ExtensionSource()

    /** Chromium package (.crx): Chrome Web Store (Chrome, Brave, Kiwi), Edge, or a direct link. */
    data class Crx(val url: String, val chromeId: String?) : ExtensionSource()
}

object ExtensionSources {

    private val AMO_PAGE = Regex("^https?://addons\\.mozilla\\.org/(?:[^/]+/)?(?:firefox|android)/addon/([^/?#]+)", RegexOption.IGNORE_CASE)
    private val CWS_PAGE = Regex("^https?://chromewebstore\\.google\\.com/detail/(?:[^/]+/)?([a-p]{32})", RegexOption.IGNORE_CASE)
    private val CWS_OLD_PAGE = Regex("^https?://chrome\\.google\\.com/webstore/detail/(?:[^/]+/)?([a-p]{32})", RegexOption.IGNORE_CASE)
    private val EDGE_PAGE = Regex("^https?://microsoftedge\\.microsoft\\.com/addons/detail/(?:[^/]+/)?([a-p]{32})", RegexOption.IGNORE_CASE)
    private val CHROME_ID = Regex("^[a-p]{32}$")

    /** Chromium version reported to the Chrome/Edge update servers. */
    private const val CHROME_PROD_VERSION = "131.0.0.0"

    fun amoLatestXpi(slug: String) = "https://addons.mozilla.org/firefox/downloads/latest/$slug/latest.xpi"

    fun chromeStoreCrx(id: String) =
        "https://clients2.google.com/service/update2/crx?response=redirect&prodversion=$CHROME_PROD_VERSION" +
            "&acceptformat=crx2,crx3&x=id%3D$id%26installsource%3Dondemand%26uc"

    fun edgeStoreCrx(id: String) =
        "https://edge.microsoft.com/extensionwebstorebase/v1/crx?response=redirect&prodversion=$CHROME_PROD_VERSION" +
            "&x=id%3D$id%26installsource%3Dondemand%26uc"

    /** A store *detail page* the user is looking at -> what to install. */
    fun fromStorePage(url: String): ExtensionSource? {
        AMO_PAGE.find(url)?.let { return ExtensionSource.Xpi(amoLatestXpi(it.groupValues[1])) }
        (CWS_PAGE.find(url) ?: CWS_OLD_PAGE.find(url))?.let {
            val id = it.groupValues[1].lowercase()
            return ExtensionSource.Crx(chromeStoreCrx(id), id)
        }
        EDGE_PAGE.find(url)?.let {
            val id = it.groupValues[1].lowercase()
            return ExtensionSource.Crx(edgeStoreCrx(id), id)
        }
        return null
    }

    /** A navigation that is actually a package download -> install instead of navigating. */
    fun interceptDownload(url: String): ExtensionSource? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path?.lowercase().orEmpty()
        return when {
            path.endsWith(".xpi") -> ExtensionSource.Xpi(url)
            host == "addons.mozilla.org" && (path.contains("/downloads/file/") || path.contains("/downloads/latest/")) ->
                ExtensionSource.Xpi(url)
            path.endsWith(".crx") -> ExtensionSource.Crx(url, null)
            host == "clients2.google.com" && path.startsWith("/service/update2/crx") -> ExtensionSource.Crx(url, null)
            host == "edge.microsoft.com" && path.contains("/extensionwebstorebase/") -> ExtensionSource.Crx(url, null)
            else -> null
        }
    }

    /**
     * Anything the user typed in "Install from URL": store page, direct link,
     * a bare Chrome extension id, or "amo:slug".
     */
    fun fromUserInput(input: String): ExtensionSource? {
        val text = input.trim()
        if (CHROME_ID.matches(text.lowercase())) {
            return ExtensionSource.Crx(chromeStoreCrx(text.lowercase()), text.lowercase())
        }
        if (text.startsWith("amo:", ignoreCase = true)) {
            return ExtensionSource.Xpi(amoLatestXpi(text.substringAfter(':').trim()))
        }
        fromStorePage(text)?.let { return it }
        interceptDownload(text)?.let { return it }
        if (text.startsWith("http://") || text.startsWith("https://")) return ExtensionSource.Xpi(text)
        return null
    }
}
