package com.tvtools.floatingmenu.system

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** فتح شاشات النظام مع بدائل عند عدم توفرها على بعض أجهزة التلفاز. */
object SystemShortcuts {

    fun openSettings(context: Context): Boolean = open(context, Intent(Settings.ACTION_SETTINGS))

    fun openWifiSettings(context: Context): Boolean =
        open(context, Intent(Settings.ACTION_WIFI_SETTINGS)) ||
            open(context, Intent(Settings.ACTION_WIRELESS_SETTINGS)) ||
            openSettings(context)

    private fun open(context: Context, intent: Intent): Boolean {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}

/** صلاحية الظهور فوق التطبيقات الأخرى (SYSTEM_ALERT_WINDOW). */
object OverlayPermission {

    fun isGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** يفتح شاشة منح الصلاحية؛ يعيد false إذا لم تكن متوفرة (شائع على Google TV). */
    fun openSettings(activity: Activity): Boolean {
        val withPackage = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}"),
        )
        val generic = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        for (intent in listOf(withPackage, generic)) {
            try {
                activity.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                // جرّب البديل التالي
            } catch (e: SecurityException) {
                // جرّب البديل التالي
            }
        }
        return false
    }

    fun adbCommand(context: Context): String =
        "adb shell appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow"
}
