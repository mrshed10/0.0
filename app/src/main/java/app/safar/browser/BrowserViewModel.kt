package app.safar.browser

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.safar.browser.data.BrowserSettings
import app.safar.browser.data.HistoryEntry
import app.safar.browser.data.QuickAction
import app.safar.browser.engine.ClosedTab
import app.safar.browser.engine.PageSaver
import app.safar.browser.extensions.ChromeStore
import app.safar.browser.extensions.ChromeStoreBridge
import app.safar.browser.extensions.StoreAnalysis
import app.safar.browser.media.PageReader
import org.mozilla.geckoview.StorageController
import app.safar.browser.data.ContentKind
import app.safar.browser.data.ProviderId
import app.safar.browser.data.SearchEngines
import app.safar.browser.data.SecureKeyStore
import app.safar.browser.data.TranslateSettings
import app.safar.browser.engine.BasicPromptDelegate
import app.safar.browser.engine.BrowserTab
import app.safar.browser.engine.DownloadHandler
import app.safar.browser.engine.GeckoProvider
import app.safar.browser.engine.TabEvents
import app.safar.browser.engine.TabManager
import app.safar.browser.engine.UrlUtils
import app.safar.browser.engine.await
import app.safar.browser.extensions.ExtensionManager
import app.safar.browser.media.MediaBridge
import app.safar.browser.media.MediaPlaybackService
import app.safar.browser.privacy.PrivacyController
import app.safar.browser.translate.LiveTranslateController
import app.safar.browser.translate.ProviderRouter
import app.safar.browser.translate.ocr.MlKitOcr
import app.safar.browser.translate.ocr.OcrRouter
import app.safar.browser.translate.ocr.TesseractOcr
import app.safar.browser.translate.providers.ClaudeProvider
import app.safar.browser.translate.providers.DeepLProvider
import app.safar.browser.translate.providers.GeminiProvider
import app.safar.browser.translate.providers.GoogleCloudProvider
import app.safar.browser.translate.providers.GoogleFreeProvider
import app.safar.browser.translate.providers.OnDeviceProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.WebResponse
import java.lang.ref.WeakReference

/** هدف الضغط المطوّل: رابط و/أو صورة. */
data class LinkTarget(val url: String?, val text: String, val title: String, val imageSrc: String?, val private: Boolean)

data class InstallRequest(val name: String, val permissions: List<String>, val answer: CompletableDeferred<Boolean>)

class BrowserViewModel(app: Application) : AndroidViewModel(app), TabEvents {
    private val c = (app as SafarApp).container
    val settings: StateFlow<BrowserSettings> = c.settings
    val keys: SecureKeyStore = c.keys
    val bookmarks = c.bookmarks.items
    val history = c.history.items
    val readingList = c.readingList.items
    val linkTarget = MutableStateFlow<LinkTarget?>(null)
    val previewUrl = MutableStateFlow<String?>(null)
    val pageReader = PageReader(app)
    private val storeBridge = ChromeStoreBridge()

    var activity: WeakReference<Activity>? = null
    private val promptDelegate = BasicPromptDelegate { activity?.get() }
    private val downloads = DownloadHandler(app)

    val tabs = TabManager(c.runtime, this)
    val privacy = PrivacyController(c.runtime)

    private val router: ProviderRouter = ProviderRouter(
        mapOf(
            ProviderId.ON_DEVICE to OnDeviceProvider { downloading ->
                // ML Kit ينزّل نموذج اللغة (~30MB) عند أول استخدام لكل زوج لغات
                viewModelScope.launch {
                    translate.setNote(if (downloading) "جارٍ تجهيز نموذج اللغة على الجهاز (مرة واحدة)…" else null)
                }
            },
            ProviderId.GOOGLE_FREE to GoogleFreeProvider(),
            ProviderId.GOOGLE to GoogleCloudProvider { keys.get(SecureKeyStore.Name.GOOGLE) },
            ProviderId.DEEPL to DeepLProvider { keys.get(SecureKeyStore.Name.DEEPL) },
            ProviderId.CLAUDE to ClaudeProvider({ keys.get(SecureKeyStore.Name.CLAUDE) }, { settings.value.translate.claudeModel }),
            ProviderId.GEMINI to GeminiProvider({ keys.get(SecureKeyStore.Name.GEMINI) }, { settings.value.translate.geminiModel }),
        )
    )
    val translate: LiveTranslateController = LiveTranslateController(
        c.runtime, viewModelScope, { settings.value }, router, OcrRouter(MlKitOcr(), TesseractOcr(app)),
        zoomFor = { url -> UrlUtils.host(url)?.let { settings.value.siteZoom[it] } ?: 100 },
        onSuggestManga = {
            if (settings.value.translate.content == ContentKind.TEXT) {
                updateTranslate { it.copy(content = ContentKind.MANGA) }
                viewModelScope.launch {
                    translate.setNote("صفحة مصوّرة: تم التبديل إلى وضع الصور / المانغا")
                    kotlinx.coroutines.delay(4000)
                    translate.setNote(null)
                }
            }
        },
    )

