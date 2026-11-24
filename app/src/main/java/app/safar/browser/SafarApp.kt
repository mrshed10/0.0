package app.safar.browser

import android.app.Application
import app.safar.browser.backup.BackupManager
import app.safar.browser.data.BookmarkStore
import app.safar.browser.data.BrowserSettings
import app.safar.browser.data.HistoryStore
import app.safar.browser.data.SecureKeyStore
import app.safar.browser.data.SessionStore
import app.safar.browser.data.SettingsRepository
import app.safar.browser.engine.GeckoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import org.mozilla.geckoview.GeckoRuntime

class SafarApp : Application() {
    /**
     * يُنشأ عند أول وصول من MainActivity فقط (وليس في Application.onCreate)، لأن GeckoView
     * يشغّل عمليات فرعية (:tab, :gpu) تمر أيضاً بـ Application.onCreate ولا يجب أن تنشئ Runtime.
     */
    val container: AppContainer by lazy { AppContainer(this) }
}

/** حاوية اعتماديات بسيطة (بدون Hilt للحفاظ على الخفة). */
class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settingsRepo = SettingsRepository(app)
    val keys = SecureKeyStore(app)
    val bookmarks = BookmarkStore(app)
    val history = HistoryStore(app)
    val sessionStore = SessionStore(app)
    val backup = BackupManager(app, settingsRepo, bookmarks, sessionStore, history)

    // قراءة واحدة سريعة متزامنة عند الإقلاع ليُنشأ المحرك بالإعدادات الصحيحة من البداية
    private val initial: BrowserSettings = runBlocking { settingsRepo.current() }

    val settings: StateFlow<BrowserSettings> =
        settingsRepo.settings.stateIn(scope, SharingStarted.Eagerly, initial)

    val runtime: GeckoRuntime = GeckoProvider.get(app, initial)
}
