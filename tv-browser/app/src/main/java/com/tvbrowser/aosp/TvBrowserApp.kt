package com.tvbrowser.aosp

import android.app.Application
import com.tvbrowser.aosp.browser.GeckoRuntimeFactory
import com.tvbrowser.aosp.browser.TabManager
import com.tvbrowser.aosp.data.Bookmarks
import com.tvbrowser.aosp.device.DeviceBridge
import com.tvbrowser.aosp.device.VolumeController
import com.tvbrowser.aosp.extensions.ExtensionManager
import com.tvbrowser.aosp.extensions.ExtensionStores
import com.tvbrowser.aosp.util.CrashGuard
import com.tvbrowser.aosp.util.Prefs
import com.tvbrowser.aosp.util.RestartActivity
import org.mozilla.geckoview.GeckoRuntime

/**
 * Process-wide singletons. Everything is `lazy`: GeckoView's own processes (":tab", ...) and
 * our ":restart" process also run Application.onCreate(), and must not start an engine.
 */
class TvBrowserApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
    }

    val prefs: Prefs by lazy { Prefs(this) }

    /** Exactly ONE GeckoRuntime per process. If the engine shuts down (fatal), restart cleanly. */
    val runtime: GeckoRuntime by lazy {
        GeckoRuntimeFactory.create(this, prefs) { RestartActivity.restart(this) }
    }

    val volume: VolumeController by lazy {
        VolumeController(this).also { it.showSystemUi = prefs.showSystemVolumeUi }
    }

    val deviceBridge: DeviceBridge by lazy { DeviceBridge(volume, prefs) }

    val extensions: ExtensionManager by lazy { ExtensionManager(this, runtime, deviceBridge) }

    val stores: ExtensionStores by lazy { ExtensionStores(prefs) }

    val bookmarks: Bookmarks by lazy { Bookmarks(prefs) }

    /** Set by MainActivity while it exists (Settings uses it to free memory / count tabs). */
    var tabs: TabManager? = null
}
