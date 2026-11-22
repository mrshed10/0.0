package com.tvtools.floatingmenu.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

/** قراءة التطبيقات المثبتة القابلة للتشغيل (تطبيقات التلفاز Leanback والتطبيقات العادية) وتشغيلها. */
object AppRepository {

    @Suppress("DEPRECATION")
    fun loadLaunchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val result = LinkedHashMap<String, AppEntry>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(query, 0)) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg == context.packageName || result.containsKey(pkg)) continue
                result[pkg] = AppEntry(
                    packageName = pkg,
                    label = info.loadLabel(pm).toString(),
                    icon = info.loadIcon(pm),
                )
            }
        }
        return result.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /** يعيد null إذا حُذف التطبيق من الجهاز. */
    @Suppress("DEPRECATION")
    fun loadEntry(context: Context, packageName: String): AppEntry? {
        val pm = context.packageManager
        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            AppEntry(packageName, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun launch(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
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
