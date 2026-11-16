package com.tvremote.keyboard

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager

/**
 * الحارس: يتأكد دورياً أن الخدمة مفعّلة ومربوطة فعلاً، ويعيد تفعيلها إن أطفأها النظام.
 *
 * لماذا؟ أندرويد نفسه يُطفئ خدمة إمكانية الوصول إذا:
 *  - أوقف "منظّف الذاكرة" في الـ TV Box التطبيق قسرياً (force-stop)، أو
 *  - أُغلقت عملية التطبيق فجأة (يعتبرها النظام "انهياراً" فلا يعيد ربطها).
 * في الحالتين لا يستطيع أي كود داخل الخدمة منع ذلك، لكن يستطيع التطبيق إعادة
 * كتابة الإعداد بنفسه إذا مُنح صلاحية WRITE_SECURE_SETTINGS مرة واحدة عبر ADB:
 *   adb shell pm grant com.tvremote.keyboard android.permission.WRITE_SECURE_SETTINGS
 */
object ServiceGuard {

    private const val TAG = "ServiceGuard"
    private const val PREFS = "guard"
    private const val KEY_ENABLED = "guard_enabled"
    private const val PERIOD_MS = 60_000L
    private const val REQ_PERIODIC = 10
    private const val REQ_SOON = 11

    // ───────────────────────── الإعدادات ─────────────────────────

    fun hasPermission(context: Context): Boolean =
        context.checkPermission(
            Manifest.permission.WRITE_SECURE_SETTINGS, Process.myPid(), Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED

    /** هل يريد المستخدم إعادة التفعيل التلقائي؟ (مفعّل افتراضياً) */
    fun isGuardEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setGuardEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) schedulePeriodic(context)
    }

    fun canRepair(context: Context): Boolean = hasPermission(context) && isGuardEnabled(context)

    // ───────────────────────── الجدولة ─────────────────────────

    /** منبّه متكرر كل دقيقة تقريباً (يبقى حتى لو أُغلقت عملية التطبيق) */
    fun schedulePeriodic(context: Context) {
        try {
            alarm(context).setRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + PERIOD_MS,
                PERIOD_MS,
                pending(context, REQ_PERIODIC)
            )
        } catch (e: Exception) {
            Log.w(TAG, "schedulePeriodic failed", e)
        }
    }

    /** فحص واحد بعد ثانيتين (عند إغلاق التطبيق أو فصل الخدمة) */
    fun checkSoon(context: Context) {
        try {
            alarm(context).set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 2_000L,
                pending(context, REQ_SOON)
            )
        } catch (e: Exception) {
            Log.w(TAG, "checkSoon failed", e)
        }
    }

    private fun alarm(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pending(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, GuardReceiver::class.java).setAction(GuardReceiver.ACTION_CHECK)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context.applicationContext, requestCode, intent, flags)
    }

    // ───────────────────────── الفحص والإصلاح ─────────────────────────

    /** هل الخدمة مربوطة فعلاً من النظام (وليست مفعّلة شكلياً فقط بعد انهيار)؟ */
    fun isBound(context: Context): Boolean {
        if (KeyboardRemoteService.isRunning) return true
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val me = KeyboardRemoteService.component(context)
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val si = info.resolveInfo?.serviceInfo ?: return@any false
                    ComponentName(si.packageName, si.name) == me
                }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * يجب استدعاؤها من خيط خلفي (تنتظر ثانية بين الإطفاء والتشغيل).
     * @return true إذا قامت بإعادة التفعيل
     */
    fun repairIfNeeded(context: Context): Boolean {
        if (!canRepair(context)) return false
        val enabled = KeyboardRemoteService.isEnabled(context)
        if (enabled && isBound(context)) return false

        return try {
            val resolver = context.contentResolver
            val me = KeyboardRemoteService.component(context)
            val others = (Settings.Secure.getString(
                resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: "")
                .split(':')
                .filter { it.isNotBlank() && ComponentName.unflattenFromString(it) != me }

            // 1) نُطفئها (لمسح حالة "متوقفة/منهارة" عند النظام)
            if (enabled) {
                Settings.Secure.putString(
                    resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    others.joinToString(":")
                )
                Thread.sleep(1_000L)
            }
            // 2) نعيد تشغيلها
            Settings.Secure.putString(
                resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                (others + me.flattenToString()).joinToString(":")
            )
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Log.i(TAG, "Accessibility service re-enabled")
            true
        } catch (e: Exception) {
            Log.w(TAG, "repair failed", e)
            false
        }
    }
}
