package app.safar.browser.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission

/** حالة تبويب واحد قابلة للمراقبة من Compose. */
class BrowserTab(
    val id: Long,
    val session: GeckoSession,
    val isPrivate: Boolean,
) {
    var url by mutableStateOf("about:blank")
    var title by mutableStateOf("")
    var progress by mutableStateOf(0)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var isSecure by mutableStateOf(false)
    var mediaPlaying by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    /** وضع الموقع المصمم للكمبيوتر لهذا التبويب. */
    var desktopMode by mutableStateOf(false)
    /** مجموعة التبويب (null = بدون مجموعة). */
    var groupId by mutableStateOf<Long?>(null)

    /** آخر SessionState مسلسل (لاستعادة الجلسة). */
    var sessionState: String? = null

    /** أذونات الصفحة الحالية من Gecko (منها إذن حماية التتبع لاستثناء المواقع). */
    var permissions: List<ContentPermission> = emptyList()
}
