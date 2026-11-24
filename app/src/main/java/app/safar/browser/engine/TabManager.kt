package app.safar.browser.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.safar.browser.data.TabSnapshot
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.WebResponse

/** أحداث يحتاجها باقي التطبيق (تنزيلات، وسائط، روابط خارجية، الترجمة...). */
interface TabEvents {
    fun onTabCreated(tab: BrowserTab) {}
    fun onLocationChanged(tab: BrowserTab) {}
    fun onTitleChanged(tab: BrowserTab) {}
    fun onExternalResponse(tab: BrowserTab, response: WebResponse) {}
    fun onExternalUri(uri: String): Boolean = false
    fun onMediaActivated(tab: BrowserTab, media: MediaSession) {}
    fun onMediaMetadata(tab: BrowserTab, title: String?, artist: String?) {}
    fun onMediaStateChanged(tab: BrowserTab, playing: Boolean) {}
    fun onMediaDeactivated(tab: BrowserTab) {}
    fun promptDelegate(): GeckoSession.PromptDelegate? = null
}

data class TabGroup(val id: Long, val name: String, val color: Long)

/** تبويب مغلق مؤخراً (لـ "علامات التبويب الأخيرة"). */
data class ClosedTab(val url: String, val title: String, val state: String?, val time: Long = System.currentTimeMillis())

