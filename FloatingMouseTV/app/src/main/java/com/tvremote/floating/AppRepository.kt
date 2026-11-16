package com.tvremote.floating

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

/** تطبيق مثبّت يمكن فتحه */
data class AppEntry(val packageName: String, val label: String)

/** قراءة التطبيقات المثبتة وفتحها */
object AppRepository {

    /** كل التطبيقات التي لها أيقونة في الواجهة (العادية أو واجهة التلفاز) مرتبة أبجدياً */
    fun launchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val result = LinkedHashMap<String, AppEntry>()
        val categories = mutableListOf(Intent.CATEGORY_LAUNCHER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            categories += Intent.CATEGORY_LEANBACK_LAUNCHER
        }
        for (category in categories) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            @Suppress("DEPRECATION")
            val list = try {
                pm.queryIntentActivities(intent, 0)
            } catch (e: Exception) {
                emptyList()
            }
            for (info in list) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg == context.packageName || result.containsKey(pkg)) continue
                result[pkg] = AppEntry(pkg, info.loadLabel(pm).toString())
            }
        }
        return result.values.sortedBy { it.label.lowercase() }
    }

    fun label(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    fun icon(context: Context, pkg: String): Drawable? = try {
        context.packageManager.getApplicationIcon(pkg)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** نفضّل نشاط واجهة التلفاز (Leanback) إن وُجد */
    fun launchIntent(context: Context, pkg: String): Intent? {
        val pm = context.packageManager
        val leanback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            pm.getLeanbackLaunchIntentForPackage(pkg) else null
        return leanback ?: pm.getLaunchIntentForPackage(pkg)
    }

    fun isInstalled(context: Context, pkg: String): Boolean = launchIntent(context, pkg) != null
}
