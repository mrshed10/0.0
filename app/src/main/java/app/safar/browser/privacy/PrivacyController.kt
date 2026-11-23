package app.safar.browser.privacy

import android.util.Log
import app.safar.browser.data.BrowserSettings
import app.safar.browser.engine.BrowserTab
import app.safar.browser.engine.UrlUtils
import app.safar.browser.engine.await
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.WebExtension

/**
 * يجمع طبقتي الخصوصية:
 * 1) أصلية في Gecko (GeckoProvider): HTTPS-Only، حماية التتبع، الكوكيز، FPP.
 * 2) إضافة مدمجة privacyshield: إخفاء IP المحلي عبر WebRTC + تشويش Canvas باستثناءات.
 * استثناء موقع = السماح بالتتبع له (إذن PERMISSION_TRACKING) + تعطيل تشويش Canvas عليه.
 */
class PrivacyController(private val runtime: GeckoRuntime) {
    private var port: WebExtension.Port? = null
    private var lastConfig: JSONObject? = null

    suspend fun install() {
        val ext = runtime.webExtensionController
            .ensureBuiltIn("resource://android/assets/extensions/privacyshield/", "privacyshield@safar.app")
            .await() ?: return
        ext.setMessageDelegate(object : WebExtension.MessageDelegate {
            override fun onConnect(p: WebExtension.Port) {
                port = p
                p.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, port: WebExtension.Port) {
                        Log.w("PrivacyShield", message.toString())
                    }
                    override fun onDisconnect(port: WebExtension.Port) {
                        if (this@PrivacyController.port === port) this@PrivacyController.port = null
                    }
                })
                lastConfig?.let { p.postMessage(it) }
            }
        }, "safar_privacy")
    }

    fun push(settings: BrowserSettings) {
        val cfg = JSONObject()
            .put("hideLocalIp", settings.hideLocalIp)
            .put("blockCanvas", settings.blockCanvas)
            .put("exceptions", JSONArray(settings.siteExceptions.toList()))
        lastConfig = cfg
        port?.postMessage(cfg)
    }

    /** يُستدعى عند كل تغيير موقع: يطبّق الاستثناء على إذن حماية التتبع الخاص بالصفحة. */
    fun applyExceptionFor(tab: BrowserTab, settings: BrowserSettings): Boolean {
        val host = UrlUtils.host(tab.url) ?: return false
        val excepted = UrlUtils.matchesException(host, settings.siteExceptions)
        val perm = tab.permissions.firstOrNull { it.permission == PermissionDelegate.PERMISSION_TRACKING } ?: return false
        val want = if (excepted) PermissionDelegate.ContentPermission.VALUE_ALLOW else PermissionDelegate.ContentPermission.VALUE_DENY
        if (perm.value != want) {
            runtime.storageController.setPermission(perm, want)
            return true // يحتاج إعادة تحميل ليأخذ أثره
        }
        return false
    }
}
