package com.deepsearch.hub.search

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.deepsearch.hub.R
import com.deepsearch.hub.browser.Downloader
import com.deepsearch.hub.browser.HubWebViewClient
import com.deepsearch.hub.browser.MediaLinkExtractor
import com.deepsearch.hub.browser.WebViewSetup
import com.deepsearch.hub.databinding.ActivitySearchResultsBinding
import com.deepsearch.hub.ui.LinkActions
import com.deepsearch.hub.ui.LinkSheet
import com.deepsearch.hub.ui.MainActivity
import com.deepsearch.hub.util.MediaUtils
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * البحث العكسي العميق: يرفع الصورة لكل المحركات المختارة بالتوازي،
 * كل محرك في تبويب مستقل، مع أداة استخراج روابط الصور/الفيديو الأصلية من النتائج.
 */
class SearchResultsActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_FILE = "file"
        private const val EXTRA_ENGINES = "engines"

        fun start(context: Context, file: File, engines: Collection<String>) {
            context.startActivity(
                Intent(context, SearchResultsActivity::class.java)
                    .putExtra(EXTRA_FILE, file.absolutePath)
                    .putStringArrayListExtra(EXTRA_ENGINES, ArrayList(engines))
            )
        }
    }

    private lateinit var b: ActivitySearchResultsBinding
    private lateinit var imageFile: File
    private lateinit var engines: List<SearchEngine>
    private val webViews = LinkedHashMap<String, WebView>()
    private var jpeg: ByteArray? = null
    private var currentId = ""
    private val current: WebView? get() = webViews[currentId]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySearchResultsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        b.toolbar.setNavigationOnClickListener { finish() }

        val path = intent.getStringExtra(EXTRA_FILE)
        if (path == null || !File(path).exists()) {
            finish()
            return
        }
        // نسخة خاصة بهذه الجلسة حتى لا يغيّرها المحرر أثناء البحث
        imageFile = File(MediaUtils.workDir(this), "search_${System.currentTimeMillis()}.jpg")
        File(path).copyTo(imageFile, overwrite = true)

        b.thumb.setImageBitmap(BitmapFactory.decodeFile(imageFile.path, BitmapFactory.Options().apply { inSampleSize = 8 }))
        engines = SearchEngines.byIds(intent.getStringArrayListExtra(EXTRA_ENGINES).orEmpty())

        engines.forEach { e ->
            b.tabs.addTab(b.tabs.newTab().setText(e.name).setTag(e.id))
            val wv = createWebView(e)
            webViews[e.id] = wv
            b.webContainer.addView(wv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        b.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = select(tab.tag as String)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        select(engines.first().id)

        onBackPressedDispatcher.addCallback(this) {
            val wv = current
            if (wv != null && wv.canGoBack()) wv.goBack() else finish()
        }

        lifecycleScope.launch {
            jpeg = withContext(Dispatchers.Default) { MediaUtils.compressForUpload(imageFile) }
            engines.forEach { submit(it) } // رفع متوازٍ لكل المحركات
        }
    }

    private fun createWebView(e: SearchEngine) = WebView(this).apply {
        isVisible = false
        WebViewSetup.apply(this, thirdPartyCookies = true)
        webViewClient = HubWebViewClient(
            onStarted = { if (currentId == e.id) b.progress.show() },
            onFinished = { url -> if (currentId == e.id) { b.progress.hide(); b.toolbar.subtitle = Uri.parse(url).host } },
        )
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (currentId == e.id) b.progress.setProgressCompat(newProgress, true)
            }

            // الرفع اليدوي: أي زر "رفع صورة" في موقع المحرك يستلم صورتنا مباشرة
            override fun onShowFileChooser(
                view: WebView, callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams,
            ): Boolean {
                callback.onReceiveValue(arrayOf(imageUri()))
                return true
            }
        }
        setDownloadListener(Downloader.listener(this@SearchResultsActivity))
        LinkActions.installLongPress(this, this@SearchResultsActivity)
    }

    private fun imageUri(): Uri = FileProvider.getUriForFile(this, "$packageName.files", imageFile)

    private fun submit(e: SearchEngine) {
        val data = jpeg ?: return
        // نعيد بناء المحرك للحصول على طابع زمني جديد (Google Lens)
        val fresh = SearchEngines.all().first { it.id == e.id }
        webViews[e.id]?.loadDataWithBaseURL(
            "https://deepsearch.hub/",
            SearchEngines.buildSubmitPage(fresh, data, getString(R.string.uploading_to)),
            "text/html", "utf-8", null,
        )
    }

    private fun select(id: String) {
        currentId = id
        webViews.forEach { (k, v) -> v.isVisible = k == id }
        b.toolbar.subtitle = current?.url?.let { Uri.parse(it).host }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.results_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val wv = current ?: return false
        val engine = engines.firstOrNull { it.id == currentId } ?: return false
        when (item.itemId) {
            R.id.action_links -> MediaLinkExtractor.extract(wv) { links -> LinkSheet.show(this, links) }
            R.id.action_reload -> submit(engine)
            R.id.action_manual -> {
                wv.loadUrl(engine.homeUrl)
                Toast.makeText(this, R.string.manual_upload_hint, Toast.LENGTH_LONG).show()
            }
            R.id.action_open_hub -> wv.url?.let { MainActivity.openUrl(this, it) }
            R.id.action_open_external -> wv.url?.let {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onDestroy() {
        webViews.values.forEach {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        webViews.clear()
        if (::imageFile.isInitialized && isFinishing) imageFile.delete()
        super.onDestroy()
    }
}