class TabManager(
    private val runtime: GeckoRuntime,
    private val events: TabEvents,
) {
    val tabs = mutableStateListOf<BrowserTab>()
    val groups = mutableStateListOf<TabGroup>()
    val recentlyClosed = mutableStateListOf<ClosedTab>()
    private val groupColors = listOf(0xFF1E88E5, 0xFF43A047, 0xFFE53935, 0xFF8E24AA, 0xFFFB8C00, 0xFF00897B)
    var selectedId by mutableStateOf<Long?>(null)
        private set

    val selected: BrowserTab? get() = tabs.firstOrNull { it.id == selectedId }

    private var nextId = 1L

    fun newTab(
        url: String?,
        private: Boolean = false,
        select: Boolean = true,
        restoreState: String? = null,
        open: Boolean = true,
        desktop: Boolean = false,
    ): BrowserTab {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(private)
            .useTrackingProtection(true)
            .userAgentMode(if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()
        val session = GeckoSession(settings)
        val tab = BrowserTab(nextId++, session, private)
        tab.desktopMode = desktop
        attachDelegates(tab)
        if (open) session.open(runtime)
        tabs.add(tab)
        events.onTabCreated(tab)
        if (open) {
            if (restoreState != null) {
                runCatching { session.restoreState(GeckoSession.SessionState.fromString(restoreState)!!) }
                    .onFailure { session.loadUri(url ?: "about:blank") }
            } else {
                session.loadUri(url ?: "about:blank")
            }
        }
        if (select) select(tab.id)
        return tab
    }

    fun select(id: Long) {
        tabs.forEach { it.session.setActive(it.id == id || it.mediaPlaying) }
        selectedId = id
    }

    fun close(id: Long) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
        val tab = tabs.removeAt(idx)
        if (!tab.isPrivate && tab.url.startsWith("http")) {
            recentlyClosed.add(0, ClosedTab(tab.url, tab.title, tab.sessionState))
            while (recentlyClosed.size > 25) recentlyClosed.removeAt(recentlyClosed.lastIndex)
        }
        tab.session.close()
        groups.removeAll { g -> tabs.none { it.groupId == g.id } }
        if (selectedId == id) {
            selectedId = null
            tabs.getOrNull(idx.coerceAtMost(tabs.lastIndex))?.let { select(it.id) }
        }
    }

    /** إعادة فتح تبويب مغلق مؤخراً مع سجل تنقله. */
    fun reopen(closed: ClosedTab) {
        recentlyClosed.remove(closed)
        newTab(closed.url, restoreState = closed.state)
    }

    /**
     * تبديل وضع الكمبيوتر/الهاتف مع الحفاظ على حالة التصفح: نغيّر User-Agent ونمط الـ viewport لنفس الجلسة
     * (لا نفتح جلسة جديدة) ثم نعيد التحميل، فيبقى سجل الرجوع والتقدّم كما هو.
     */
    fun setDesktopMode(tab: BrowserTab, desktop: Boolean) {
        if (tab.desktopMode == desktop) return
        tab.desktopMode = desktop
        tab.session.settings.userAgentMode =
            if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        tab.session.settings.viewportMode =
            if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        tab.session.reload()
    }

    /** "الإضافة إلى مجموعة جديدة": ينشئ مجموعة ويضيف إليها التبويب. */
    fun addToNewGroup(tab: BrowserTab): TabGroup {
        val g = TabGroup(System.currentTimeMillis(), "مجموعة ${groups.size + 1}", groupColors[groups.size % groupColors.size])
        groups.add(g)
        tab.groupId = g.id
        return g
    }

    fun moveToGroup(tab: BrowserTab, groupId: Long?) {
        tab.groupId = groupId
        groups.removeAll { g -> tabs.none { it.groupId == g.id } }
    }

    fun closeAllPrivate() = tabs.filter { it.isPrivate }.forEach { close(it.id) }

    /** للتنقل بالإيماءات: +1 التالي، -1 السابق. */
    fun selectRelative(delta: Int) {
        if (tabs.size < 2) return
        val idx = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        val next = (idx + delta).mod(tabs.size)
        select(tabs[next].id)
    }

    fun snapshot(): List<TabSnapshot> = tabs.filterNot { it.isPrivate }.map {
        TabSnapshot(it.url, it.title, it.sessionState, it.id == selectedId)
    }

    fun restore(snapshots: List<TabSnapshot>) {
        snapshots.forEach { s -> newTab(s.url, select = s.selected, restoreState = s.state) }
        if (selectedId == null) tabs.lastOrNull()?.let { select(it.id) }
    }

    private fun attachDelegates(tab: BrowserTab) {
        val s = tab.session
        events.promptDelegate()?.let { s.promptDelegate = it }

        s.navigationDelegate = object : NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                tab.url = url ?: "about:blank"
                tab.permissions = perms.toList()
                events.onLocationChanged(tab)
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { tab.canGoBack = canGoBack }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { tab.canGoForward = canGoForward }

            override fun onLoadRequest(session: GeckoSession, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                val web = uri.startsWith("http") || uri.startsWith("about:") || uri.startsWith("data:") ||
                    uri.startsWith("blob:") || uri.startsWith("moz-extension:") || uri.startsWith("resource:")
                if (!web && events.onExternalUri(uri)) return GeckoResult.fromValue(AllowOrDeny.DENY)
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            // نافذة جديدة (target=_blank / window.open): الجلسة المُعادة يجب ألا تكون مفتوحة (GeckoView يفتحها)
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val newTab = newTab(null, private = tab.isPrivate, select = true, open = false)
                newTab.url = uri
                return GeckoResult.fromValue(newTab.session)
            }
        }

        s.progressDelegate = object : ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) { tab.loading = true; tab.progress = 5 }
            override fun onPageStop(session: GeckoSession, success: Boolean) { tab.loading = false; tab.progress = 100 }
            override fun onProgressChange(session: GeckoSession, progress: Int) { tab.progress = progress }
            override fun onSecurityChange(session: GeckoSession, securityInfo: ProgressDelegate.SecurityInformation) {
                tab.isSecure = securityInfo.isSecure
            }
            override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
                if (!tab.isPrivate) tab.sessionState = sessionState.toString()
            }
        }

        s.contentDelegate = object : ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title.orEmpty()
                events.onTitleChanged(tab)
            }
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                events.onExternalResponse(tab, response)
            }
            override fun onCloseRequest(session: GeckoSession) { close(tab.id) }
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) { tab.fullscreen = fullScreen }
            override fun onCrash(session: GeckoSession) { session.open(runtime); session.reload() }
            override fun onKill(session: GeckoSession) { session.open(runtime); session.loadUri(tab.url) }
        }

        s.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                events.onMediaActivated(tab, mediaSession)
            }
            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                tab.mediaPlaying = false
                events.onMediaDeactivated(tab)
            }
            override fun onMetadata(session: GeckoSession, mediaSession: MediaSession, meta: MediaSession.Metadata) {
                events.onMediaMetadata(tab, meta.title, meta.artist)
            }
            override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
                tab.mediaPlaying = true; events.onMediaStateChanged(tab, true)
            }
            override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
                tab.mediaPlaying = false; events.onMediaStateChanged(tab, false)
            }
            override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
                tab.mediaPlaying = false; events.onMediaStateChanged(tab, false)
            }
        }
    }
}
