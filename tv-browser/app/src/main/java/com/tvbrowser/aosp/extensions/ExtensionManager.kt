package com.tvbrowser.aosp.extensions

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tvbrowser.aosp.BuildConfig
import com.tvbrowser.aosp.R
import com.tvbrowser.aosp.browser.DesktopMode
import com.tvbrowser.aosp.device.DeviceBridge
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Everything about WebExtensions:
 *  - install from AMO / Chrome Web Store / Edge / any URL / local file,
 *  - list, enable, disable, uninstall, options page, toolbar popup (browser action),
 *  - the built-in "tvbridge" extension that exposes device volume control to web pages.
 */
class ExtensionManager(
    private val context: Context,
    private val runtime: GeckoRuntime,
    private val deviceBridge: DeviceBridge,
) {
    companion object {
        private const val TAG = "ExtensionManager"
        const val BRIDGE_ID = "tvbridge@tvbrowser.aosp"
        const val BRIDGE_URL = "resource://android/assets/extensions/tvbridge/"
        const val NATIVE_APP = "tvbridge"
    }

    private val controller: WebExtensionController get() = runtime.webExtensionController
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val workDir = File(context.filesDir, "ext-packages")

    private var bridge: WebExtension? = null
    private val pendingSessions = ArrayList<WeakReference<GeckoSession>>()

    /** extension id -> its default browser action (toolbar button / popup). */
    private val browserActions = HashMap<String, WebExtension.Action>()

    /** Shows toasts etc. Set by the resumed activity. */
    var messageSink: ((String) -> Unit)? = null

    /** Opens an extension popup (e.g. uBlock's panel). Set by the resumed activity. */
    var popupHost: ((WebExtension) -> GeckoResult<GeckoSession>?)? = null

    /** Opens a URL (options page...) in a browser tab. Set by MainActivity. */
    var tabOpener: ((String) -> Unit)? = null

    var changeListener: (() -> Unit)? = null

    private val bridgeDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            val json = message as? JSONObject ?: return GeckoResult.fromValue(JSONObject().put("error", "bad message"))
            return GeckoResult.fromValue(deviceBridge.handle(json))
        }
    }

    private val actionDelegate = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            if (session == null) browserActions[extension.id] = action
        }

        override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? =
            popupHost?.invoke(extension)
    }

    private val tabDelegate = object : WebExtension.TabDelegate {
        override fun onNewTab(extension: WebExtension, createDetails: WebExtension.CreateTabDetails): GeckoResult<GeckoSession>? {
            createDetails.url?.let { url -> main.post { tabOpener?.invoke(url) } }
            return null
        }
    }

    init {
        controller.promptDelegate = object : WebExtensionController.PromptDelegate {
            // The user explicitly chose to install: accept the permission prompt.
            override fun onInstallPrompt(extension: WebExtension): GeckoResult<AllowOrDeny>? =
                GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }

        controller.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
            override fun onInstalled(extension: WebExtension) {
                register(extension)
                notifyChanged()
            }

            override fun onUninstalled(extension: WebExtension) {
                browserActions.remove(extension.id)
                notifyChanged()
            }

            override fun onEnabled(extension: WebExtension) {
                notifyChanged()
            }

            override fun onDisabled(extension: WebExtension) {
                notifyChanged()
            }
        })

        controller.ensureBuiltIn(BRIDGE_URL, BRIDGE_ID).accept({ ext ->
            if (ext != null) {
                bridge = ext
                val sessions = pendingSessions.mapNotNull { it.get() }
                pendingSessions.clear()
                sessions.forEach { attachBridge(it, ext) }
            }
        }, { e -> Log.e(TAG, "tvbridge failed to load", e) })

        controller.list().accept({ list -> list?.forEach { register(it) } }, { e -> Log.w(TAG, "list", e) })
    }

    // ------------------------------------------------------------------ sessions

    /** Every browsing session must be connected to the bridge to get window.tvDevice. */
    fun attachSession(session: GeckoSession) {
        val b = bridge
        if (b != null) attachBridge(session, b) else pendingSessions += WeakReference(session)
    }

    private fun attachBridge(session: GeckoSession, ext: WebExtension) {
        session.webExtensionController.setMessageDelegate(ext, bridgeDelegate, NATIVE_APP)
    }

    private fun register(ext: WebExtension) {
        if (ext.id == BRIDGE_ID) return
        ext.setActionDelegate(actionDelegate)
        ext.tabDelegate = tabDelegate
    }

    private fun notifyChanged() = main.post { changeListener?.invoke() }

    // ------------------------------------------------------------------ queries

    fun list(callback: (List<WebExtension>) -> Unit) {
        controller.list().accept({ list ->
            callback(list.orEmpty().filter { it.id != BRIDGE_ID && !it.isBuiltIn })
        }, { e ->
            Log.w(TAG, "list failed", e)
            callback(emptyList())
        })
    }

    fun hasPopup(ext: WebExtension) = browserActions.containsKey(ext.id)

    fun openPopup(ext: WebExtension): Boolean {
        val action = browserActions[ext.id] ?: return false
        action.click()
        return true
    }

    fun openOptions(ext: WebExtension): Boolean {
        val url = ext.metaData.optionsPageUrl ?: return false
        tabOpener?.invoke(url)
        return true
    }

    fun setEnabled(ext: WebExtension, enabled: Boolean, done: () -> Unit) {
        val r = if (enabled) controller.enable(ext, WebExtensionController.EnableSource.USER)
        else controller.disable(ext, WebExtensionController.EnableSource.USER)
        r.accept({ done() }, { e -> say(e?.message ?: "error"); done() })
    }

    fun uninstall(ext: WebExtension, done: () -> Unit) {
        controller.uninstall(ext).accept({ done() }, { e -> say(e?.message ?: "error"); done() })
    }

    // ------------------------------------------------------------------ install

    fun install(source: ExtensionSource) {
        when (source) {
            is ExtensionSource.Xpi -> installUrl(source.url)
            is ExtensionSource.Crx -> downloadAndInstall(source.url, source.chromeId ?: "crx-${source.url.hashCode().toUInt()}")
        }
    }

    /** Mozilla-signed .xpi: Gecko downloads, verifies and installs it itself. */
    private fun installUrl(url: String) {
        say(context.getString(R.string.ext_installing))
        controller.install(url).accept({ ext ->
            say(context.getString(R.string.ext_installed, ext?.metaData?.name ?: ""))
        }, { e -> say(describe(e)) })
    }

    /** .crx (Chrome/Brave/Kiwi/Edge): download -> convert to .xpi -> install from file. */
    private fun downloadAndInstall(url: String, id: String) {
        say(context.getString(R.string.ext_downloading))
        io.execute {
            try {
                val raw = File(workDir, "$id.download").also { workDir.mkdirs() }
                download(url, raw)
                installPackageFile(raw, id)
            } catch (e: Exception) {
                Log.w(TAG, "download failed", e)
                say(context.getString(R.string.ext_error_download, e.message ?: ""))
            }
        }
    }

    /** Local file chosen by the user (xpi / crx / zip). */
    fun installFromStream(input: InputStream, nameHint: String) {
        say(context.getString(R.string.ext_installing))
        io.execute {
            try {
                val id = nameHint.substringAfterLast('/').substringBeforeLast('.')
                    .replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "local" }
                val raw = File(workDir, "$id.download").also { workDir.mkdirs() }
                input.use { inp -> FileOutputStream(raw).use { inp.copyTo(it) } }
                installPackageFile(raw, id)
            } catch (e: Exception) {
                say(context.getString(R.string.ext_error_download, e.message ?: ""))
            }
        }
    }

    /** Runs on [io]. */
    private fun installPackageFile(raw: File, id: String) {
        val prepared = CrxConverter.prepare(raw, workDir, id)
        raw.delete()
        if (!prepared.signedByMozilla && !BuildConfig.ALLOW_UNSIGNED) {
            // Still try: some GeckoView builds allow it; otherwise we explain why.
            Log.i(TAG, "Installing unsigned package on stable engine: ${prepared.file}")
        }
        val fileUrl = Uri.fromFile(prepared.file).toString()
        main.post {
            controller.install(fileUrl).accept({ ext ->
                say(context.getString(R.string.ext_installed, ext?.metaData?.name ?: ""))
            }, { e -> say(describe(e)) })
        }
    }

    private fun download(url: String, dest: File) {
        var current = URL(url)
        repeat(6) {
            val c = current.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", DesktopMode.USER_AGENT)
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IllegalStateException("redirect without Location")
                current = URL(current, loc)
                c.disconnect()
                return@repeat
            }
            if (code != 200) {
                c.disconnect()
                throw IllegalStateException("HTTP $code")
            }
            c.inputStream.use { inp -> FileOutputStream(dest).use { inp.copyTo(it) } }
            c.disconnect()
            return
        }
        throw IllegalStateException("too many redirects")
    }

    private fun describe(e: Throwable?): String {
        if (e is WebExtension.InstallException) {
            return when (e.code) {
                WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED ->
                    context.getString(R.string.ext_error_unsigned)
                WebExtension.InstallException.ErrorCodes.ERROR_CORRUPT_FILE ->
                    context.getString(R.string.ext_error_corrupt)
                WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE ->
                    context.getString(R.string.ext_error_incompatible)
                WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE ->
                    context.getString(R.string.ext_error_network)
                WebExtension.InstallException.ErrorCodes.ERROR_BLOCKLISTED ->
                    context.getString(R.string.ext_error_blocked)
                else -> context.getString(R.string.ext_error_generic, e.code.toString())
            }
        }
        return context.getString(R.string.ext_error_generic, e?.message ?: "?")
    }

    private fun say(text: String) = main.post { messageSink?.invoke(text) }
}
