package com.tvbrowser.aosp.browser

import android.content.Context
import android.util.Log
import com.tvbrowser.aosp.BuildConfig
import com.tvbrowser.aosp.util.Prefs
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import java.io.File

/**
 * Builds the single GeckoRuntime with settings tuned for a quad-core / 1GB RAM TV box.
 *
 * Gecko preferences that have no Java setter are written to a YAML config file that
 * GeckoView reads at start-up (GeckoRuntimeSettings.configFilePath).
 */
object GeckoRuntimeFactory {

    private const val TAG = "GeckoRuntimeFactory"

    fun create(context: Context, prefs: Prefs): GeckoRuntime {
        val config = writeConfig(context, prefs.lowRam)

        val blocking = ContentBlocking.Settings.Builder()
            // Built-in tracker blocking is cheap and complements uBlock/ABP.
            .antiTracking(ContentBlocking.AntiTracking.DEFAULT)
            // Safe Browsing keeps large lists in memory: disabled on 1GB devices.
            .safeBrowsing(ContentBlocking.SafeBrowsing.NONE)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .build()

        val settings = GeckoRuntimeSettings.Builder()
            .configFilePath(config.absolutePath)
            .javaScriptEnabled(true)
            .webFontsEnabled(true)
            .aboutConfigEnabled(true)
            // Lets addons.mozilla.org "Add to Firefox" button install directly (mozAddonManager).
            .extensionsWebAPIEnabled(true)
            .inputAutoZoomEnabled(false)
            .doubleTapZoomingEnabled(false)
            .contentBlocking(blocking)
            .consoleOutput(BuildConfig.DEBUG)
            .remoteDebuggingEnabled(BuildConfig.DEBUG)
            .build()

        return GeckoRuntime.create(context, settings)
    }

    private fun writeConfig(context: Context, lowRam: Boolean): File {
        val p = LinkedHashMap<String, Any>()

        // ---- Memory ----
        p["browser.sessionhistory.max_total_viewers"] = 0          // no back/forward page cache in RAM
        p["browser.sessionhistory.max_entries"] = 30
        p["browser.cache.memory.capacity"] = if (lowRam) 16384 else 32768   // KB
        p["browser.cache.disk.enable"] = true                      // prefer disk over RAM
        p["browser.cache.disk.smart_size.enabled"] = false
        p["browser.cache.disk.capacity"] = 131072                  // 128MB on disk
        p["image.mem.surfacecache.max_size_kb"] = if (lowRam) 65536 else 131072
        p["image.mem.surfacecache.min_expiration_ms"] = 30000
        p["media.memory_cache_max_size"] = if (lowRam) 4096 else 16384
        p["media.memory_caches_combined_limit_kb"] = if (lowRam) 16384 else 65536
        p["dom.ipc.processCount"] = 1                              // one content process
        p["dom.ipc.processCount.webIsolated"] = 1
        p["dom.ipc.processPrelaunch.enabled"] = false              // don't keep a spare process
        p["fission.autostart"] = false                             // no site isolation (saves 100MB+)
        p["javascript.options.mem.gc_incremental_slice_ms"] = 10

        // ---- CPU / network (quad-core ARM) ----
        p["network.prefetch-next"] = false
        p["network.dns.disablePrefetch"] = true
        p["network.http.speculative-parallel-limit"] = 0
        p["network.predictor.enabled"] = false
        p["toolkit.cosmeticAnimations.enabled"] = false
        p["ui.prefersReducedMotion"] = 1

        // ---- Media on a TV ----
        p["media.autoplay.default"] = 0                            // allow autoplay (it's a TV)
        p["media.autoplay.blocking_policy"] = 0
        p["general.smoothScroll"] = true

        // ---- Extensions ----
        p["xpinstall.whitelist.required"] = false                  // allow installs from any store
        p["extensions.webextensions.restrictedDomains"] = ""       // let ad blockers run on store pages too
        if (BuildConfig.ALLOW_UNSIGNED) {
            // Honoured only by GeckoView Nightly ("unsigned" flavor): enables Chrome/CRX add-ons.
            p["xpinstall.signatures.required"] = false
            p["extensions.experiments.enabled"] = true
        }

        val yaml = buildString {
            append("prefs:\n")
            for ((k, v) in p) {
                val value = when (v) {
                    is String -> "\"" + v.replace("\"", "\\\"") + "\""
                    else -> v.toString()
                }
                append("  ").append(k).append(": ").append(value).append('\n')
            }
        }
        val file = File(context.filesDir, "geckoview-config.yaml")
        runCatching { file.writeText(yaml) }.onFailure { Log.w(TAG, "Cannot write gecko config", it) }
        return file
    }
}
