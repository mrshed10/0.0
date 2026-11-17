package com.tvbrowser.aosp.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.tvbrowser.aosp.R
import com.tvbrowser.aosp.TvBrowserApp
import com.tvbrowser.aosp.browser.GeckoRuntimeFactory
import com.tvbrowser.aosp.extensions.ExtensionsActivity
import com.tvbrowser.aosp.util.CrashGuard
import com.tvbrowser.aosp.util.MemoryGuard
import com.tvbrowser.aosp.util.Prefs
import com.tvbrowser.aosp.util.RestartActivity
import com.tvbrowser.aosp.util.safe
import org.mozilla.geckoview.StorageController

/**
 * Preferences screen: one vertical list, grouped by section headers.
 * UP/DOWN moves between options (headers are skipped), OK changes / opens the option.
 * Options marked ⟳ change engine settings and apply after "Restart browser".
 */
class SettingsActivity : Activity() {

    private sealed class Row {
        class Header(val title: String) : Row()
        class Item(val title: String, val value: () -> String, val onClick: () -> Unit) : Row()
    }

    private val app get() = application as TvBrowserApp
    private val prefs get() = app.prefs
    private lateinit var list: ListView
    private val adapter = Adapter()
    private var rows: List<Row> = emptyList()

    private val bookmarkDialogs by lazy {
        BookmarkDialogs(this, app.bookmarks, prefs) { refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        list = findViewById(R.id.settings_list)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            (rows.getOrNull(pos) as? Row.Item)?.let { item -> safe(TAG) { item.onClick() }; refresh() }
        }
        findViewById<View>(R.id.settings_close).setOnClickListener { finish() }
        build()
        list.requestFocus()
        list.setSelection(1)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        build()
        adapter.notifyDataSetChanged()
    }

    private fun yes(v: Boolean) = getString(if (v) R.string.on else R.string.off)

    private fun build() {
        val engines = resources.getStringArray(R.array.search_engines)
        val r = ArrayList<Row>()
        fun header(id: Int) = r.add(Row.Header(getString(id)))
        fun item(id: Int, value: () -> String, onClick: () -> Unit) = r.add(Row.Item(getString(id), value, onClick))

        // ---------------- General
        header(R.string.sec_general)
        item(R.string.set_home_title, {
            if (prefs.homePage == Prefs.QUICK_ACCESS_URL) getString(R.string.quick_access) else prefs.homePage
        }) { chooseHome() }
        item(R.string.set_search_title, { engines[prefs.searchEngine] }) {
            choice(R.string.set_search_title, engines, prefs.searchEngine) { prefs.searchEngine = it }
        }
        item(R.string.set_restore_tabs, { yes(prefs.restoreTabs) }) { prefs.restoreTabs = !prefs.restoreTabs }

        // ---------------- Quick access
        header(R.string.sec_quick)
        item(R.string.bm_manage, { getString(R.string.count_sites, app.bookmarks.all().size) }) {
            bookmarkDialogs.manage { openInBrowser(it) }
        }
        item(R.string.bm_add, { "" }) { bookmarkDialogs.edit(null) }

        // ---------------- Ad blocking
        header(R.string.sec_adblock)
        item(R.string.set_adblock_builtin, { yes(prefs.builtInAdBlock) }) {
            prefs.builtInAdBlock = !prefs.builtInAdBlock
            GeckoRuntimeFactory.applyBuiltInAdBlock(app.runtime, prefs.builtInAdBlock)
        }
        item(R.string.set_extensions_all, { yes(prefs.extensionsEnabled) }) {
            val enable = !prefs.extensionsEnabled
            prefs.extensionsEnabled = enable
            app.extensions.setAllEnabled(enable) { toast(getString(if (enable) R.string.ext_all_on else R.string.ext_all_off)) }
        }
        item(R.string.extensions_title, { "" }) {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(this, ExtensionsActivity::class.java), REQ_EXT)
        }

        // ---------------- Performance & memory
        header(R.string.sec_performance)
        item(R.string.set_live_tabs_title, { "${prefs.maxLiveTabs}" }) {
            choice(R.string.set_live_tabs_title, (1..4).map { "$it" }.toTypedArray(), prefs.maxLiveTabs - 1) {
                prefs.maxLiveTabs = it + 1
            }
        }
        item(R.string.set_max_tabs_title, { "${prefs.maxTabs}" }) {
            choice(R.string.set_max_tabs_title, (1..8).map { "$it" }.toTypedArray(), prefs.maxTabs - 1) {
                prefs.maxTabs = it + 1
            }
        }
        val intervals = intArrayOf(0, 5, 10, 15, 30)
        item(R.string.set_cache_interval, {
            if (prefs.cacheClearMinutes == 0) getString(R.string.off) else getString(R.string.every_min, prefs.cacheClearMinutes)
        }) {
            val labels = intervals.map { if (it == 0) getString(R.string.off) else getString(R.string.every_min, it) }.toTypedArray()
            choice(R.string.set_cache_interval, labels, intervals.indexOf(prefs.cacheClearMinutes).coerceAtLeast(0)) {
                prefs.cacheClearMinutes = intervals[it]
            }
        }
        item(R.string.set_cpu_saver, { yes(prefs.cpuSaver) + " ⟳" }) { prefs.cpuSaver = !prefs.cpuSaver; needRestart() }
        item(R.string.set_h264, { yes(prefs.preferH264) + " ⟳" }) { prefs.preferH264 = !prefs.preferH264; needRestart() }
        item(R.string.set_block_images, { yes(prefs.blockImages) + " ⟳" }) { prefs.blockImages = !prefs.blockImages; needRestart() }
        item(R.string.set_free_memory, { MemoryGuard.status(this) }) {
            app.tabs?.discardBackgroundTabs()
            app.runtime.storageController.clearData(StorageController.ClearFlags.IMAGE_CACHE)
            System.gc()
            toast(getString(R.string.memory_freed))
        }

