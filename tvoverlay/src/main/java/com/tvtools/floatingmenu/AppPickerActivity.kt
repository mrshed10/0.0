package com.tvtools.floatingmenu

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvtools.floatingmenu.data.AppEntry
import com.tvtools.floatingmenu.data.AppRepository
import com.tvtools.floatingmenu.data.Prefs
import com.tvtools.floatingmenu.ui.AppPickerAdapter
import com.tvtools.floatingmenu.ui.FocusEffects
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** اختيار التطبيقات المثبتة لإضافتها أو إزالتها من اختصارات القائمة العائمة. */
class AppPickerActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppPickerAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var search: EditText
    private lateinit var txtCount: TextView
    private lateinit var txtEmpty: TextView
    private lateinit var progress: ProgressBar

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var allApps: List<AppEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)
        prefs = Prefs(this)

        recycler = findViewById(R.id.recycler)
        search = findViewById(R.id.edit_search)
        txtCount = findViewById(R.id.txt_count)
        txtEmpty = findViewById(R.id.txt_empty)
        progress = findViewById(R.id.progress)
        val btnDone: Button = findViewById(R.id.btn_done)

        adapter = AppPickerAdapter { app, position ->
            prefs.toggleFavorite(app.packageName)
            adapter.updateSelection(prefs.favorites.toSet(), position)
            updateCount()
        }
        recycler.layoutManager = GridLayoutManager(this, spanCount())
        recycler.adapter = adapter

        search.doAfterTextChanged { applyFilter() }
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                focusFirstApp()
                true
            } else {
                false
            }
        }
        btnDone.setOnClickListener { finish() }
        FocusEffects.apply(btnDone)

        updateCount()
        loadApps()
    }

    private fun loadApps() {
        progress.isVisible = true
        executor.execute {
            val apps = AppRepository.loadLaunchableApps(applicationContext)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                allApps = apps
                progress.isVisible = false
                applyFilter()
                focusFirstApp()
            }
        }
    }

    private fun applyFilter() {
        val query = search.text.toString().trim()
        val filtered = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
        }
        adapter.submit(filtered, prefs.favorites.toSet())
        txtEmpty.isVisible = filtered.isEmpty() && !progress.isVisible
    }

    private fun focusFirstApp() {
        recycler.post { recycler.getChildAt(0)?.requestFocus() }
    }

    private fun updateCount() {
        txtCount.text = getString(R.string.picker_count, prefs.favorites.size)
    }

    /** عدد الأعمدة حسب عرض الشاشة (5 تقريباً على تلفاز 1080p). */
    private fun spanCount(): Int = ((resources.configuration.screenWidthDp - 96) / 160).coerceAtLeast(3)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            // Esc: يمسح البحث أولاً ثم يغلق الشاشة
            if (event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (search.text.isNotEmpty()) search.text.clear() else finish()
                return true
            }
            // الكتابة بالكيبورد في أي مكان تنتقل مباشرة إلى خانة البحث
            val char = event.unicodeChar
            if (!search.hasFocus() && char > 0 && Character.isLetterOrDigit(char)) {
                search.requestFocus()
                search.append(String(Character.toChars(char)))
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun finish() {
        super.finish()
        // عند الفتح من القائمة العائمة نعيد إظهارها بعد الانتهاء
        if (intent.getBooleanExtra(EXTRA_REOPEN_MENU, false) && OverlayService.isRunning) {
            OverlayService.start(this, OverlayService.ACTION_SHOW_MENU)
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_REOPEN_MENU = "reopen_menu"
    }
}
