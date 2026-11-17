package com.tvbrowser.aosp.util

import android.app.ActivityManager
import android.content.Context

/** Detects weak hardware (1GB RAM AOSP boxes) so we can pick conservative defaults. */
object DeviceProfile {

    fun totalRamMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / (1024 * 1024)
    }

    fun availRamMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / (1024 * 1024)
    }

    /** true on "Android Go"/low-ram builds or anything with less than ~1.5GB RAM. */
    fun isLowRam(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.isLowRamDevice || totalRamMb(context) < 1600
    }
}
