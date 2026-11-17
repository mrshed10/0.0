package com.tvbrowser.aosp.browser

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.tvbrowser.aosp.extensions.ExtensionManager
import com.tvbrowser.aosp.util.Prefs
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
 *  - only [Prefs.maxLiveTabs] sessions stay open (default 1 on low-RAM devices);
 *  - background tabs are discarded (session closed, SessionState kept) and restored on demand;
 *  - on onTrimMemory / onLowMemory every background tab is discarded immediately;
 *  - if the content process is killed by the Low Memory Killer, the tab is restored from state.
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
        /** Return true if the URL was consumed (e.g. an .xpi/.crx extension install). */
        fun interceptLoad(uri: String): Boolean
    }

    private val main = Handler(Looper.getMainLooper())
    val tabs = mutableListOf<BrowserTab>()
    var current: BrowserTab? = null
        private set
    private var nextId = 1

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

    fun select(tab: BrowserTab) {
        tab.lastUsed = SystemClock.elapsedRealtime()
        ensureLive(tab)
        val previous = current
        if (previous != null && previous !== tab) previous.session?.takeIf { it.isOpen }?.setActive(false)
        current = tab
        tab.session?.takeIf { it.isOpen }?.setActive(true)
        listener.onTabSelected(tab)
        enforceLiveLimit()
    }

    fun close(tab: BrowserTab) {
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

    fun load(tab: BrowserTab, url: String) {
        tab.url = url
        val s = tab.session
        if (s == null) {
            tab.state = null
            if (tab === current) select(tab)
        } else {
            s.loadUri(url)
        }
    }

    /** Closes the session but keeps its state: frees the page's RAM. */
    fun discard(tab: BrowserTab) {
        val s = tab.session ?: return
        listener.onBeforeSessionClose(s)
        runCatching { if (s.isOpen) s.close() }
        tab.session = null
        tab.pendingOpen = false
    }

    fun discardBackgroundTabs() {
        tabs.filter { it !== current }.forEach { discard(it) }
    }

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
        val s = newSession(tab)
        tab.session = s
        s.open(runtime)
        val st = tab.state
        if (st != null) s.restoreState(st) else s.loadUri(tab.url)
    }

    // ------------------------------------------------------------------ persistence

    fun serialize(): String {
        val arr = JSONArray()
        for (t in tabs) {
            arr.put(JSONObject().apply {
                put("url", t.url)
                put("title", t.title)
                t.state?.let { put("state", it.toString()) }
                put("current", t === current)
            })
        }
        return arr.toString()
    }

    /** Restores tabs as discarded entries; only the current one is loaded. */
    fun restore(json: String?): Boolean {
        if (json.isNullOrBlank()) return false
        return runCatching {
            val arr = JSONArray(json)
            var selected: BrowserTab? = null
            for (i in 0 until minOf(arr.length(), prefs.maxTabs)) {
                val o = arr.getJSONObject(i)
                val tab = BrowserTab(nextId++, o.optString("url", prefs.homePage))
                tab.title = o.optString("title")
                o.optString("state").takeIf { it.isNotEmpty() }?.let {
                    tab.state = GeckoSession.SessionState.fromString(it)
                }
                tabs += tab
                if (o.optBoolean("current")) selected = tab
            }
            val toSelect = selected ?: tabs.lastOrNull() ?: return@runCatching false
            select(toSelect)
            listener.onTabListChanged()
            true
        }.getOrElse {
            Log.w(TAG, "restore failed", it)
            tabs.clear()
            false
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
        extensions.attachSession(s)
        return s
    }

    /** Content process was killed (usually by the Low Memory Killer) or crashed. */
    private fun recover(tab: BrowserTab) {
        val now = SystemClock.elapsedRealtime()
        tab.killTimes.addLast(now)
        while (tab.killTimes.isNotEmpty() && now - tab.killTimes.first() > 60_000) tab.killTimes.removeFirst()
        discard(tab)
        if (tab !== current) return
        discardBackgroundTabs()
        if (tab.killTimes.size >= 3) {
            // Page is too heavy for this device: stop the reload loop.
            tab.state = null
            tab.url = "about:blank"
        }
        main.post { if (current === tab) select(tab) }
    }

    private inner class Navigation(private val tab: BrowserTab) : GeckoSession.NavigationDelegate {
        override fun onLocationChange(
            session: GeckoSession,
            url: String?,
            perms: MutableList<ContentPermission>,
            hasUserGesture: Boolean,
        ) {
            if (url != null) tab.url = url
            listener.onTabChanged(tab)
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            tab.canGoBack = canGoBack
            listener.onTabChanged(tab)
        }

        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
            tab.canGoForward = canGoForward
            listener.onTabChanged(tab)
        }

        override fun onLoadRequest(
            session: GeckoSession,
            request: GeckoSession.NavigationDelegate.LoadRequest,
        ): GeckoResult<AllowOrDeny>? {
            val uri = request.uri
            if (listener.interceptLoad(uri)) return GeckoResult.fromValue(AllowOrDeny.DENY)
            val scheme = uri.substringBefore(':').lowercase()
            if (scheme == "intent" || scheme == "market" || scheme == "tel" || scheme == "mailto") {
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }
            return GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            if (listener.interceptLoad(uri)) return null
            if (tabs.size >= prefs.maxTabs) {
                // Tab limit reached (RAM): open the link in the same tab instead.
                session.loadUri(uri)
                return null
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
            return GeckoResult.fromValue(s)
        }
    }

    private inner class Progress(private val tab: BrowserTab) : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
            tab.loading = true
            tab.progress = 5
            listener.onTabChanged(tab)
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            tab.loading = false
            tab.progress = 100
            listener.onTabChanged(tab)
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            tab.progress = progress
            listener.onTabChanged(tab)
        }

        override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
            tab.state = sessionState
        }
    }

    private inner class Content(private val tab: BrowserTab) : GeckoSession.ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) {
            tab.title = title.orEmpty()
            listener.onTabChanged(tab)
        }

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            listener.onFullScreen(tab, fullScreen)
        }

        override fun onCloseRequest(session: GeckoSession) {
            if (tabs.contains(tab)) close(tab)
        }

        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            listener.onExternalResponse(tab, response)
        }

        override fun onCrash(session: GeckoSession) {
            Log.w(TAG, "content crashed: ${tab.url}")
            recover(tab)
        }

        override fun onKill(session: GeckoSession) {
            Log.w(TAG, "content killed (low memory?): ${tab.url}")
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