    val installRequest = MutableStateFlow<InstallRequest?>(null)
    val popupSession = MutableStateFlow<GeckoSession?>(null)
    val extensions = ExtensionManager(
        c.runtime,
        confirmInstall = { name, perms ->
            val req = InstallRequest(name, perms, CompletableDeferred())
            installRequest.value = req
            req.answer.await().also { installRequest.value = null }
        },
        showPopup = { popupSession.value = it },
    )

    init {
        val s = settings.value
        if (s.restoreTabs) tabs.restore(c.sessionStore.items.value)
        if (tabs.tabs.isEmpty()) tabs.newTab(s.homePage)

        viewModelScope.launch {
            runCatching { privacy.install(); privacy.push(settings.value) }
            runCatching { translate.install(tabs.tabs.map { it.session }) }
            extensions.init(viewModelScope)
            runCatching { extensions.refresh() }
        }

        viewModelScope.launch {
            var prevTarget = s.translate.target
            settings.drop(1).collect { ns ->
                GeckoProvider.apply(c.runtime, ns)
                privacy.push(ns)
                if (ns.translate.target != prevTarget) { translate.clearCache(); prevTarget = ns.translate.target }
                translate.pushConfig()
            }
        }
    }

    // ---------------------------------------------------------------- أحداث التبويبات

    override fun promptDelegate(): GeckoSession.PromptDelegate = promptDelegate

    override fun onTabCreated(tab: BrowserTab) = translate.attach(tab.session)

    private var lastHost: String? = null

    override fun onLocationChanged(tab: BrowserTab) {
        if (privacy.applyExceptionFor(tab, settings.value)) tab.session.reload()
        val host = UrlUtils.host(tab.url)
        if (tab.id == tabs.selectedId && host != lastHost) { lastHost = host; translate.onNavigated() }
        if (!tab.isPrivate) viewModelScope.launch { c.history.record(tab.url, tab.title) }
        // وضع سطح المكتب التلقائي: مواقع محفوظة + متجر Chrome (ليسهل التصفح والتحميل)
        val s = settings.value
        val wantDesktop = host != null && (host in s.desktopSites || (s.autoDesktopForWebStore && ChromeStore.isStoreUrl(tab.url)))
        if (wantDesktop && !tab.desktopMode) tabs.setDesktopMode(tab, true)
    }

    override fun onContextMenu(tab: BrowserTab, element: GeckoSession.ContentDelegate.ContextElement) {
        val link = element.linkUri
        val img = element.srcUri?.takeIf { element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE }
        if (link == null && img == null) return
        linkTarget.value = LinkTarget(
            url = link,
            text = element.textContent?.trim().orEmpty(),
            title = element.title ?: element.altText ?: "",
            imageSrc = img,
            private = tab.isPrivate,
        )
    }

    override fun onTitleChanged(tab: BrowserTab) {
        if (!tab.isPrivate) viewModelScope.launch { c.history.updateTitle(tab.url, tab.title) }
    }

    override fun onExternalResponse(tab: BrowserTab, response: WebResponse) =
        downloads.handle(response, settings.value, tab.isPrivate)

    override fun onExternalUri(uri: String): Boolean {
        val ctx = activity?.get() ?: return false
        return try {
            val intent = if (uri.startsWith("intent:")) Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
            else Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            intent.addCategory(Intent.CATEGORY_BROWSABLE).setComponent(null).setSelector(null)
            ctx.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            // fallback لروابط intent: التي تحمل رابطاً بديلاً
            Regex("""S\.browser_fallback_url=([^;]+)""").find(uri)?.groupValues?.get(1)?.let {
                tabs.selected?.session?.loadUri(Uri.decode(it))
            }
            true
        } catch (e: Exception) { false }
    }

    override fun onMediaActivated(tab: BrowserTab, media: MediaSession) { MediaBridge.session = media }

