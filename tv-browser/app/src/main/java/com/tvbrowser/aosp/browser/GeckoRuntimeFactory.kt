package com.tvbrowser.aosp.browser

import android.content.Context
import android.util.Log
import com.tvbrowser.aosp.BuildConfig
import com.tvbrowser.aosp.util.Prefs
import com.tvbrowser.aosp.util.safe
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import java.io.File

/**
 * Builds the single GeckoRuntime, tuned for a quad-core ARMv7 / 1GB RAM TV.
 *
 * Biggest wins against the "white screen → slow → crash" pattern:
 *  - process count cut from 5 to 2 (UI + one content process): no GPU process, no socket
 *    process, no separate extensions process, no media (RDD/utility) processes, no prelaunch;
 *  - H.264 instead of VP9/AV1 video (hardware decoded instead of burning the CPU);
 *  - small RAM caches, no back/forward page cache, optional 30 fps and image blocking.
 *
 * Prefs without a Java setter go to a YAML file GeckoView reads at start-up; changing them
 * needs a browser restart (Settings offers it).
 */
object GeckoRuntimeFactory {

    private const val TAG = "GeckoRuntimeFactory"

    fun create(context: Context, prefs: Prefs, onEngineShutdown: () -> Unit): GeckoRuntime {
        val config = writeConfig(context, prefs)

        val settings = GeckoRuntimeSettings.Builder()
            .configFilePath(config.absolutePath)
            .javaScriptEnabled(true)
            .webFontsEnabled(true)
            .aboutConfigEnabled(true)
            .extensionsWebAPIEnabled(true)        // AMO "Add to Firefox" button works
            .extensionsProcessEnabled(false)      // run add-ons in the main process: one process less
            .inputAutoZoomEnabled(false)
            .doubleTapZoomingEnabled(false)
            .contentBlocking(contentBlocking(prefs.builtInAdBlock))
            .consoleOutput(BuildConfig.DEBUG)
            .remoteDebuggingEnabled(BuildConfig.DEBUG)
            .build()

        val runtime = GeckoRuntime.create(context, settings)
        runtime.delegate = GeckoRuntime.Delegate {
            // The engine's parent process is gone (fatal). Only a full restart recovers.
            Log.e(TAG, "Gecko runtime shut down; restarting browser")
            onEngineShutdown()
        }
        return runtime
    }

    private fun blockingCategories(enabled: Boolean): Int =
        if (enabled) {
            ContentBlocking.AntiTracking.AD or ContentBlocking.AntiTracking.ANALYTIC or
                ContentBlocking.AntiTracking.SOCIAL or ContentBlocking.AntiTracking.CRYPTOMINING or
                ContentBlocking.AntiTracking.FINGERPRINTING
        } else {
            ContentBlocking.AntiTracking.NONE
        }

    private fun contentBlocking(adBlock: Boolean): ContentBlocking.Settings =
        ContentBlocking.Settings.Builder()
            .antiTracking(blockingCategories(adBlock))
            .safeBrowsing(ContentBlocking.SafeBrowsing.NONE) // big in-memory lists: off on 1GB
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .build()

    /** Built-in ad blocker on/off at runtime (no restart). */
    fun applyBuiltInAdBlock(runtime: GeckoRuntime, enabled: Boolean) = safe(TAG) {
        runtime.settings.contentBlocking.setAntiTracking(blockingCategories(enabled))
    }

    private fun writeConfig(context: Context, prefs: Prefs): File {
        val p = LinkedHashMap<String, Any>()

        // ---- Processes: 5 → 2 ----
        p["layers.gpu-process.enabled"] = false              // compositor in main process
        p["network.process.enabled"] = false                 // no socket process
        p["media.rdd-process.enabled"] = false               // decode in content process
        p["media.utility-process.enabled"] = false
        p["dom.ipc.processCount"] = 1
        p["dom.ipc.processCount.webIsolated"] = 1
        p["dom.ipc.processPrelaunch.enabled"] = false        // no spare process waiting in RAM
        p["fission.autostart"] = false                       // no per-site processes

        // ---- Memory ----
        p["browser.sessionhistory.max_total_viewers"] = 0    // no back/forward page cache
        p["browser.sessionhistory.max_entries"] = 20
        p["browser.cache.memory.capacity"] = 8192            // 8MB RAM cache (KB)
        p["browser.cache.memory.max_entry_size"] = 1024
        p["browser.cache.disk.enable"] = true                // disk is cheap, RAM is not
        p["browser.cache.disk.smart_size.enabled"] = false
        p["browser.cache.disk.capacity"] = 102400            // 100MB
        p["image.mem.surfacecache.max_size_kb"] = 32768      // decoded images: 32MB max
        p["image.mem.surfacecache.min_expiration_ms"] = 15000
        p["image.mem.animated.discardable"] = true
        p["media.memory_cache_max_size"] = 2048
        p["media.memory_caches_combined_limit_kb"] = 8192
        p["javascript.options.mem.gc_incremental_slice_ms"] = 8
        p["dom.ipc.keepProcessesAlive.web"] = 0

        // ---- CPU (quad-core ARMv7) ----
        p["network.prefetch-next"] = false
        p["network.dns.disablePrefetch"] = true
        p["network.http.speculative-parallel-limit"] = 0
        p["network.predictor.enabled"] = false
        p["toolkit.cosmeticAnimations.enabled"] = false
        p["ui.prefersReducedMotion"] = 1
        p["general.smoothScroll"] = false                    // smooth scroll = extra frames
        p["browser.download.animateNotifications"] = false
        if (prefs.cpuSaver) p["layout.frame_rate"] = 30

        // ---- Video: hardware H.264 instead of software VP9/AV1 ----
        p["media.av1.enabled"] = false
        if (prefs.preferH264) {
            p["media.mediasource.webm.enabled"] = false      // YouTube then serves H.264 (hw decoded)
            p["media.mediasource.vp9.enabled"] = false
        }
        p["media.hardware-video-decoding.enabled"] = true
        p["media.autoplay.default"] = 0
        p["media.autoplay.blocking_policy"] = 0

        // ---- Images ----
        p["permissions.default.image"] = if (prefs.blockImages) 2 else 1

        // ---- Extensions ----
        p["xpinstall.whitelist.required"] = false
        p["extensions.webextensions.restrictedDomains"] = ""
        if (BuildConfig.ALLOW_UNSIGNED) p["xpinstall.signatures.required"] = false

        val yaml = buildString {
            append("prefs:\n")
            for ((k, v) in p) {
                val value = if (v is String) "\"" + v.replace("\"", "\\\"") + "\"" else v.toString()
                append("  ").append(k).append(": ").append(value).append('\n')
            }
        }
        val file = File(context.filesDir, "geckoview-config.yaml")
        runCatching { file.writeText(yaml) }.onFailure { Log.w(TAG, "Cannot write gecko config", it) }
        return file
    }
}
