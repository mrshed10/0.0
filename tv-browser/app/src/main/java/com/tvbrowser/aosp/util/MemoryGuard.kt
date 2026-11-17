package com.tvbrowser.aosp.util

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.StorageController

/**
 * RAM management for 1GB devices (runs only while the browser is visible):
 *  - every 10s checks free system RAM; when it gets close to the Low-Memory-Killer
 *    threshold, frees memory BEFORE Android kills the page (the "white screen" + crash cause):
 *    background tabs are frozen and the engine's image/network caches are dropped;
 *  - periodic cache clearing (image cache) every N minutes, configurable in Settings;
 *  - reacts to onTrimMemory() from the system.
 */
class MemoryGuard(
    private val context: Context,
    private val prefs: Prefs,
    private val runtime: () -> GeckoRuntime?,
    private val freeTabs: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private var running = false
    private var lastPressure = 0L
    private var lastPeriodicClear = SystemClock.elapsedRealtime()

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            safe(TAG) { check() }
            main.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        main.postDelayed(tick, CHECK_INTERVAL_MS)
    }

    fun stop() {
        running = false
        main.removeCallbacks(tick)
    }

    private fun check() {
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val now = SystemClock.elapsedRealtime()
        val nearLimit = info.lowMemory || info.availMem < info.threshold + SAFETY_MARGIN_BYTES
        if (nearLimit && now - lastPressure > 30_000) {
            lastPressure = now
            Log.w(TAG, "low memory: avail=${info.availMem / MB}MB threshold=${info.threshold / MB}MB")
            relieve(aggressive = true)
        }
        val minutes = prefs.cacheClearMinutes
        if (minutes > 0 && now - lastPeriodicClear > minutes * 60_000L) {
            lastPeriodicClear = now
            clearCaches(StorageController.ClearFlags.IMAGE_CACHE)
        }
    }

    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            relieve(aggressive = level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        }
    }

    /** Frees as much as possible without touching the page the user is looking at. */
    fun relieve(aggressive: Boolean) {
        safe(TAG) { freeTabs() }
        clearCaches(
            if (aggressive) StorageController.ClearFlags.IMAGE_CACHE or StorageController.ClearFlags.NETWORK_CACHE
            else StorageController.ClearFlags.IMAGE_CACHE,
        )
        System.gc()
    }

    fun clearCaches(flags: Long) {
        safe(TAG) { runtime()?.storageController?.clearData(flags) }
    }

    companion object {
        private const val TAG = "MemoryGuard"
        private const val CHECK_INTERVAL_MS = 10_000L
        private const val MB = 1024L * 1024L
        private const val SAFETY_MARGIN_BYTES = 64L * MB

        fun status(context: Context): String {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            return "${info.availMem / MB} / ${info.totalMem / MB} MB"
        }
    }
}