    override fun onMediaMetadata(tab: BrowserTab, title: String?, artist: String?) {
        MediaBridge.title = title ?: tab.title
        MediaBridge.artist = artist ?: UrlUtils.display(tab.url)
        if (MediaBridge.playing) updateMediaNotification()
    }

    override fun onMediaStateChanged(tab: BrowserTab, playing: Boolean) {
        MediaBridge.playing = playing
        if (MediaBridge.title.isBlank()) MediaBridge.title = tab.title
        updateMediaNotification()
    }

    override fun onMediaDeactivated(tab: BrowserTab) {
        MediaBridge.session = null; MediaBridge.playing = false
        MediaPlaybackService.stop(getApplication())
    }

    private fun updateMediaNotification() {
        val s = settings.value
        if (!s.backgroundAudio || !s.mediaNotification) return
        runCatching { MediaPlaybackService.update(getApplication()) }
    }

    // ---------------------------------------------------------------- دورة حياة النشاط

    fun onActivityStart() { tabs.selected?.session?.setActive(true) }

    /** نوقف العرض لتوفير البطارية، إلا إن كان هناك صوت يعمل والتشغيل في الخلفية مفعّل. */
    fun onActivityStop() {
        val keepAlive = settings.value.backgroundAudio && MediaBridge.playing
        tabs.tabs.forEach { it.session.setActive(keepAlive && it.mediaPlaying) }
        saveSession()
    }

    fun saveSession() = viewModelScope.launch { c.sessionStore.set(tabs.snapshot()) }

    // ---------------------------------------------------------------- إجراءات المتصفح

    fun load(input: String) {
        val url = UrlUtils.toUrlOrSearch(input, SearchEngines.byId(settings.value.searchEngineId).template)
        val tab = tabs.selected ?: tabs.newTab(null)
        tab.session.loadUri(url)
    }

    fun openInNewTab(url: String, private: Boolean = false) {
        tabs.newTab(UrlUtils.toUrlOrSearch(url, SearchEngines.byId(settings.value.searchEngineId).template), private)
    }

    fun newTab(private: Boolean = false) = tabs.newTab(settings.value.homePage, private, desktop = settings.value.desktopByDefault)

    fun closeTab(id: Long) {
        tabs.tabs.firstOrNull { it.id == id }?.let { translate.detach(it.session) }
        tabs.close(id)
        if (tabs.tabs.isEmpty()) newTab()
    }

    fun goHome() = tabs.selected?.session?.loadUri(settings.value.homePage)

    fun toggleBookmark() = viewModelScope.launch {
        val t = tabs.selected ?: return@launch
        c.bookmarks.toggle(t.url, t.title)
    }

    fun removeBookmark(url: String) = viewModelScope.launch {
        c.bookmarks.set(c.bookmarks.items.value.filterNot { it.url == url })
    }

    fun isBookmarked(url: String) = c.bookmarks.contains(url)

