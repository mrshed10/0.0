package com.tvbrowser.aosp.ui

import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
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
import com.tvbrowser.aosp.device.VolumeController
import com.tvbrowser.aosp.extensions.ExtensionSource
import com.tvbrowser.aosp.extensions.ExtensionSources
import com.tvbrowser.aosp.extensions.ExtensionsActivity
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse

/**
 * The browser screen.
 *
 *  ┌ toolbar: ◀ ▶ ⟳ ⌂ [ URL ] [تثبيت] 🛒 🧩 ▭ 🔉 🔇 🔊 🖱 ⋮ ┐
 *  │                                                       │
 *  │          GeckoView (desktop mode) + virtual cursor     │
 *  └───────────────────────────────────────────────────────┘
 *
 * Remote control:
 *  - Page: D-pad = cursor, OK = click, edges = scroll, CH+/CH- = page up/down
 *  - UP at the top of the page / MENU key = jump to the toolbar; DOWN from toolbar = back to page
 *  - BACK: exit full screen → toolbar→page → history back → toolbar → (twice) exit
 */
class MainActivity : Activity(), TabManager.Listener {

    private val app get() = application as TvBrowserApp
    private val prefs get() = app.prefs
    private val main = Handler(Looper.getMainLooper())

    private lateinit var geckoView: GeckoView
    private lateinit var cursorView: CursorView
    private lateinit var cursor: CursorController
    private lateinit var toolbar: View
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var installHere: Button
    private lateinit var tabsButton: ImageButton
    private lateinit var cursorButton: ImageButton
    private lateinit var volumeOsd: TextView
    private lateinit var focusHint: TextView

    lateinit var tabs: TabManager
        private set
    private lateinit var dialogs: BrowserDialogs

    private var isFullScreen = false
    private var lastBackPress = 0L
    private var attachToken = 0
    private var storePageSource: ExtensionSource? = null
    private val hideOsd = Runnable { volumeOsd.visibility = View.GONE }

    // ================================================================== lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()

