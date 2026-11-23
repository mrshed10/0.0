package app.safar.browser

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.safar.browser.data.BrowserSettings
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

data class InstallRequest(val name: String, val permissions: List<String>, val answer: CompletableDeferred<Boolean>)

class BrowserViewModel(app: Application) : AndroidViewModel(app), TabEvents {
    private val c = (app as SafarApp).container
    val settings: StateFlow<BrowserSettings> = c.settings
    val keys: SecureKeyStore = c.keys
    val bookmarks = c.bookmarks.items

    var activity: WeakReference<Activity>? = null
    private val promptDelegate = BasicPromptDelegate { activity?.get() }
    private val downloads = DownloadHandler(app)

    val tabs = TabManager(c.runtime, this)
    val privacy = PrivacyController(c.runtime)

    private val router = ProviderRouter(
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
    val translate = LiveTranslateController(
        c.runtime, viewModelScope, { settings.value }, router, OcrRouter(MlKitOcr(), TesseractOcr(app)),
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

    fun newTab(private: Boolean = false) = tabs.newTab(settings.value.homePage, private)

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
        saveSession()
        super.onCleared()
    }
}