    fun share() {
        val t = tabs.selected ?: return
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t.url)
        activity?.get()?.startActivity(Intent.createChooser(i, t.title))
    }

    /** إضافة/إزالة الموقع الحالي من استثناءات الحماية. */
    fun toggleSiteException() {
        val host = UrlUtils.host(tabs.selected?.url) ?: return
        updateSettings { s ->
            val set = if (host in s.siteExceptions) s.siteExceptions - host else s.siteExceptions + host
            s.copy(siteExceptions = set)
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(150)
            tabs.selected?.let { privacy.applyExceptionFor(it, settings.value); it.session.reload() }
        }
    }

    fun currentHostExcepted(): Boolean =
        UrlUtils.matchesException(UrlUtils.host(tabs.selected?.url), settings.value.siteExceptions)

    fun updateSettings(transform: (BrowserSettings) -> BrowserSettings) =
        viewModelScope.launch { c.settingsRepo.update(transform) }

    fun updateTranslate(transform: (TranslateSettings) -> TranslateSettings) =
        updateSettings { it.copy(translate = transform(it.translate)) }

    // ---------------------------------------------------------------- الإضافات

    fun installExtension(url: String) = viewModelScope.launch {
        try {
            val id = extensions.install(url) ?: return@launch
            updateSettings { it.copy(extensionSources = it.extensionSources + (id to url)) }
            toast("تم تثبيت الإضافة")
        } catch (e: Exception) {
            toast("فشل التثبيت: ${e.message}")
        }
    }

    fun uninstallExtension(id: String) = viewModelScope.launch {
        extensions.uninstall(id)
        updateSettings { it.copy(extensionSources = it.extensionSources - id, pinnedExtensions = it.pinnedExtensions - id) }
    }

    fun togglePin(id: String) = updateSettings {
        it.copy(pinnedExtensions = if (id in it.pinnedExtensions) it.pinnedExtensions - id else it.pinnedExtensions + id)
    }

    // ---------------------------------------------------------------- النسخ الاحتياطي

    fun exportBackup(uri: Uri, password: CharArray) = viewModelScope.launch {
        runCatching { c.backup.export(uri, password, tabs.snapshot()) }
            .onSuccess { toast("تم حفظ النسخة الاحتياطية المشفرة") }
            .onFailure { toast("فشل الحفظ: ${it.message}") }
        password.fill('\u0000')
    }

    fun importBackup(uri: Uri, password: CharArray) = viewModelScope.launch {
        runCatching { c.backup.import(uri, password) }
            .onSuccess { payload ->
                tabs.restore(payload.tabs)
                payload.settings.extensionSources.values.forEach { url -> runCatching { extensions.install(url) } }
                toast("تمت الاستعادة")
            }
            .onFailure { toast(it.message ?: "فشلت الاستعادة") }
        password.fill('\u0000')
    }

    // ---------------------------------------------------------------- وضع سطح المكتب

    /** تبديل الموقع الحالي بين الهاتف والكمبيوتر، مع تذكّر الاختيار لهذا الموقع. */
    fun toggleDesktopMode() {
        val tab = tabs.selected ?: return
        val on = !tab.desktopMode
        tabs.setDesktopMode(tab, on)
        UrlUtils.host(tab.url)?.let { host ->
            updateSettings { it.copy(desktopSites = if (on) it.desktopSites + host else it.desktopSites - host) }
        }
    }

    fun openChromeWebStore() {
        val tab = tabs.newTab(ChromeStore.HOME_URL, desktop = settings.value.autoDesktopForWebStore)
        tab.url = ChromeStore.HOME_URL
    }

    suspend fun analyzeStoreExtension(): StoreAnalysis {
        val t = tabs.selected ?: throw IllegalStateException("لا توجد صفحة")
        return storeBridge.analyze(t.url, t.title)
    }

    // ---------------------------------------------------------------- التكبير والتصغير

    fun currentZoom(): Int = UrlUtils.host(tabs.selected?.url)?.let { settings.value.siteZoom[it] } ?: 100

    /** حفظ مستوى التكبير لهذا الموقع وتطبيقه فوراً. */
    fun setZoom(pct: Int) {
        val v = pct.coerceIn(50, 300)
        val host = UrlUtils.host(tabs.selected?.url) ?: return
        updateSettings { s -> s.copy(siteZoom = if (v == 100) s.siteZoom - host else s.siteZoom + (host to v)) }
        translate.setZoom(tabs.selected?.session, v)
    }

    fun resetZoom(host: String) = updateSettings { it.copy(siteZoom = it.siteZoom - host) }

    // ---------------------------------------------------------------- حفظ الصفحة

    fun savePage(share: Boolean = false) = viewModelScope.launch {
        val t = tabs.selected ?: return@launch
        runCatching { PageSaver.saveAsPdf(getApplication(), t.session, t.title.ifBlank { UrlUtils.display(t.url) }) }
            .onSuccess { uri ->
                toast("تم حفظ الصفحة في التنزيلات/Safar")
                if (share && Build.VERSION.SDK_INT >= 29) {
                    val i = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    activity?.get()?.startActivity(Intent.createChooser(i, t.title))
                }
            }
            .onFailure { toast("تعذر حفظ الصفحة: ${it.message}") }
    }

    // ---------------------------------------------------------------- السجل وحذف البيانات

    fun removeHistory(e: HistoryEntry) = viewModelScope.launch { c.history.remove(e) }

    /**
     * حذف بيانات التصفح. نطاق الوقت يُطبق على السجل؛ الكوكيز وذاكرة التخزين المؤقت في Gecko تُحذف بالكامل
     * (لا يوفر GeckoView حذفاً حسب الوقت).
     */
    fun clearBrowsingData(sinceMillis: Long, history: Boolean, cookies: Boolean, cache: Boolean) = viewModelScope.launch {
        if (history) c.history.clearSince(sinceMillis)
        var flags = 0L
        if (cookies) flags = flags or StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.SITE_DATA or
            StorageController.ClearFlags.DOM_STORAGES or StorageController.ClearFlags.AUTH_SESSIONS
        if (cache) flags = flags or StorageController.ClearFlags.ALL_CACHES
        if (flags != 0L) runCatching { c.runtime.storageController.clearData(flags).await() }
        if (history || cache) translate.clearCache()
        toast("تم حذف بيانات التصفح")
    }

    fun openDownloads() {
        runCatching {
            activity?.get()?.startActivity(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS))
        }.onFailure { toast("لا يوجد تطبيق لعرض التنزيلات") }
    }

    fun reopenClosed(t: ClosedTab) = tabs.reopen(t)

    fun addCurrentToNewGroup() {
        val t = tabs.selected ?: return
        val g = tabs.addToNewGroup(t)
        toast("أُضيف التبويب إلى ${g.name}")
    }

    // ---------------------------------------------------------------- زر الاختصار السريع

    /** تسجيل استخدام إجراء (لوضع "حسب معدل استخدامك"). */
    fun countAction(a: QuickAction) = updateSettings { s ->
        s.copy(actionUsage = s.actionUsage + (a.name to ((s.actionUsage[a.name] ?: 0) + 1)))
    }

    fun readAloud() = viewModelScope.launch {
        if (pageReader.speaking.value) { pageReader.stop(); return@launch }
        val (text, lang) = translate.requestPageText(tabs.selected?.session) ?: run { toast("تعذر قراءة نص الصفحة"); return@launch }
        if (text.isBlank()) { toast("لا يوجد نص للقراءة"); return@launch }
        pageReader.speak(text, lang)
    }

    // ---------------------------------------------------------------- قائمة الضغط المطوّل على الروابط

    /** يفتح الرابط في تبويب جديد في الخلفية (تبقى الصفحة الحالية ظاهرة كما في Chrome). */
    fun openLinkInNewTab(url: String) {
        val cur = tabs.selected
        tabs.newTab(url, private = cur?.isPrivate == true, select = false, desktop = settings.value.desktopByDefault)
        toast("فُتح الرابط في علامة تبويب جديدة")
    }

    /** تبويب جديد ضمن مجموعة التبويب الحالي (أو مجموعة جديدة تضم الاثنين). */
    fun openLinkInGroup(url: String) {
        val cur = tabs.selected ?: return openLinkInNewTab(url)
        val gid = cur.groupId ?: tabs.addToNewGroup(cur).id
        val t = tabs.newTab(url, private = cur.isPrivate, select = false)
        tabs.moveToGroup(t, gid)
        toast("أُضيف إلى ${tabs.groups.firstOrNull { it.id == gid }?.name ?: "المجموعة"}")
    }

    fun openLinkIncognito(url: String) { tabs.newTab(url, private = true) }

    /** "نافذة جديدة": تبويب في مجموعة مستقلة باسم نافذة، ويُعرض فوراً (سفر يعمل بنافذة واحدة على أندرويد). */
    fun openLinkInNewWindow(url: String) {
        val t = tabs.newTab(url, private = tabs.selected?.isPrivate == true)
        val g = tabs.addToNewGroup(t)
        toast("فُتح في ${g.name}")
    }

    fun copyToClipboard(label: String, text: String) {
        val cm = getApplication<Application>().getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        if (Build.VERSION.SDK_INT < 33) toast("تم النسخ")
    }

    fun downloadLink(url: String) = downloads.downloadUrl(url, settings.value, tabs.selected?.isPrivate == true)

    fun addToReadingList(url: String, title: String) = viewModelScope.launch {
        c.readingList.add(url, title.ifBlank { UrlUtils.display(url) })
        toast("أُضيف إلى قائمة القراءة")
    }

    fun removeFromReadingList(url: String) = viewModelScope.launch { c.readingList.remove(url) }
    fun markRead(url: String) = viewModelScope.launch { c.readingList.markRead(url) }

    fun shareLink(url: String, title: String) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        activity?.get()?.startActivity(Intent.createChooser(i, title.ifBlank { url }))
    }

    /** جلسة معاينة سريعة (لا تُضاف للتبويبات ولا للسجل). */
    fun newPreviewSession(url: String, private: Boolean): GeckoSession {
        val s = GeckoSession(org.mozilla.geckoview.GeckoSessionSettings.Builder().usePrivateMode(private).build())
        s.open(c.runtime)
        s.loadUri(url)
        return s
    }

    // ---------------------------------------------------------------- Intents

    fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString?.let { openInNewTab(it) }
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra("query")?.let { openInNewTab(it) }
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { openInNewTab(it) }
        }
    }

    private fun toast(msg: String) = Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()

    override fun onCleared() {
        pageReader.shutdown()
        saveSession()
        super.onCleared()
    }
}
