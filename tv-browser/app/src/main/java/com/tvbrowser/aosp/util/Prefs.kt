package com.tvbrowser.aosp.util

import android.content.Context
import android.net.Uri

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("tvbrowser", Context.MODE_PRIVATE)
    val lowRam: Boolean = DeviceProfile.isLowRam(context)

    private fun bool(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun putBool(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    private fun int(key: String, def: Int) = sp.getInt(key, def)
    private fun putInt(key: String, v: Int) = sp.edit().putInt(key, v).apply()

    // ---------------- general
    /** QUICK_ACCESS_URL = the native Quick Access screen (costs no engine memory). */
    var homePage: String
        get() = sp.getString("home", QUICK_ACCESS_URL) ?: QUICK_ACCESS_URL
        set(v) = sp.edit().putString("home", v).apply()

    /** 0 = Google, 1 = DuckDuckGo, 2 = Bing */
    var searchEngine: Int
        get() = int("search", 0)
        set(v) = putInt("search", v)

    var restoreTabs: Boolean
        get() = bool("restore_tabs", true)
        set(v) = putBool("restore_tabs", v)

    // ---------------- ad blocking
    /** Engine's built-in tracker/ad-network blocking: native code, almost free on CPU/RAM. */
    var builtInAdBlock: Boolean
        get() = bool("adblock_builtin", true)
        set(v) = putBool("adblock_builtin", v)

    var extensionsEnabled: Boolean
        get() = bool("extensions_enabled", true)
        set(v) = putBool("extensions_enabled", v)

    // ---------------- performance & memory
    var maxTabs: Int
        get() = int("max_tabs", if (lowRam) 3 else 8)
        set(v) = putInt("max_tabs", v.coerceIn(1, 12))

    /** Tabs that keep a live page. Others are frozen (state only, ~0 RAM). */
    var maxLiveTabs: Int
        get() = int("max_live", 1.takeIf { lowRam } ?: 3)
        set(v) = putInt("max_live", v.coerceIn(1, 6))

    /** Periodic cache clearing interval in minutes (0 = off). */
    var cacheClearMinutes: Int
        get() = int("cache_clear_min", 10)
        set(v) = putInt("cache_clear_min", v)

    /** 30 fps rendering: halves CPU/GPU work for animations & scrolling. (restart) */
    var cpuSaver: Boolean
        get() = bool("cpu_saver", lowRam)
        set(v) = putBool("cpu_saver", v)

    /** Make YouTube & co. send H.264 (hardware-decoded on every TV chip) instead of VP9/AV1. (restart) */
    var preferH264: Boolean
        get() = bool("prefer_h264", true)
        set(v) = putBool("prefer_h264", v)

    /** Don't load images at all: huge memory savings on heavy pages. (restart) */
    var blockImages: Boolean
        get() = bool("block_images", false)
        set(v) = putBool("block_images", v)

    // ---------------- data
    var clearOnExit: Boolean
        get() = bool("clear_on_exit", false)
        set(v) = putBool("clear_on_exit", v)

    // ---------------- remote / volume
    var cursorSpeed: Int
        get() = int("cursor_speed", 3)
        set(v) = putInt("cursor_speed", v.coerceIn(1, 5))

    /** 0 = mouse events (desktop-like, supports hover), 1 = touch events (fallback) */
    var clickMode: Int
        get() = int("click_mode", CLICK_MOUSE)
        set(v) = putInt("click_mode", v)

    var cursorMode: Boolean
        get() = bool("cursor_mode", true)
        set(v) = putBool("cursor_mode", v)

    var allowPageVolume: Boolean
        get() = bool("page_volume", true)
        set(v) = putBool("page_volume", v)

    var showSystemVolumeUi: Boolean
        get() = bool("system_volume_ui", false)
        set(v) = putBool("system_volume_ui", v)

    // ---------------- storage
    var savedTabs: String?
        get() = sp.getString("saved_tabs", null)
        set(v) = sp.edit().putString("saved_tabs", v).apply()

    var storesJson: String?
        get() = sp.getString("stores", null)
        set(v) = sp.edit().putString("stores", v).apply()

    var bookmarksJson: String?
        get() = sp.getString("bookmarks", null)
        set(v) = sp.edit().putString("bookmarks", v).apply()

    /** Engine-level prefs changed since start → a restart is pending. */
    var restartPending = false

    /** Turns whatever the user typed in the URL bar into a URL. */
    fun resolveInput(input: String): String {
        val text = input.trim()
        if (text.isEmpty()) return homePage
        val lower = text.lowercase()
        if (lower == QUICK_ACCESS_URL || lower.startsWith("http://") || lower.startsWith("https://") ||
            lower.startsWith("about:") || lower.startsWith("moz-extension://")
        ) return text
        val looksLikeHost = !text.contains(' ') &&
            (text.contains('.') || text.startsWith("localhost")) && !text.endsWith('.')
        if (looksLikeHost) return "https://$text"
        val q = Uri.encode(text)
        return when (searchEngine) {
            1 -> "https://duckduckgo.com/?q=$q"
            2 -> "https://www.bing.com/search?q=$q"
            else -> "https://www.google.com/search?q=$q"
        }
    }

    companion object {
        const val QUICK_ACCESS_URL = "tv:quick"
        const val CLICK_MOUSE = 0
        const val CLICK_TOUCH = 1
    }
}
