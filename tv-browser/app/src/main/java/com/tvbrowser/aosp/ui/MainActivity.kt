package com.tvbrowser.aosp.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.tvbrowser.aosp.R
import com.tvbrowser.aosp.TvBrowserApp
import com.tvbrowser.aosp.browser.BrowserTab
import com.tvbrowser.aosp.browser.PromptHandler
import com.tvbrowser.aosp.browser.SessionAttacher
import com.tvbrowser.aosp.browser.TabManager
import com.tvbrowser.aosp.data.Bookmark
import com.tvbrowser.aosp.device.VolumeController
import com.tvbrowser.aosp.extensions.ExtensionSource
import com.tvbrowser.aosp.extensions.ExtensionSources
import com.tvbrowser.aosp.extensions.ExtensionsActivity
import com.tvbrowser.aosp.util.CrashGuard
import com.tvbrowser.aosp.util.MemoryGuard
import com.tvbrowser.aosp.util.Prefs
import com.tvbrowser.aosp.util.safe
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebResponse

/**
 * The browser screen.
 *
 *  ┌ toolbar: ◀ ▶ ⟳ ⌂ [ URL ] [تثبيت] ★ 🛒 🧩 ▭ 🔉 🔇 🔊 🖱 ⚙ ┐
 *  │   Quick Access grid (native, no engine memory)              │
 *  │   — or — GeckoView page (desktop mode) + virtual cursor     │
 *  └─────────────────────────────────────────────────────────────┘
 *
 * Remote: D-pad = cursor, OK = click, edges = scroll, CH+/CH- = page up/down,
 * UP at page top / MENU = toolbar, BACK = full screen → toolbar → history → Quick Access → exit.
 * On a Quick Access tile: OK = open, long-OK or MENU = edit/delete/move.
 */
class MainActivity : Activity(), TabManager.Listener {

    private val app get() = application as TvBrowserApp
    private val prefs get() = app.prefs
    private val main = Handler(Looper.getMainLooper())

    private lateinit var geckoView: GeckoView
    private lateinit var cursorView: CursorView
    private lateinit var cursor: CursorController
    private lateinit var toolbar: ViewGroup
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var installHere: Button
    private lateinit var tabsButton: ImageButton
    private lateinit var cursorButton: ImageButton
    private lateinit var bookmarkButton: ImageButton
    private lateinit var volumeOsd: TextView
    private lateinit var focusHint: TextView
    private lateinit var quickPanel: View
    private lateinit var quickGrid: GridView
    private lateinit var quickTitle: TextView
    private val quickAdapter = QuickAdapter()

    lateinit var tabs: TabManager
        private set
    private lateinit var dialogs: BrowserDialogs
    private lateinit var bookmarkDialogs: BookmarkDialogs
    private lateinit var memoryGuard: MemoryGuard

    private var isFullScreen = false
    private var quickVisible = false
    private var lastBackPress = 0L
    private var attachToken = 0
    private var storePageSource: ExtensionSource? = null
    private val hideOsd = Runnable { volumeOsd.visibility = View.GONE }
    private val coverColor by lazy { getColor(R.color.bg) }

    // ================================================================== lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Previous launch died before becoming stable → safe mode: don't reload the last page.
        val safeMode = CrashGuard.beginLaunch(this)

        setContentView(R.layout.activity_main)
        bindViews()

        dialogs = BrowserDialogs(this, app)
        bookmarkDialogs = BookmarkDialogs(this, app.bookmarks, prefs) { refreshQuickAccess(); renderCurrent() }
        tabs = TabManager(app.runtime, prefs, this, PromptHandler(this), app.extensions)
        app.tabs = tabs
        memoryGuard = MemoryGuard(this, prefs, { app.runtime }) { tabs.discardBackgroundTabs() }

        cursor = CursorController(
            target = geckoView,
            cursor = cursorView,
            prefs = prefs,
            session = { tabs.current?.session },
            pageScrollY = { tabs.current?.scrollY ?: 0 },
            onTopEdge = { focusToolbar() },
        )
        cursor.enabled = prefs.cursorMode
        updateCursorButton()
        app.volume.listener = { showVolume(it) }

