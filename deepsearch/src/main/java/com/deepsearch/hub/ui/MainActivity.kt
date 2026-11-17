package com.deepsearch.hub.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.deepsearch.hub.R
import com.deepsearch.hub.databinding.ActivityMainBinding
import com.deepsearch.hub.search.SearchViewModel

class MainActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_URL = "open_url"
        private const val TAG_SEARCH = "search"
        private const val TAG_BROWSER = "browser"
        private const val TAG_DNS = "dns"

        /** يفتح رابطاً في المتصفح الداخلي من أي مكان في التطبيق. */
        fun openUrl(context: Context, url: String) {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    private lateinit var b: ActivityMainBinding
    private val searchVm: SearchViewModel by viewModels()
    private val hubVm: HubViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        if (savedInstanceState == null) {
            // كل الشاشات تُنشأ مرة واحدة وتُخفى/تُظهر — يحافظ على حالة المتصفح
            val search = SearchFragment()
            val browser = BrowserFragment()
            val dns = DnsFragment()
            supportFragmentManager.commit {
                add(R.id.container, search, TAG_SEARCH)
                add(R.id.container, browser, TAG_BROWSER)
                add(R.id.container, dns, TAG_DNS)
                hide(browser)
                hide(dns)
            }
        }

        b.bottomNav.setOnItemSelectedListener { item ->
            showTab(
                when (item.itemId) {
                    R.id.nav_browser -> TAG_BROWSER
                    R.id.nav_dns -> TAG_DNS
                    else -> TAG_SEARCH
                }
            )
            true
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun showTab(tag: String) {
        val fm = supportFragmentManager
        fm.commit {
            listOf(TAG_SEARCH, TAG_BROWSER, TAG_DNS).forEach { t ->
                val f: Fragment = fm.findFragmentByTag(t) ?: return@forEach
                if (t == tag) show(f) else hide(f)
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when {
            intent.action == Intent.ACTION_SEND -> {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let {
                    searchVm.load(it)
                    b.bottomNav.selectedItemId = R.id.nav_search
                }
            }
            intent.action == Intent.ACTION_VIEW && intent.data != null -> openInBrowserTab(intent.dataString!!)
            intent.hasExtra(EXTRA_URL) -> openInBrowserTab(intent.getStringExtra(EXTRA_URL)!!)
        }
    }

    private fun openInBrowserTab(url: String) {
        hubVm.pendingUrl.value = url
        b.bottomNav.selectedItemId = R.id.nav_browser
    }
}
