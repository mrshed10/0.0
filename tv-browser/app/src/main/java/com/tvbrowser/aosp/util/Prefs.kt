package com.tvbrowser.aosp.util

import android.content.Context
import android.net.Uri

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("tvbrowser", Context.MODE_PRIVATE)
    val lowRam: Boolean = DeviceProfile.isLowRam(context)

    var homePage: String
        get() = sp.getString(KEY_HOME, DEFAULT_HOME) ?: DEFAULT_HOME
        set(v) = sp.edit().putString(KEY_HOME, v).apply()

    /** 0 = Google, 1 = DuckDuckGo, 2 = Bing */
    var searchEngine: Int
        get() = sp.getInt(KEY_SEARCH, 0)
        set(v) = sp.edit().putInt(KEY_SEARCH, v).apply()

    /** Max number of tabs (open or discarded). */
    var maxTabs: Int
        get() = sp.getInt(KEY_MAX_TABS, if (lowRam) 4 else 8)
        set(v) = sp.edit().putInt(KEY_MAX_TABS, v.coerceIn(1, 12)).apply()

    /** Tabs allowed to keep a live GeckoSession. Others are "discarded" (state only, ~0 RAM). */
    var maxLiveTabs: Int
        get() = sp.getInt(KEY_MAX_LIVE, if (lowRam) 1 else 3)
        set(v) = sp.edit().putInt(KEY_MAX_LIVE, v.coerceIn(1, 6)).apply()

    /** Virtual cursor speed 1..5 */
    var cursorSpeed: Int
        get() = sp.getInt(KEY_CURSOR_SPEED, 3)
        set(v) = sp.edit().putInt(KEY_CURSOR_SPEED, v.coerceIn(1, 5)).apply()

    /** 0 = mouse events (desktop-like, supports hover), 1 = touch events (fallback) */
    var clickMode: Int
        get() = sp.getInt(KEY_CLICK_MODE, CLICK_MOUSE)
        set(v) = sp.edit().putInt(KEY_CLICK_MODE, v).apply()

    var cursorMode: Boolean
        get() = sp.getBoolean(KEY_CURSOR_MODE, true)
        set(v) = sp.edit().putBoolean(KEY_CURSOR_MODE, v).apply()

    /** Allow web pages to call window.tvDevice.* (volume control). */
    var allowPageVolume: Boolean
        get() = sp.getBoolean(KEY_PAGE_VOLUME, true)
        set(v) = sp.edit().putBoolean(KEY_PAGE_VOLUME, v).apply()

    var savedTabs: String?
        get() = sp.getString(KEY_SAVED_TABS, null)
        set(v) = sp.edit().putString(KEY_SAVED_TABS, v).apply()

    var storesJson: String?
        get() = sp.getString(KEY_STORES, null)
        set(v) = sp.edit().putString(KEY_STORES, v).apply()

    /** Turns whatever the user typed in the URL bar into a URL. */
    fun resolveInput(input: String): String {
        val text = input.trim()
        if (text.isEmpty()) return homePage
        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://") ||
            lower.startsWith("about:") || lower.startsWith("file://") ||
            lower.startsWith("resource://") || lower.startsWith("moz-extension://")
        ) return text
        val looksLikeHost = !text.contains(' ') &&
            (text.contains('.') || text.startsWith("localhost")) &&
            !text.endsWith('.')
        if (looksLikeHost) return "https://$text"
        val q = Uri.encode(text)
        return when (searchEngine) {
            1 -> "https://duckduckgo.com/?q=$q"
            2 -> "https://www.bing.com/search?q=$q"
            else -> "https://www.google.com/search?q=$q"
        }
    }

    companion object {
        const val DEFAULT_HOME = "https://www.google.com/"
        const val CLICK_MOUSE = 0
        const val CLICK_TOUCH = 1

        private const val KEY_HOME = "home"
        private const val KEY_SEARCH = "search"
        private const val KEY_MAX_TABS = "max_tabs"
        private const val KEY_MAX_LIVE = "max_live"
        private const val KEY_CURSOR_SPEED = "cursor_speed"
        private const val KEY_CLICK_MODE = "click_mode"
        private const val KEY_CURSOR_MODE = "cursor_mode"
        private const val KEY_PAGE_VOLUME = "page_volume"
        private const val KEY_SAVED_TABS = "saved_tabs"
        private const val KEY_STORES = "stores"
    }
}