        val startUrl = intent?.dataString
        val restored = prefs.restoreTabs && startUrl == null &&
            tabs.restore(prefs.savedTabs, loadCurrent = !safeMode)
        if (!restored) tabs.openTab(startUrl ?: prefs.homePage)

        if (safeMode) toast(getString(R.string.safe_mode_started))
        CrashGuard.takeNewReport(this)?.let { showCrashReport(it) }
        // Running 20s without dying = stable launch.
        main.postDelayed({ CrashGuard.markStable(this) }, 20_000)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        safe(TAG) { intent.dataString?.let { tabs.openTab(it) } }
    }

    override fun onResume() {
        super.onResume()
        val ext = app.extensions
        ext.messageSink = { toast(it) }
        ext.popupHost = { e -> PopupHost(this).open(e) }
        ext.tabOpener = { url -> tabs.openTab(url) }
        safe(TAG) { tabs.current?.session?.takeIf { it.isOpen }?.setActive(true) }
        refreshQuickAccess()
        if (prefs.restartPending) toast(getString(R.string.restart_needed))
    }

    override fun onStart() {
        super.onStart()
        memoryGuard.start()
    }

    override fun onPause() {
        super.onPause()
        prefs.savedTabs = tabs.serialize()
    }

    override fun onStop() {
        super.onStop()
        memoryGuard.stop()
        // Background: stop page timers/rendering and free every non-visible tab.
        safe(TAG) {
            tabs.current?.session?.takeIf { it.isOpen }?.setActive(false)
            tabs.discardBackgroundTabs()
        }
        CrashGuard.markStable(this)
    }

    override fun onDestroy() {
        safe(TAG) {
            if (isFinishing && prefs.clearOnExit) {
                app.runtime.storageController.clearData(
                    StorageController.ClearFlags.ALL_CACHES or StorageController.ClearFlags.COOKIES or
                        StorageController.ClearFlags.SITE_DATA,
                )
            }
            geckoView.releaseSession()
            tabs.closeAll()
        }
        app.tabs = null
        app.volume.listener = null
        app.extensions.apply { messageSink = null; popupHost = null; tabOpener = null }
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        memoryGuard.onTrimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        memoryGuard.relieve(aggressive = true)
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        safe(TAG) {
            when (requestCode) {
                REQ_PICK_FILE -> data.data?.let { installFromUri(it) }
                REQ_EXTENSIONS, REQ_SETTINGS -> data.getStringExtra(EXTRA_OPEN_URL)?.let { openUrl(it) }
            }
        }
    }

    // ================================================================== views

    private fun bindViews() {
        geckoView = findViewById(R.id.gecko_view)
        cursorView = findViewById(R.id.cursor_view)
        toolbar = findViewById(R.id.toolbar)
        urlBar = findViewById(R.id.url_bar)
        progress = findViewById(R.id.progress)
        installHere = findViewById(R.id.btn_install_here)
        tabsButton = findViewById(R.id.btn_tabs)
        cursorButton = findViewById(R.id.btn_cursor)
        bookmarkButton = findViewById(R.id.btn_bookmark)
        volumeOsd = findViewById(R.id.volume_osd)
        focusHint = findViewById(R.id.focus_hint)
        quickPanel = findViewById(R.id.quick_panel)
        quickGrid = findViewById(R.id.quick_grid)
        quickTitle = findViewById(R.id.quick_title)

        click(R.id.btn_back) { tabs.current?.session?.goBack() }
        click(R.id.btn_forward) { tabs.current?.session?.goForward() }
        click(R.id.btn_reload) { tabs.current?.session?.reload() }
        click(R.id.btn_home) { tabs.current?.let { tabs.goQuickAccess(it) } }
        click(R.id.btn_bookmark) { toggleBookmark() }
        click(R.id.btn_store) { dialogs.showStore() }
        click(R.id.btn_extensions) {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(this, ExtensionsActivity::class.java), REQ_EXTENSIONS)
        }
        click(R.id.btn_tabs) { dialogs.showTabs() }
        click(R.id.btn_vol_down) { app.volume.down() }
        click(R.id.btn_mute) { app.volume.toggleMute() }
        click(R.id.btn_vol_up) { app.volume.up() }
        click(R.id.btn_cursor) { toggleCursorMode() }
        click(R.id.btn_settings) {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(this, SettingsActivity::class.java), REQ_SETTINGS)
        }
        installHere.setOnClickListener { safe(TAG) { storePageSource?.let { app.extensions.install(it) } } }

        urlBar.setOnEditorActionListener { v, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                openInput(v.text.toString())
                true
            } else false
        }
        urlBar.setOnFocusChangeListener { _, has ->
            if (has) urlBar.selectAll() else renderCurrent()
            showHint(if (has) getString(R.string.hint_url) else null)
        }

        for (i in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(i)
            if (child is ImageButton) {
                child.setOnFocusChangeListener { v, has -> showHint(if (has) v.contentDescription?.toString() else null) }
            }
        }

        quickGrid.adapter = quickAdapter
        quickGrid.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ -> onQuickTile(pos) }
        quickGrid.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            onQuickTileOptions(pos)
            true
        }
    }

    private fun click(id: Int, action: () -> Unit) =
        findViewById<View>(id).setOnClickListener { safe(TAG) { action() } }

    private fun showHint(text: String?) {
        focusHint.text = text
        focusHint.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    fun openInput(text: String) = safe(TAG) {
        val url = prefs.resolveInput(text)
        hideKeyboard()
        val src = ExtensionSources.interceptDownload(url)
        if (src != null) {
            app.extensions.install(src)
            return@safe
        }
        openUrl(url)
    }

    fun openUrl(url: String) = safe(TAG) {
        tabs.current?.let { tabs.load(it, url) } ?: tabs.openTab(url)
        focusPage()
    }

    fun focusPage() {
        hideKeyboard()
        if (quickVisible) quickGrid.requestFocus() else geckoView.requestFocus()
    }

    fun focusToolbar() {
        if (isFullScreen) return
        findViewById<View>(R.id.btn_back).requestFocus()
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(urlBar.windowToken, 0)
    }

    private fun toggleCursorMode() {
        prefs.cursorMode = !prefs.cursorMode
        cursor.enabled = prefs.cursorMode && !quickVisible
        updateCursorButton()
        toast(getString(if (prefs.cursorMode) R.string.cursor_on else R.string.cursor_off))
    }

    private fun updateCursorButton() {
        cursorButton.alpha = if (prefs.cursorMode) 1f else 0.5f
    }

    private fun toggleBookmark() {
        val tab = tabs.current
        if (tab == null || tabs.isQuickAccess(tab)) {
            bookmarkDialogs.edit(null)
            return
        }
        val idx = app.bookmarks.indexOf(tab.url)
        if (idx >= 0) {
            bookmarkDialogs.options(idx) { openUrl(it) }
        } else {
            bookmarkDialogs.edit(null, Bookmark(tab.title.ifBlank { BookmarkDialogs.hostOf(tab.url) }.take(40), tab.url))
        }
    }

    fun requestFilePick() {
        val intents = listOf(
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
            Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
        )
        for (i in intents) {
            if (i.resolveActivity(packageManager) != null) {
                @Suppress("DEPRECATION")
                startActivityForResult(i, REQ_PICK_FILE)
                return
            }
        }
        dialogs.showDownloadsPicker()
    }

    private fun installFromUri(uri: Uri) {
        val stream = runCatching { contentResolver.openInputStream(uri) }.getOrNull()
        if (stream == null) {
            toast(getString(R.string.ext_error_download, uri.toString()))
            return
        }
        app.extensions.installFromStream(stream, uri.lastPathSegment ?: "local")
    }

    private fun showVolume(state: VolumeController.State) {
        volumeOsd.text = if (state.muted) getString(R.string.volume_muted)
        else getString(R.string.volume_level, state.percent)
        volumeOsd.visibility = View.VISIBLE
        main.removeCallbacks(hideOsd)
        main.postDelayed(hideOsd, 1500)
    }

    private fun showCrashReport(report: String) = safe(TAG) {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.crash_title)
            .setMessage(getString(R.string.crash_message) + "\n\n" + report.take(1500))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    fun toast(text: String) = safe(TAG) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }

    // ================================================================== Quick Access

    private fun showQuickAccess(show: Boolean) {
        quickVisible = show
        quickPanel.visibility = if (show) View.VISIBLE else View.GONE
        // Hidden GeckoView = no compositing work at all while on Quick Access.
        geckoView.visibility = if (show) View.INVISIBLE else View.VISIBLE
        cursor.enabled = prefs.cursorMode && !show
        val down = if (show) R.id.quick_grid else R.id.gecko_view
        for (i in 0 until toolbar.childCount) toolbar.getChildAt(i).nextFocusDownId = down
        if (show) {
            refreshQuickAccess()
            if (!urlBar.hasFocus()) quickGrid.requestFocus()
        }
    }

    private fun refreshQuickAccess() {
        quickAdapter.items = app.bookmarks.all()
        quickAdapter.notifyDataSetChanged()
        quickTitle.text = getString(R.string.quick_title, MemoryGuard.status(this))
    }

    private fun onQuickTile(pos: Int) {
        val items = quickAdapter.items
        if (pos < items.size) openUrl(items[pos].url) else bookmarkDialogs.edit(null)
    }

    private fun onQuickTileOptions(pos: Int) {
        if (pos < quickAdapter.items.size) bookmarkDialogs.options(pos) { openUrl(it) }
        else bookmarkDialogs.edit(null)
    }

    private inner class QuickAdapter : BaseAdapter() {
        var items: List<Bookmark> = emptyList()
        private val palette = intArrayOf(
            0xFF1E88E5.toInt(), 0xFFE53935.toInt(), 0xFF43A047.toInt(), 0xFFFB8C00.toInt(),
            0xFF8E24AA.toInt(), 0xFF00897B.toInt(), 0xFF6D4C41.toInt(), 0xFF3949AB.toInt(),
        )

        override fun getCount() = items.size + 1
        override fun getItem(position: Int): Any? = items.getOrNull(position)
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.item_quick_tile, parent, false)
            val letter = v.findViewById<TextView>(R.id.tile_letter)
            val name = v.findViewById<TextView>(R.id.tile_name)
            val bg = (letter.background.mutate() as? GradientDrawable)
            if (position < items.size) {
                val b = items[position]
                name.text = b.title
                letter.text = b.title.trim().firstOrNull()?.uppercase() ?: "★"
                bg?.setColor(palette[(b.url.hashCode() and 0x7fffffff) % palette.size])
            } else {
                name.text = getString(R.string.bm_add)
                letter.text = "+"
                bg?.setColor(0xFF37474F.toInt())
            }
            return v
        }
    }

    // ================================================================== keys

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return try {
            handleKey(event) || super.dispatchKeyEvent(event)
        } catch (t: Throwable) {
            safe(TAG) { throw t }
            true
        }
    }

    private fun handleKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) handleBack()
            return true
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP) {
                when {
                    quickVisible && quickGrid.hasFocus() -> onQuickTileOptions(quickGrid.selectedItemPosition)
                    geckoView.hasFocus() || quickGrid.hasFocus() -> focusToolbar()
                    else -> focusPage()
                }
            }
            return true
        }
        if (!quickVisible && geckoView.hasFocus() && cursor.handleKey(event)) return true

        if (event.action == KeyEvent.ACTION_UP &&
            (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE)
        ) {
            main.postDelayed({ safe(TAG) { showVolume(app.volume.state()) } }, 50)
        }
        return false
    }

    private fun handleBack() {
        val tab = tabs.current
        val pageFocused = geckoView.hasFocus() || quickGrid.hasFocus()
        when {
            isFullScreen -> tab?.session?.exitFullScreen()
            !pageFocused -> focusPage()
            tab?.canGoBack == true && tab.session != null -> tab.session?.goBack()
            tab != null && !tabs.isQuickAccess(tab) -> tabs.goQuickAccess(tab) // frees the page's RAM
            tabs.tabs.size > 1 && tab != null -> tabs.close(tab)
            SystemClock.elapsedRealtime() - lastBackPress < 2000 -> finish()
            else -> {
                lastBackPress = SystemClock.elapsedRealtime()
                toast(getString(R.string.press_back_to_exit))
            }
        }
    }

    // ================================================================== TabManager.Listener

    override fun onTabSelected(tab: BrowserTab) = safe(TAG) {
        val s = tab.session
        if (s == null) {
            geckoView.releaseSession()
            showQuickAccess(true)
        } else {
            showQuickAccess(false)
            val token = ++attachToken
            SessionAttacher.attach(geckoView, s, coverColor, { token == attachToken && tabs.current === tab })
            if (!urlBar.hasFocus()) geckoView.requestFocus()
        }
        renderTab(tab)
    }

    override fun onTabChanged(tab: BrowserTab) {
        if (tab === tabs.current) renderTab(tab)
    }

    override fun onTabListChanged() {
        tabsButton.contentDescription = getString(R.string.tabs_count, tabs.tabs.size)
    }

    override fun onBeforeSessionClose(session: GeckoSession) {
        if (geckoView.session === session) geckoView.releaseSession()
    }

    override fun onFullScreen(tab: BrowserTab, fullScreen: Boolean) {
        isFullScreen = fullScreen
        toolbar.visibility = if (fullScreen) View.GONE else View.VISIBLE
        progress.visibility = if (fullScreen) View.GONE else View.VISIBLE
        if (fullScreen) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        geckoView.requestFocus()
    }

    override fun onPageRecovered(tab: BrowserTab, gaveUp: Boolean) {
        toast(getString(if (gaveUp) R.string.page_too_heavy else R.string.page_reloaded_memory))
    }

    override fun onExternalResponse(tab: BrowserTab, response: WebResponse) {
        val type = response.headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value.orEmpty()
        val src = ExtensionSources.interceptDownload(response.uri)
            ?: when {
                type.contains("x-xpinstall") -> ExtensionSource.Xpi(response.uri)
                type.contains("x-chrome-extension") -> ExtensionSource.Crx(response.uri, null)
                else -> null
            }
        if (src != null) {
            app.extensions.install(src)
        } else {
            runCatching { response.body?.close() }
            toast(getString(R.string.downloads_unsupported))
        }
    }

    override fun interceptLoad(uri: String): Boolean {
        val src = ExtensionSources.interceptDownload(uri) ?: return false
        main.post { safe(TAG) { app.extensions.install(src) } }
        return true
    }

    private fun renderCurrent() {
        tabs.current?.let { renderTab(it) }
    }

    private fun renderTab(tab: BrowserTab) {
        val quick = tabs.isQuickAccess(tab)
        if (!urlBar.hasFocus()) urlBar.setText(if (quick || tab.url == Prefs.QUICK_ACCESS_URL) "" else tab.url)
        progress.progress = tab.progress
        progress.visibility = if (tab.loading && !isFullScreen) View.VISIBLE else View.INVISIBLE
        findViewById<View>(R.id.btn_back).alpha = if (tab.canGoBack) 1f else 0.4f
        findViewById<View>(R.id.btn_forward).alpha = if (tab.canGoForward) 1f else 0.4f
        bookmarkButton.alpha = if (!quick && app.bookmarks.indexOf(tab.url) >= 0) 1f else 0.55f
        storePageSource = if (quick) null else ExtensionSources.fromStorePage(tab.url)
        installHere.visibility = if (storePageSource != null) View.VISIBLE else View.GONE
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQ_PICK_FILE = 10
        private const val REQ_EXTENSIONS = 11
        private const val REQ_SETTINGS = 12
        const val EXTRA_OPEN_URL = "open_url"
    }
}
