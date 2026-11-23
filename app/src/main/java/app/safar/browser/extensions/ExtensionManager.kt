package app.safar.browser.extensions

import android.graphics.Bitmap
import app.safar.browser.engine.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

data class ExtensionInfo(
    val id: String,
    val name: String,
    val version: String,
    val enabled: Boolean,
    val builtIn: Boolean,
    val actionTitle: String?,
    val icon: Bitmap?,
)

data class RecommendedExtension(val name: String, val descriptionAr: String, val amoSlug: String) {
    val xpiUrl get() = "https://addons.mozilla.org/firefox/downloads/latest/$amoSlug/latest.xpi"
}

/**
 * إدارة إضافات WebExtensions عبر GeckoView: تثبيت من AMO أو رابط xpi، تمكين/تعطيل، إزالة،
 * وزر الإضافة (browserAction) لتثبيته في شريط الأدوات وفتح نافذتها المنبثقة.
 */
class ExtensionManager(
    private val runtime: GeckoRuntime,
    /** طلب تأكيد من المستخدم قبل التثبيت (اسم الإضافة + الأذونات). */
    private val confirmInstall: suspend (name: String, permissions: List<String>) -> Boolean,
    /** عرض نافذة الإضافة المنبثقة (popup) داخل لوح سفلي. */
    private val showPopup: (GeckoSession) -> Unit,
) {
    private val controller get() = runtime.webExtensionController
    private val extensions = mutableMapOf<String, WebExtension>()
    private val actions = mutableMapOf<String, WebExtension.Action>()
    private val icons = mutableMapOf<String, Bitmap?>()

    private val _installed = MutableStateFlow<List<ExtensionInfo>>(emptyList())
    val installed: StateFlow<List<ExtensionInfo>> = _installed

    /** المعرفات الداخلية التي لا نعرضها للمستخدم. */
    private val hiddenIds = setOf("privacyshield@safar.app", "livetranslate@safar.app")

    val recommended = listOf(
        RecommendedExtension("uBlock Origin", "حاجب إعلانات ومتتبعات قوي وخفيف", "ublock-origin"),
        RecommendedExtension("Dark Reader", "وضع داكن لكل المواقع", "darkreader"),
        RecommendedExtension("Bitwarden", "مدير كلمات مرور مفتوح المصدر", "bitwarden-password-manager"),
        RecommendedExtension("SponsorBlock", "تخطي الفقرات الإعلانية في يوتيوب", "sponsorblock"),
        RecommendedExtension("Privacy Badger", "حظر المتتبعات تلقائياً بالتعلم", "privacy-badger17"),
        RecommendedExtension("Simple Translate", "ترجمة النص المحدد بسرعة", "simple-translate"),
        RecommendedExtension("ClearURLs", "إزالة معاملات التتبع من الروابط", "clearurls"),
    )

    private val pendingConfirm = mutableMapOf<String, Boolean>()

    fun init(scope: CoroutineScope) {
        controller.promptDelegate = object : WebExtensionController.PromptDelegate {
            // GV-API: في GeckoView 129+ استُبدل onInstallPrompt بـ onInstallPromptRequest.
            // إن اختلف التوقيع في إصدارك، عدّل المعاملات وفق Javadoc الخاص بـ WebExtensionController.PromptDelegate.
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                scope.launchMain {
                    val ok = confirmInstall(extension.metaData.name ?: extension.id, permissions.toList() + origins.toList())
                    result.complete(WebExtension.PermissionPromptResponse(ok, false))
                }
                return result
            }
        }
        controller.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
            override fun onInstalled(extension: WebExtension) { register(extension) }
            override fun onUninstalled(extension: WebExtension) { scope.launchMain { refresh() } }
            override fun onEnabled(extension: WebExtension) { scope.launchMain { refresh() } }
            override fun onDisabled(extension: WebExtension) { scope.launchMain { refresh() } }
        })
    }

    suspend fun refresh() {
        val list = controller.list().await().orEmpty()
        list.forEach { register(it, publish = false) }
        publish(list)
    }

    private fun register(ext: WebExtension, publish: Boolean = true) {
        extensions[ext.id] = ext
        ext.setActionDelegate(object : WebExtension.ActionDelegate {
            override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
                actions[extension.id] = action
                action.icon?.getBitmap(96)?.accept({ bmp -> icons[extension.id] = bmp; publish(extensions.values.toList()) }, { })
                publish(extensions.values.toList())
            }

            // GV-API: الجلسة المعادة تُستخدم لعرض الـ popup
            override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? {
                val popup = GeckoSession()
                popup.open(runtime)
                showPopup(popup)
                return GeckoResult.fromValue(popup)
            }
        })
        if (publish) publish(extensions.values.toList())
    }

    private fun publish(list: List<WebExtension>) {
        _installed.value = list.filterNot { it.id in hiddenIds }.map { e ->
            ExtensionInfo(
                id = e.id,
                name = e.metaData.name ?: e.id,
                version = e.metaData.version,
                enabled = e.metaData.enabled,
                builtIn = e.isBuiltIn,
                actionTitle = actions[e.id]?.title,
                icon = icons[e.id],
            )
        }.sortedBy { it.name.lowercase() }
    }

    /** يُرجع معرّف الإضافة المثبتة. */
    suspend fun install(url: String): String? {
        val ext = controller.install(url).await() ?: return null
        register(ext)
        refresh()
        return ext.id
    }

    suspend fun uninstall(id: String) {
        extensions[id]?.let { controller.uninstall(it).await() }
        extensions.remove(id); actions.remove(id); icons.remove(id)
        refresh()
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val ext = extensions[id] ?: return
        if (enabled) controller.enable(ext, WebExtensionController.EnableSource.USER).await()
        else controller.disable(ext, WebExtensionController.EnableSource.USER).await()
        refresh()
    }

    /** ضغط زر الإضافة في شريط الأدوات. */
    fun click(id: String) { actions[id]?.click() }
}

private fun CoroutineScope.launchMain(block: suspend () -> Unit) =
    launch(Dispatchers.Main) { block() }
