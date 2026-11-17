package com.tvtools.controlpanel

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ListView
import android.widget.TextView

/**
 * مستعرض أنشطة مدمج (بديل Activity Launcher):
 *  - المستوى الأول: قائمة الحزم التي تحتوي أنشطة قابلة للفتح (حزم الإعدادات أولًا).
 *  - المستوى الثاني: أنشطة الحزمة المختارة، والضغط على OK يفتح النشاط مباشرة.
 */
class ActivityBrowserActivity : Activity() {

    companion object {
        const val EXTRA_PACKAGE = "package"
    }

    private lateinit var header: TextView
    private lateinit var list: ListView

    @Volatile
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        header = findViewById(R.id.header)
        list = findViewById(R.id.list)
        list.setOnItemClickListener { parent, _, position, _ ->
            (parent.adapter.getItem(position) as Row).onClick()
        }

        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        header.setText(R.string.loading)
        Thread {
            val rows = if (pkg == null) loadPackages() else loadActivities(pkg)
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                header.text = if (pkg == null) {
                    getString(R.string.browser_packages_header, rows.size)
                } else {
                    getString(R.string.browser_activities_header, pkg, rows.size)
                }
                list.adapter = RowAdapter(this, rows)
                list.requestFocus()
            }
        }.start()
    }

    override fun onDestroy() {
        destroyed = true
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun exportedActivities(pm: PackageManager, pkg: String): List<ActivityInfo> = try {
        pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities
            .orEmpty()
            .filter { it.exported && it.enabled }
    } catch (e: Exception) {
        // NameNotFound أو حزمة ضخمة تتجاوز حد الـ Binder
        emptyList()
    }

    private fun loadPackages(): List<Row> {
        val pm = packageManager
        @Suppress("DEPRECATION")
        val apps: List<ApplicationInfo> = pm.getInstalledApplications(0)

        data class Entry(val app: ApplicationInfo, val label: String, val count: Int)

        val entries = apps.mapNotNull { app ->
            val count = exportedActivities(pm, app.packageName).size
            if (count == 0) null else Entry(app, app.loadLabel(pm).toString(), count)
        }.sortedWith(
            compareByDescending<Entry> { it.app.packageName.contains("settings", ignoreCase = true) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
        )

        return entries.map { e ->
            Row(
                title = e.label,
                subtitle = getString(R.string.browser_package_sub, e.app.packageName, e.count),
                icon = e.app.loadIcon(pm),
            ) {
                startActivity(
                    Intent(this, ActivityBrowserActivity::class.java)
                        .putExtra(EXTRA_PACKAGE, e.app.packageName),
                )
            }
        }
    }

    private fun loadActivities(pkg: String): List<Row> {
        val pm = packageManager
        return exportedActivities(pm, pkg)
            .sortedBy { it.name }
            .map { info ->
                val shortName = info.name.removePrefix(pkg)
                Row(
                    title = info.loadLabel(pm).toString().ifBlank { shortName },
                    subtitle = shortName,
                    icon = info.loadIcon(pm),
                ) {
                    SystemIntents.launchWithFeedback(this, SystemIntents.component(pkg, info.name))
                }
            }
    }
}