        // ---------------- Data
        header(R.string.sec_data)
        item(R.string.set_clear_cache, { "" }) {
            clear(StorageController.ClearFlags.ALL_CACHES)
        }
        item(R.string.set_clear_cookies, { "" }) {
            confirm(R.string.set_clear_cookies) {
                clear(StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.SITE_DATA)
            }
        }
        item(R.string.set_clear_all, { "" }) {
            confirm(R.string.set_clear_all) { clear(StorageController.ClearFlags.ALL) }
        }
        item(R.string.set_clear_on_exit, { yes(prefs.clearOnExit) }) { prefs.clearOnExit = !prefs.clearOnExit }

        // ---------------- Remote & volume
        header(R.string.sec_remote)
        item(R.string.set_cursor_speed_title, { "${prefs.cursorSpeed}" }) {
            choice(R.string.set_cursor_speed_title, (1..5).map { "$it" }.toTypedArray(), prefs.cursorSpeed - 1) {
                prefs.cursorSpeed = it + 1
            }
        }
        item(R.string.set_click_title, {
            getString(if (prefs.clickMode == Prefs.CLICK_MOUSE) R.string.click_mouse else R.string.click_touch)
        }) {
            prefs.clickMode = if (prefs.clickMode == Prefs.CLICK_MOUSE) Prefs.CLICK_TOUCH else Prefs.CLICK_MOUSE
        }
        item(R.string.set_page_volume, { yes(prefs.allowPageVolume) }) { prefs.allowPageVolume = !prefs.allowPageVolume }
        item(R.string.set_system_volume_ui, { yes(prefs.showSystemVolumeUi) }) {
            prefs.showSystemVolumeUi = !prefs.showSystemVolumeUi
            app.volume.showSystemUi = prefs.showSystemVolumeUi
        }

        // ---------------- Diagnostics
        header(R.string.sec_diag)
        item(R.string.set_restart, { if (prefs.restartPending) getString(R.string.restart_needed_short) else "" }) {
            confirm(R.string.set_restart) { RestartActivity.restart(this) }
        }
        item(R.string.set_last_crash, { if (CrashGuard.lastReport(this) == null) getString(R.string.none) else "…" }) {
            val text = buildString {
                append(CrashGuard.lastReport(this@SettingsActivity) ?: getString(R.string.none))
                val soft = CrashGuard.softErrors()
                if (soft.isNotEmpty()) append("\n\n").append(getString(R.string.soft_errors)).append('\n').append(soft.joinToString("\n"))
            }
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.set_last_crash).setMessage(text.take(4000))
                .setPositiveButton(android.R.string.ok, null).show()
        }
        item(R.string.set_about_config, { "" }) { openInBrowser("about:config") }

        rows = r
    }

    // ------------------------------------------------------------------ helpers

    private fun needRestart() {
        prefs.restartPending = true
        toast(getString(R.string.restart_needed))
    }

    private fun clear(flags: Long) {
        app.runtime.storageController.clearData(flags).accept(
            { toast(getString(R.string.data_cleared)) },
            { toast(it?.message ?: "error") },
        )
    }

    private fun chooseHome() {
        val items = arrayOf(getString(R.string.quick_access), getString(R.string.custom_url))
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.set_home_title)
            .setItems(items) { _, which ->
                if (which == 0) {
                    prefs.homePage = Prefs.QUICK_ACCESS_URL
                    refresh()
                } else {
                    val input = EditText(this).apply {
                        setSingleLine()
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                        setText(prefs.homePage.takeIf { it != Prefs.QUICK_ACCESS_URL } ?: "https://")
                    }
                    AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle(R.string.set_home_title).setView(input)
                        .setPositiveButton(R.string.save) { _, _ -> prefs.homePage = prefs.resolveInput(input.text.toString()); refresh() }
                        .setNegativeButton(android.R.string.cancel, null).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun choice(title: Int, items: Array<String>, selected: Int, save: (Int) -> Unit) {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(title)
            .setSingleChoiceItems(items, selected) { d, which -> save(which); d.dismiss(); refresh() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun confirm(title: Int, action: () -> Unit) {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(title).setMessage(R.string.are_you_sure)
            .setPositiveButton(android.R.string.ok) { _, _ -> safe(TAG) { action() } }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun openInBrowser(url: String) {
        setResult(RESULT_OK, Intent().putExtra(MainActivity.EXTRA_OPEN_URL, url))
        finish()
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        data?.getStringExtra(ExtensionsActivity.EXTRA_OPEN_URL)?.let { openInBrowser(it) }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1
        override fun areAllItemsEnabled() = false
        override fun isEnabled(position: Int) = rows[position] is Row.Item

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val inflater = LayoutInflater.from(parent.context)
            return when (val row = rows[position]) {
                is Row.Header -> {
                    val v = convertView ?: inflater.inflate(R.layout.item_setting_header, parent, false)
                    (v as TextView).text = row.title
                    v
                }
                is Row.Item -> {
                    val v = convertView ?: inflater.inflate(R.layout.item_setting, parent, false)
                    v.findViewById<TextView>(R.id.setting_title).text = row.title
                    val value = runCatching { row.value() }.getOrDefault("")
                    v.findViewById<TextView>(R.id.setting_value).apply {
                        text = value
                        visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
                    }
                    v
                }
            }
        }
    }

    companion object {
        private const val TAG = "SettingsActivity"
        private const val REQ_EXT = 1
    }
}
