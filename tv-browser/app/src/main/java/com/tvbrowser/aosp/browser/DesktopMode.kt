package com.tvbrowser.aosp.browser

import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/**
 * Permanent desktop mode: every session uses a desktop User-Agent + desktop viewport.
 * There is intentionally no "request mobile site" toggle.
 */
object DesktopMode {

    /** Gecko major version taken from the engine itself so the UA never looks stale. */
    private val geckoMajor: String by lazy {
        val ua = runCatching { GeckoSession.getDefaultUserAgent() }.getOrDefault("")
        Regex("Firefox/(\\d+)").find(ua)?.groupValues?.get(1) ?: "128"
    }

    /** Windows desktop UA gives the most "full PC" layouts (YouTube, Facebook, Google...). */
    val USER_AGENT: String by lazy {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:$geckoMajor.0) Gecko/20100101 Firefox/$geckoMajor.0"
    }

    fun sessionSettings(): GeckoSessionSettings =
        GeckoSessionSettings.Builder()
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
            .userAgentOverride(USER_AGENT)
            .displayMode(GeckoSessionSettings.DISPLAY_MODE_BROWSER)
            .useTrackingProtection(true)
            .suspendMediaWhenInactive(true)
            .allowJavascript(true)
            .build()
}
