package com.tvbrowser.aosp.browser

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.tvbrowser.aosp.extensions.ExtensionManager
import com.tvbrowser.aosp.util.Prefs
import com.tvbrowser.aosp.util.safe
import com.tvbrowser.aosp.util.safeOr
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.WebResponse

/**
 * Owns all tabs and their GeckoSessions.
 *
 * RAM strategy (1GB devices):
 *  - the home page is the native Quick Access screen: a tab showing it has NO session at all;
 *  - only [Prefs.maxLiveTabs] sessions stay open (default 1); the rest are frozen
 *    (session closed, SessionState kept) and restored on demand;
 *  - if Android kills the page process, the page is restored — at most 3 times per minute,
 *    then the tab falls back to Quick Access instead of looping (white screen + crash).
 *  - every engine callback is wrapped in [safe]: an exception never crashes the browser.
 */
class TabManager(
    private val runtime: GeckoRuntime,
    private val prefs: Prefs,
    private val listener: Listener,
    private val prompts: GeckoSession.PromptDelegate,
    private val extensions: ExtensionManager,
) {
    interface Listener {
        fun onTabSelected(tab: BrowserTab)
        fun onTabChanged(tab: BrowserTab)
        fun onTabListChanged()
        fun onBeforeSessionClose(session: GeckoSession)
        fun onFullScreen(tab: BrowserTab, fullScreen: Boolean)
        fun onExternalResponse(tab: BrowserTab, response: WebResponse)
        fun onPageRecovered(tab: BrowserTab, gaveUp: Boolean)
        /** Return true if the URL was consumed (e.g. an .xpi/.crx extension install). */
        fun interceptLoad(uri: String): Boolean
    }

    private val main = Handler(Looper.getMainLooper())
    val tabs = mutableListOf<BrowserTab>()
    var current: BrowserTab? = null
        private set
    private var nextId = 1

    fun isQuickAccess(tab: BrowserTab) = tab.url == Prefs.QUICK_ACCESS_URL && tab.session == null

    // ------------------------------------------------------------------ tabs

    fun openTab(url: String, select: Boolean = true, state: GeckoSession.SessionState? = null): BrowserTab {
        val cur = current
        if (tabs.size >= prefs.maxTabs && cur != null) {
            load(cur, url)
            return cur
        }
        val tab = BrowserTab(nextId++, url).also { it.state = state }
        tabs += tab
        if (select || current == null) select(tab)
        listener.onTabListChanged()
        return tab
    }

    fun select(tab: BrowserTab) = safe(TAG) {
        tab.lastUsed = SystemClock.elapsedRealtime()
        val previous = current
        if (previous != null && previous !== tab) previous.session?.takeIf { it.isOpen }?.setActive(false)
        current = tab
        // Free memory first, THEN open the new page.
        enforceLiveLimit()
        ensureLive(tab)
        tab.session?.takeIf { it.isOpen }?.setActive(true)
        listener.onTabSelected(tab)
    }

    fun close(tab: BrowserTab) = safe(TAG) {
        discard(tab)
        val idx = tabs.indexOf(tab)
        tabs.remove(tab)
        if (current === tab) {
            current = null
            if (tabs.isEmpty()) openTab(prefs.homePage)
            else select(tabs[(idx - 1).coerceIn(0, tabs.size - 1)])
        }
        listener.onTabListChanged()
    }

    fun closeAll() {
        tabs.toList().forEach { discard(it) }
        tabs.clear()
        current = null
    }

    fun load(tab: BrowserTab, url: String) = safe(TAG) {
        if (url == Prefs.QUICK_ACCESS_URL) {
            goQuickAccess(tab)
            return@safe
        }
        tab.url = url
        val s = tab.session
        if (s == null) {
            tab.state = null
            if (tab === current) select(tab)
        } else {
            s.loadUri(url)
        }
    }

    /** Shows the native Quick Access screen in this tab and frees its page memory. */
    fun goQuickAccess(tab: BrowserTab) = safe(TAG) {
        discard(tab)
        tab.url = Prefs.QUICK_ACCESS_URL
        tab.title = ""
        tab.state = null
        tab.canGoBack = false
        tab.canGoForward = false
        tab.loading = false
        if (tab === current) listener.onTabSelected(tab)
    }

    /** Closes the session but keeps its state: frees the page's RAM. */
    fun discard(tab: BrowserTab) {
        val s = tab.session ?: return
        safe(TAG) { listener.onBeforeSessionClose(s) }
        safe(TAG) { if (s.isOpen) s.close() }
        tab.session = null
        tab.pendingOpen = false
    }

    fun discardBackgroundTabs() {
        tabs.filter { it !== current }.forEach { discard(it) }
    }

    fun liveCount() = tabs.count { it.isLive }

    private fun enforceLiveLimit() {
        val background = tabs.filter { it.isLive && it !== current }.sortedBy { it.lastUsed }
        var excess = background.size + 1 - prefs.maxLiveTabs
        for (t in background) {
            if (excess <= 0) break
            discard(t)
            excess--
        }
    }

    private fun ensureLive(tab: BrowserTab) {
        if (tab.session != null) return
        if (tab.url == Prefs.QUICK_ACCESS_URL && tab.state == null) return // native screen
        val s = newSession(tab)
        tab.session = s
        s.open(runtime)
        val st = tab.state
        if (st != null) s.restoreState(st) else s.loadUri(tab.url)
    }

    // ------------------------------------------------------------------ persistence

    fun serialize(): String = safeOr(TAG, "[]") {
        val arr = JSONArray()
        for (t in tabs) {
            arr.put(JSONObject().apply {
                put("url", t.url)
                put("title", t.title)
                if (t.url != Prefs.QUICK_ACCESS_URL) t.state?.let { put("state", it.toString()) }
                put("current", t === current)
            })
        }
        arr.toString()
    }

    /**
     * Restores tabs as frozen entries. The page the user was on is NOT reloaded automatically
     * when [loadCurrent] is false (safe mode) — Quick Access is shown instead.
     */
    fun restore(json: String?, loadCurrent: Boolean): Boolean {
        if (json.isNullOrBlank()) return false
        return safeOr(TAG, false) {
            val arr = JSONArray(json)
            var selected: BrowserTab? = null
            for (i in 0 until minOf(arr.length(), prefs.maxTabs)) {
                val o = arr.getJSONObject(i)
                val tab = BrowserTab(nextId++, o.optString("url", prefs.homePage))
                tab.title = o.optString("title")
                o.optString("state").takeIf { it.isNotEmpty() }?.let {
                    tab.state = runCatching { GeckoSession.SessionState.fromString(it) }.getOrNull()
                }
                tabs += tab
                if (o.optBoolean("current")) selected = tab
            }
            if (tabs.isEmpty()) return@safeOr false
            val toSelect = if (loadCurrent) selected ?: tabs.last()
            else BrowserTab(nextId++, Prefs.QUICK_ACCESS_URL).also { tabs += it }
            select(toSelect)
            listener.onTabListChanged()
            true
        }
    }

    // ------------------------------------------------------------------ sessions

    private fun newSession(tab: BrowserTab): GeckoSession {
        val s = GeckoSession(DesktopMode.sessionSettings())
        s.navigationDelegate = Navigation(tab)
        s.progressDelegate = Progress(tab)
        s.contentDelegate = Content(tab)
        s.permissionDelegate = Permissions
        s.scrollDelegate = Scroll(tab)
        s.promptDelegate = prompts
        safe(TAG) { extensions.attachSession(s) }
        return s
    }

    /** Content process was killed (Low Memory Killer) or crashed. */
    private fun recover(tab: BrowserTab) = safe(TAG) {
        val now = SystemClock.elapsedRealtime()
        tab.killTimes.addLast(now)
        while (tab.killTimes.isNotEmpty() && now - tab.killTimes.first() > 60_000) tab.killTimes.removeFirst()
        discard(tab)
        if (tab !== current) return@safe
        discardBackgroundTabs()
        val gaveUp = tab.killTimes.size >= 3
        if (gaveUp) {
            // This page is too heavy for the device: stop the reload loop.
            tab.killTimes.clear()
            main.post { goQuickAccess(tab); listener.onPageRecovered(tab, true) }
        } else {
            main.postDelayed({
                if (current === tab) {
                    select(tab)
                    listener.onPageRecovered(tab, false)
                }
            }, 400)
        }
    }

    private inner class Navigation(private val tab: BrowserTab) : GeckoSession.NavigationDelegate {
        override fun onLocationChange(
            session: GeckoSession,
            url: String?,
            perms: MutableList<ContentPermission>,
            hasUserGesture: Boolean,
        ) = safe(TAG) {
            if (url != null && url != "about:blank") tab.url = url
            listener.onTabChanged(tab)
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) = safe(TAG) {
            tab.canGoBack = canGoBack
            listener.onTabChanged(tab)
        }

        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) = safe(TAG) {
            tab.canGoForward = canGoForward
            listener.onTabChanged(tab)
        }

        override fun onLoadRequest(
            session: GeckoSession,
            request: GeckoSession.NavigationDelegate.LoadRequest,
        ): GeckoResult<AllowOrDeny>? = safeOr(TAG, GeckoResult.fromValue(AllowOrDeny.ALLOW)) {
            val uri = request.uri
            if (listener.interceptLoad(uri)) return@safeOr GeckoResult.fromValue(AllowOrDeny.DENY)
            when (uri.substringBefore(':').lowercase()) {
                "intent", "market", "tel", "mailto", "sms" -> GeckoResult.fromValue(AllowOrDeny.DENY)
                else -> GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? =
            safeOr<GeckoResult<GeckoSession>?>(TAG, null) {
                if (listener.interceptLoad(uri)) return@safeOr null
                if (tabs.size >= prefs.maxTabs || prefs.maxLiveTabs <= 1) {
                    // 1GB mode: a second live page would double memory. Open in the same tab.
                    session.loadUri(uri)
                    return@safeOr null
                }
                val newTab = BrowserTab(nextId++, uri)
                val s = newSession(newTab)
                newTab.session = s
                newTab.pendingOpen = true
                tabs += newTab
                main.post {
                    newTab.pendingOpen = false
                    select(newTab)
                    listener.onTabListChanged()
                }
                GeckoResult.fromValue(s)
            }
    }

    private inner class Progress(private val tab: BrowserTab) : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) = safe(TAG) {
            tab.loading = true
            tab.progress = 5
            listener.onTabChanged(tab)
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) = safe(TAG) {
            tab.loading = false
            tab.progress = 100
            listener.onTabChanged(tab)
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) = safe(TAG) {
            tab.progress = progress
            listener.onTabChanged(tab)
        }

        override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
            tab.state = sessionState
        }
    }

    private inner class Content(private val tab: BrowserTab) : GeckoSession.ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) = safe(TAG) {
            tab.title = title.orEmpty()
            listener.onTabChanged(tab)
        }

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) = safe(TAG) {
            listener.onFullScreen(tab, fullScreen)
        }

        override fun onCloseRequest(session: GeckoSession) = safe(TAG) {
            if (tabs.contains(tab)) close(tab)
        }

        override fun onExternalResponse(session: GeckoSession, response: WebResponse) = safe(TAG) {
            listener.onExternalResponse(tab, response)
        }

        override fun onCrash(session: GeckoSession) {
            Log.w(TAG, "content crashed: ${tab.url}")
            recover(tab)
        }

        override fun onKill(session: GeckoSession) {
            Log.w(TAG, "content killed (low memory): ${tab.url}")
            recover(tab)
        }
    }

    private inner class Scroll(private val tab: BrowserTab) : GeckoSession.ScrollDelegate {
        override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
            tab.scrollY = scrollY
        }
    }

    /** TV: allow autoplay & DRM, deny location/notifications (no use on a TV, saves resources). */
    private object Permissions : GeckoSession.PermissionDelegate {
        override fun onContentPermissionRequest(
            session: GeckoSession,
            perm: ContentPermission,
        ): GeckoResult<Int>? {
            val allow = when (perm.permission) {
                GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
                GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
                GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS,
                GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> true
                else -> false
            }
            return GeckoResult.fromValue(if (allow) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY)
        }
    }

    companion object {
        private const val TAG = "TabManager"
    }
}
