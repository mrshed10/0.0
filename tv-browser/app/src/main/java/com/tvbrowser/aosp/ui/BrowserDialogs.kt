package com.tvbrowser.aosp.ui

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import com.tvbrowser.aosp.BuildConfig
import com.tvbrowser.aosp.R
import com.tvbrowser.aosp.TvBrowserApp
import com.tvbrowser.aosp.extensions.ExtensionSources
import java.io.File
import java.io.FileInputStream

/** All toolbar dialogs. Plain AlertDialogs = fully navigable with the D-pad, near-zero RAM. */
class BrowserDialogs(private val activity: MainActivity, private val app: TvBrowserApp) {

    private fun builder() = AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)

    // ------------------------------------------------------------------ store

    /** The built-in store: one-click catalog + external stores + manual installs. */
    fun showStore() {
        val stores = app.stores
        val catalog = stores.catalog
        val storeList = stores.all()
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()

        catalog.forEach { entry ->
            labels += "⚡  ${entry.name} — ${entry.description}"
            actions += { app.extensions.install(entry.source) }
        }
        storeList.forEach { store ->
            labels += "🛒  ${store.name}"
            actions += { activity.tabs.openTab(store.url); activity.focusPage() }
        }
        labels += activity.getString(R.string.store_add)
        actions += { addStoreDialog() }
        labels += activity.getString(R.string.store_install_url)
        actions += { installUrlDialog() }
        labels += activity.getString(R.string.store_install_file)
        actions += { activity.requestFilePick() }
        if (stores.custom().isNotEmpty()) {
            labels += activity.getString(R.string.store_remove)
            actions += { removeStoreDialog() }
        }

        builder()
            .setTitle(activity.getString(if (BuildConfig.ALLOW_UNSIGNED) R.string.store_title_unsigned else R.string.store_title))
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addStoreDialog() {
        val name = EditText(activity).apply { hint = activity.getString(R.string.store_name); setSingleLine() }
        val url = EditText(activity).apply {
            hint = "https://"
            setSingleLine()
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(name); addView(url)
        }
        builder().setTitle(R.string.store_add).setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val u = app.prefs.resolveInput(url.text.toString())
                app.stores.add(name.text.toString().ifBlank { u }, u)
                activity.toast(activity.getString(R.string.store_added))
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun removeStoreDialog() {
        val custom = app.stores.custom()
        builder().setTitle(R.string.store_remove)
            .setItems(custom.map { it.name }.toTypedArray()) { _, which -> app.stores.remove(custom[which]) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun installUrlDialog() {
        val input = EditText(activity).apply {
            hint = activity.getString(R.string.store_install_url_hint)
            setSingleLine()
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        builder().setTitle(R.string.store_install_url).setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val src = ExtensionSources.fromUserInput(input.text.toString())
                if (src == null) activity.toast(activity.getString(R.string.ext_error_source))
                else app.extensions.install(src)
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    /** Fallback when the TV has no document picker: list packages in /sdcard/Download. */
    fun showDownloadsPicker() {
        if (Build.VERSION.SDK_INT in 23..32 &&
            activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
            activity.toast(activity.getString(R.string.grant_then_retry))
            return
        }
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val files = (dir.listFiles() ?: emptyArray<File>())
            .filter { it.isFile && it.extension.lowercase() in setOf("xpi", "crx", "zip") }
            .sortedByDescending { it.lastModified() }
        if (files.isEmpty()) {
            activity.toast(activity.getString(R.string.no_packages_found, dir.absolutePath))
            return
        }
        builder().setTitle(R.string.store_install_file)
            .setItems(files.map { it.name }.toTypedArray()) { _, which ->
                val f = files[which]
                app.extensions.installFromStream(FileInputStream(f), f.name)
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    // ------------------------------------------------------------------ tabs

    fun showTabs() {
        val tm = activity.tabs
        val list = tm.tabs.toList()
        val labels = ArrayList<String>()
        list.forEach { t ->
            val mark = if (t === tm.current) "▶ " else if (t.isLive) "● " else "○ "
            labels += mark + t.displayTitle.take(80)
        }
        labels += activity.getString(R.string.tab_new)
        builder()
            .setTitle(activity.getString(R.string.tabs_title, list.size, app.prefs.maxTabs))
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == list.size) {
                    tm.openTab(app.prefs.homePage)
                    activity.focusPage()
                } else {
                    tabActions(list[which])
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun tabActions(tab: com.tvbrowser.aosp.browser.BrowserTab) {
        val tm = activity.tabs
        builder().setTitle(tab.displayTitle.take(80))
            .setItems(arrayOf(activity.getString(R.string.tab_switch), activity.getString(R.string.tab_close))) { _, which ->
                if (which == 0) { tm.select(tab); activity.focusPage() } else tm.close(tab)
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }
}
