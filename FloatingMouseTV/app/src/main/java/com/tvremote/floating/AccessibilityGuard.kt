package com.tvremote.floating

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import android.util.Log

/**
 * يتأكد من استمرار عمل خدمة إمكانية الوصول.
 * إذا أطفأها النظام (منظّف ذاكرة أو إغلاق قسري) يعيد تفعيلها بنفسه،
 * بشرط منح صلاحية WRITE_SECURE_SETTINGS مرة واحدة عبر ADB:
 *   adb shell pm grant com.tvremote.floating android.permission.WRITE_SECURE_SETTINGS
 */
object AccessibilityGuard {

    private const val TAG = "AccessibilityGuard"

    @Volatile
    private var busy = false

    fun hasPermission(context: Context): Boolean =
        context.checkPermission(
            Manifest.permission.WRITE_SECURE_SETTINGS, Process.myPid(), Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED

    /** يعمل في خيط خلفي لأنه ينتظر ثانية بين الإطفاء والتشغيل */
    fun repairAsync(context: Context) {
        if (busy || !hasPermission(context)) return
        val enabled = ControlAccessibilityService.isEnabled(context)
        val running = ControlAccessibilityService.instance != null
        if (enabled && running) return

        busy = true
        val app = context.applicationContext
        Thread {
            try {
                repair(app, enabled)
            } finally {
                busy = false
            }
        }.start()
    }

    private fun repair(context: Context, wasEnabled: Boolean) {
        try {
            val resolver = context.contentResolver
            val me = ControlAccessibilityService.component(context)
            val others = (Settings.Secure.getString(
                resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: "")
                .split(':')
                .filter { it.isNotBlank() && ComponentName.unflattenFromString(it) != me }

            if (wasEnabled) {
                // مفعّلة شكلياً لكنها متوقفة: نطفئها ثم نشغّلها لإجبار النظام على إعادة ربطها
                Settings.Secure.putString(
                    resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others.joinToString(":")
                )
                Thread.sleep(1_000L)
            }
            Settings.Secure.putString(
                resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                (others + me.flattenToString()).joinToString(":")
            )
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Log.i(TAG, "Accessibility service re-enabled")
        } catch (e: Exception) {
            Log.w(TAG, "repair failed", e)
        }
    }
}