        dialogs = BrowserDialogs(this, app)
        tabs = TabManager(app.runtime, prefs, this, PromptHandler(this), app.extensions)

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
        if (!tabs.restore(prefs.savedTabs) || startUrl != null) {
            tabs.openTab(startUrl ?: prefs.homePage)
        }
        geckoView.requestFocus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { tabs.openTab(it) }
    }

    override fun onResume() {
        super.onResume()
        val ext = app.extensions
        ext.messageSink = { toast(it) }
        ext.popupHost = { e -> PopupHost(this).open(e) }
        ext.tabOpener = { url -> tabs.openTab(url) }
        tabs.current?.session?.takeIf { it.isOpen }?.setActive(true)
    }

    override fun onPause() {
        super.onPause()
        prefs.savedTabs = tabs.serialize()
    }

    override fun onStop() {
        super.onStop()
        // Background: stop page timers/rendering and free all non-visible tabs.
        tabs.current?.session?.takeIf { it.isOpen }?.setActive(false)
        tabs.discardBackgroundTabs()
    }

    override fun onDestroy() {
        geckoView.releaseSession()
        tabs.closeAll()
        app.volume.listener = null
        app.extensions.apply { messageSink = null; popupHost = null; tabOpener = null }
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            tabs.discardBackgroundTabs()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        tabs.discardBackgroundTabs()
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            REQ_PICK_FILE -> data.data?.let { installFromUri(it) }
            REQ_EXTENSIONS -> data.getStringExtra(ExtensionsActivity.EXTRA_OPEN_URL)?.let { tabs.openTab(it) }
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
        volumeOsd = findViewById(R.id.volume_osd)
        focusHint = findViewById(R.id.focus_hint)

        click(R.id.btn_back) { tabs.current?.session?.goBack() }
        click(R.id.btn_forward) { tabs.current?.session?.goForward() }
        click(R.id.btn_reload) { tabs.current?.session?.reload() }
        click(R.id.btn_home) { tabs.current?.let { tabs.load(it, prefs.homePage) }; focusPage() }
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
        click(R.id.btn_menu) { dialogs.showSettings() }
        installHere.setOnClickListener { storePageSource?.let { app.extensions.install(it) } }

        urlBar.setOnEditorActionListener { v, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                openInput(v.text.toString())
                true
            } else false
        }
        urlBar.setOnFocusChangeListener { _, has ->
            if (has) urlBar.selectAll() else tabs.current?.let { urlBar.setText(it.url) }
            showHint(if (has) getString(R.string.hint_url) else null)
        }

        // Every toolbar control: DOWN returns to the page, focus shows a label (no hover on TV).
        val group = toolbar as android.view.ViewGroup
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            child.nextFocusDownId = R.id.gecko_view
            if (child is ImageButton) {
                child.setOnFocusChangeListener { v, has -> showHint(if (has) v.contentDescription?.toString() else null) }
            }
        }
    }

    private fun click(id: Int, action: () -> Unit) = findViewById<View>(id).setOnClickListener { action() }

    private fun showHint(text: String?) {
        focusHint.text = text
        focusHint.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    fun openInput(text: String) {
        val url = prefs.resolveInput(text)
        hideKeyboard()
        val src = ExtensionSources.interceptDownload(url)
        if (src != null) {
            app.extensions.install(src)
            return
        }
        tabs.current?.let { tabs.load(it, url) } ?: tabs.openTab(url)
        focusPage()
    }

    fun focusPage() {
        hideKeyboard()
        geckoView.requestFocus()
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
        cursor.enabled = prefs.cursorMode
        updateCursorButton()
        toast(getString(if (prefs.cursorMode) R.string.cursor_on else R.string.cursor_off))
    }

    private fun updateCursorButton() {
        cursorButton.alpha = if (prefs.cursorMode) 1f else 0.5f
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
        // Many AOSP TV builds ship without a file picker: fall back to our Download folder browser.
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

    fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    // ================================================================== keys

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode

        if (code == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) handleBack()
            return true
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (geckoView.hasFocus()) focusToolbar() else focusPage()
            }
            return true
        }
        if (geckoView.hasFocus() && cursor.handleKey(event)) return true

        // Volume keys keep their normal system behaviour; we just show our indicator afterwards.
        if (event.action == KeyEvent.ACTION_UP &&
            (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE)
        ) {
            main.postDelayed({ showVolume(app.volume.state()) }, 50)
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleBack() {
        val tab = tabs.current
        when {
            isFullScreen -> tab?.session?.exitFullScreen()
            !geckoView.hasFocus() -> focusPage()
            tab?.canGoBack == true -> tab.session?.goBack()
            tabs.tabs.size > 1 && tab != null -> tabs.close(tab)
            SystemClock.elapsedRealtime() - lastBackPress < 2000 -> finish()
            else -> {
                lastBackPress = SystemClock.elapsedRealtime()
                toast(getString(R.string.press_back_to_exit))
                focusToolbar()
            }
        }
    }

    // ================================================================== TabManager.Listener

    override fun onTabSelected(tab: BrowserTab) {
        val s = tab.session ?: return
        val token = ++attachToken
        SessionAttacher.attach(geckoView, s, app.runtime, { token == attachToken && tabs.current === tab })
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
        cursor.enabled = prefs.cursorMode
        geckoView.requestFocus()
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
            response.body?.close()
            toast(getString(R.string.downloads_unsupported))
        }
    }

    override fun interceptLoad(uri: String): Boolean {
        val src = ExtensionSources.interceptDownload(uri) ?: return false
        main.post { app.extensions.install(src) }
        return true
    }

    private fun renderTab(tab: BrowserTab) {
        if (!urlBar.hasFocus()) urlBar.setText(tab.url)
        progress.progress = tab.progress
        progress.visibility = if (tab.loading && !isFullScreen) View.VISIBLE else View.INVISIBLE
        findViewById<View>(R.id.btn_back).alpha = if (tab.canGoBack) 1f else 0.4f
        findViewById<View>(R.id.btn_forward).alpha = if (tab.canGoForward) 1f else 0.4f
        storePageSource = ExtensionSources.fromStorePage(tab.url)
        installHere.visibility = if (storePageSource != null) View.VISIBLE else View.GONE
    }

    companion object {
        private const val REQ_PICK_FILE = 10
        private const val REQ_EXTENSIONS = 11
    }
}
