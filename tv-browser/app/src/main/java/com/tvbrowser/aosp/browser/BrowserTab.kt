package com.tvbrowser.aosp.browser

import org.mozilla.geckoview.GeckoSession

/**
 * A tab. When [session] is null the tab is "discarded": it costs almost no RAM and only
 * keeps [state] (history + scroll + form data) to be restored when the user returns to it.
 */
class BrowserTab(val id: Int, var url: String) {
    var title: String = ""
    var session: GeckoSession? = null
    var state: GeckoSession.SessionState? = null
    var canGoBack = false
    var canGoForward = false
    var progress = 0
    var loading = false
    var scrollY = 0
    var lastUsed = 0L
    /** Session returned to Gecko from onNewSession and not opened yet. */
    var pendingOpen = false
    val killTimes = ArrayDeque<Long>()

    val isLive: Boolean get() = session != null
    val displayTitle: String get() = title.ifBlank { if (url == "tv:quick") "★ الوصول السريع" else url }
}
