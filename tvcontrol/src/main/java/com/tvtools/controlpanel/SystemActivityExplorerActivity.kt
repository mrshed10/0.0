package com.tvtools.controlpanel

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/**
 * مستكشف أنشطة النظام: يعرض كل الأنشطة (ActivityInfo) المسجّلة — المُصدَّرة والداخلية —
 * داخل حزم الإعدادات وحزم النظام التي تحمل أسماء factory / engineer / dev.
 *
 * الهدف: الوصول إلى شاشة خيارات المطور أو قوائم المصنع حتى لو حذفت الشركة
 * الـ Intent القياسي ACTION_APPLICATION_DEVELOPMENT_SETTINGS.
 */
class SystemActivityExplorerActivity : Activity() {

    companion object {
        const val EXTRA_QUERY = "query"

        /** حزم تُعرض دائمًا إن وُجدت. */
        private val TARGET_PACKAGES = setOf("com.android.settings", "com.android.tv.settings")

        /** كلمات تُضاف بها أي حزمة نظام إضافية. */
        private val SYSTEM_KEYWORDS = listOf("factory", "engineer", "dev")

        /** أزرار تصفية سريعة: الكتابة بالريموت بطيئة. */
        private val QUICK_FILTERS = listOf("Dev", "Debug", "USB", "Adb", "Info", "Factory", "Hidden")
    }

    private class Item(
        val pkg: String,
        val className: String,
        val label: String,
        val exported: Boolean,
        val permission: String?,
        val icon: Drawable?,
    ) {
        val searchText = "$label $className $pkg".lowercase()
    }

    private lateinit var header: TextView
    private lateinit var countView: TextView
    private lateinit var search: EditText
    private lateinit var list: ListView
    private lateinit var adapter: RowAdapter

    private var allItems: List<Item> = emptyList()

    @Volatile
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_explorer)
        header = findViewById(R.id.header)
        countView = findViewById(R.id.count)
        search = findViewById(R.id.search)
        list = findViewById(R.id.list)

        adapter = RowAdapter(this, emptyList())
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            (adapter.getItem(position) as Row).onClick()
        }

        setupSearch()
        setupQuickFilters()

        intent.getStringExtra(EXTRA_QUERY)?.let { search.setText(it) }

        header.setText(R.string.explorer_title)
        countView.setText(R.string.loading)
        Thread {
            val items = loadItems()
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                allItems = items
                applyFilter()
                list.requestFocus()
            }
        }.start()
    }

    override fun onDestroy() {
        destroyed = true
        super.onDestroy()
    }

    private fun setupSearch() {
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })
        // زر "بحث" في لوحة المفاتيح: إخفاؤها والانتقال للقائمة مباشرة.
        search.setOnEditorActionListener { v, actionId, event ->
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || isEnter) {
                getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(v.windowToken, 0)
                list.requestFocus()
                true
            } else {
                false
            }
        }
    }

    private fun setupQuickFilters() {
        val container = findViewById<LinearLayout>(R.id.quick_filters)
        val inflater = LayoutInflater.from(this)
        val labels = listOf(getString(R.string.filter_all)) + QUICK_FILTERS
        labels.forEachIndexed { index, label ->
            val button = inflater.inflate(R.layout.item_chip, container, false) as Button
            button.text = label
            button.setOnClickListener {
                search.setText(if (index == 0) "" else label)
                search.setSelection(search.text.length)
                list.requestFocus()
            }
            container.addView(button)
        }
    }

    private fun applyFilter() {
        val query = search.text.toString().trim().lowercase()
        val filtered = if (query.isEmpty()) {
            allItems
        } else {
            allItems.filter { it.searchText.contains(query) }
        }
        adapter.submit(filtered.map(::toRow))
        countView.text = getString(R.string.explorer_count, filtered.size, allItems.size)
        if (filtered.isNotEmpty()) list.setSelection(0)
    }

    private fun toRow(item: Item): Row {
        val access = getString(if (item.exported) R.string.explorer_exported else R.string.explorer_internal)
        val permission = item.permission?.let { "  •  🔒 $it" }.orEmpty()
        val shortClass = if (item.className.startsWith(item.pkg)) {
            item.className.removePrefix(item.pkg)
        } else {
            item.className
        }
        return Row(
            title = if (item.label.isNotBlank()) "${item.label}  ($shortClass)" else shortClass,
            subtitle = "${item.pkg}  •  $access$permission",
            icon = item.icon,
        ) {
            SystemIntents.launchWithFeedback(this, SystemIntents.component(item.pkg, item.className))
        }
    }

    private fun isTargetPackage(app: ApplicationInfo): Boolean {
        if (app.packageName in TARGET_PACKAGES) return true
        val isSystem = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val name = app.packageName.lowercase()
        return isSystem && SYSTEM_KEYWORDS.any { name.contains(it) }
    }

    @Suppress("DEPRECATION")
    private fun loadItems(): List<Item> {
        val pm = packageManager
        val disabledFlag = if (Build.VERSION.SDK_INT >= 24) {
            PackageManager.MATCH_DISABLED_COMPONENTS
        } else {
            PackageManager.GET_DISABLED_COMPONENTS
        }
        val apps = pm.getInstalledApplications(0).filter(::isTargetPackage)

        val items = mutableListOf<Item>()
        for (app in apps) {
            val activities: Array<ActivityInfo> = try {
                pm.getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES or disabledFlag)
                    .activities ?: continue
            } catch (e: Exception) {
                continue
            }
            val appIcon = try {
                app.loadIcon(pm)
            } catch (e: Exception) {
                null
            }
            val appLabel = app.loadLabel(pm).toString()
            for (info in activities) {
                val label = try {
                    info.loadLabel(pm).toString()
                } catch (e: Exception) {
                    ""
                }
                items += Item(
                    pkg = info.packageName,
                    className = info.name,
                    // تسمية النشاط غالبًا تساوي اسم التطبيق، فنخفيها لتقليل التكرار.
                    label = if (label == appLabel) "" else label,
                    exported = info.exported,
                    permission = info.permission,
                    icon = appIcon,
                )
            }
        }
        // المُصدَّرة أولًا (قابلة للفتح غالبًا)، ثم حسب الحزمة والاسم.
        return items.sortedWith(
            compareByDescending<Item> { it.exported }
                .thenBy { it.pkg }
                .thenBy { it.className },
        )
    }
}
