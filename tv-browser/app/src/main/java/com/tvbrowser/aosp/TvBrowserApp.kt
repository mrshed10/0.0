package com.tvbrowser.aosp

import android.app.Application
import com.tvbrowser.aosp.browser.GeckoRuntimeFactory
import com.tvbrowser.aosp.device.DeviceBridge
import com.tvbrowser.aosp.device.VolumeController
import com.tvbrowser.aosp.extensions.ExtensionManager
import com.tvbrowser.aosp.extensions.ExtensionStores
import com.tvbrowser.aosp.util.Prefs
import org.mozilla.geckoview.GeckoRuntime

/**
 * Process-wide singletons.
 *
 * IMPORTANT for 1GB devices: everything here is `lazy`. GeckoView starts its content
 * processes (":tab0", ":gpu" ...) inside this same APK, and Application.onCreate() runs in
 * each of them. Creating the GeckoRuntime eagerly would waste ~30-60MB per child process.
 */
class TvBrowserApp : Application() {

    val prefs: Prefs by lazy { Prefs(this) }

    /** Exactly ONE GeckoRuntime per process (GeckoView requirement). */
    val runtime: GeckoRuntime by lazy { GeckoRuntimeFactory.create(this, prefs) }

    val volume: VolumeController by lazy { VolumeController(this) }

    val deviceBridge: DeviceBridge by lazy { DeviceBridge(volume, prefs) }

    val extensions: ExtensionManager by lazy { ExtensionManager(this, runtime, deviceBridge) }

    val stores: ExtensionStores by lazy { ExtensionStores(prefs) }
}
