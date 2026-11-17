package com.tvtools.controlpanel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * قراءة وتعديل حالة تصحيح الأخطاء (ADB).
 *
 * القراءة متاحة لأي تطبيق. أما التعديل فيحتاج WRITE_SECURE_SETTINGS،
 * وهي صلاحية لا يمنحها النظام لتطبيقات الطرف الثالث إلا عبر adb:
 *   adb shell pm grant com.tvtools.controlpanel android.permission.WRITE_SECURE_SETTINGS
 * بدونها يفتح التطبيق شاشة خيارات المطور ليقوم المستخدم بالتفعيل بنفسه.
 */
object AdbHelper {

    /** Settings.Global.ADB_WIFI_ENABLED مخفي في الـ SDK، قيمته النصية ثابتة منذ Android 11. */
    private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"

    val supportsWirelessDebugging: Boolean get() = Build.VERSION.SDK_INT >= 30

    fun grantCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    fun canWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun isDeveloperOptionsEnabled(context: Context): Boolean =
        readGlobal(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == 1

    fun isUsbDebuggingEnabled(context: Context): Boolean =
        readGlobal(context, Settings.Global.ADB_ENABLED) == 1

    fun isWirelessDebuggingEnabled(context: Context): Boolean =
        supportsWirelessDebugging && readGlobal(context, ADB_WIFI_ENABLED) == 1

    /** يفعّل خيارات المطور أيضًا عند تشغيل التصحيح، لأن بعض الأنظمة تتجاهل ADB بدونها. */
    fun setUsbDebugging(context: Context, enabled: Boolean): Boolean {
        if (enabled && !writeGlobal(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)) return false
        return writeGlobal(context, Settings.Global.ADB_ENABLED, if (enabled) 1 else 0)
    }

    fun setWirelessDebugging(context: Context, enabled: Boolean): Boolean {
        if (!supportsWirelessDebugging) return false
        if (enabled && !writeGlobal(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)) return false
        return writeGlobal(context, ADB_WIFI_ENABLED, if (enabled) 1 else 0)
    }

    /** مفاتيح سرعة الرسوم المتحركة الثلاثة (نفس ما يعدّله قسم "الرسم" في خيارات المطور). */
    val animationScaleKeys: List<String> = listOf(
        Settings.Global.WINDOW_ANIMATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        Settings.Global.ANIMATOR_DURATION_SCALE,
    )

    fun getAnimationScale(context: Context, key: String): Float =
        Settings.Global.getFloat(context.contentResolver, key, 1f)

    /** يُطبَّق فورًا: WindowManager يراقب هذه القيم ويحدّث السرعة دون إعادة تشغيل. */
    fun setAnimationScale(context: Context, key: String, value: Float): Boolean = try {
        Settings.Global.putFloat(context.contentResolver, key, value)
    } catch (e: SecurityException) {
        false
    }

    fun setDeveloperOptions(context: Context, enabled: Boolean): Boolean =
        writeGlobal(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, if (enabled) 1 else 0)

    private fun readGlobal(context: Context, key: String): Int =
        Settings.Global.getInt(context.contentResolver, key, 0)

    private fun writeGlobal(context: Context, key: String, value: Int): Boolean = try {
        Settings.Global.putInt(context.contentResolver, key, value)
    } catch (e: SecurityException) {
        false
    }
}
