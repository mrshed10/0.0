package com.deepsearch.hub.ui

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.deepsearch.hub.Prefs
import com.deepsearch.hub.R
import com.deepsearch.hub.browser.Downloader
import com.deepsearch.hub.browser.HubWebViewClient
import com.deepsearch.hub.browser.MediaLinkExtractor
import com.deepsearch.hub.browser.ProxyHelper
import com.deepsearch.hub.browser.WebViewSetup
import com.deepsearch.hub.databinding.FragmentBrowserBinding
import com.deepsearch.hub.dns.DnsVpnService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.net.URLEncoder

/** متصفح داخلي: DoH + حجب إعلانات + بروكسي اختياري + تنزيل الوسائط واستخراج روابطها. */
class BrowserFragment : Fragment(R.layout.fragment_browser) {

    private var _b: FragmentBrowserBinding? = null
    private val b get() = _b!!
    private val hubVm: HubViewModel by activityViewModels()
    private var blockedOnPage = 0

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            _b?.webView?.goBack()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentBrowserBinding.bind(view)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        setupWebView()
        setupToolbar()
        ProxyHelper.apply(requireContext(), Prefs.proxyRule)

        if (savedInstanceState == null || b.webView.restoreState(savedInstanceState) == null) {
            load(Prefs.homePage)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    hubVm.pendingUrl.collect { url ->
                        if (url != null) {
                            load(url)
                            hubVm.pendingUrl.value = null
                        }
                    }
                }
                launch { DnsVpnService.running.collect { updateShield() } }
            }
        }
    }

    private fun setupWebView() {
        val wv = b.webView
        WebViewSetup.apply(wv)
        wv.webViewClient = HubWebViewClient(
            onStarted = { url ->
                blockedOnPage = 0
                b.urlInput.setText(url)
                b.progress.show()
                updateShield()
            },
            onFinished = {
                b.progress.hide()
                updateBack()
            },
            onHistory = { updateBack() },
            onAdBlocked = {
                blockedOnPage++
                updateShield()
            },
        )
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                _b?.progress?.setProgressCompat(newProgress, true)
            }
        }
        wv.setDownloadListener(Downloader.listener(requireContext()))
        LinkActions.installLongPress(wv, requireActivity() as AppCompatActivity)
    }

    private fun setupToolbar() {
        b.urlInput.setOnEditorActionListener { v, actionId, event ->
            val go = actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (go) {
                load(v.text.toString())
                hideKeyboard(v)
            }
            go
        }
        b.btnBack.setOnClickListener { if (b.webView.canGoBack()) b.webView.goBack() }
        b.btnForward.setOnClickListener { if (b.webView.canGoForward()) b.webView.goForward() }
        b.btnReload.setOnClickListener { b.webView.reload() }
        b.btnHome.setOnClickListener { load(Prefs.homePage) }
        b.btnLinks.setOnClickListener {
            MediaLinkExtractor.extract(b.webView) { LinkSheet.show(requireActivity() as AppCompatActivity, it) }
        }
        b.btnMenu.setOnClickListener { showMenu(it) }
    }

    /** يحول النص المدخل إلى رابط أو بحث. */
    fun load(input: String) {
        val text = input.trim()
        if (text.isEmpty()) return
        val url = when {
            text.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) -> text
            !text.contains(' ') && text.contains('.') -> "https://$text"
            else -> "https://duckduckgo.com/?q=" + URLEncoder.encode(text, "UTF-8")
        }
        _b?.webView?.loadUrl(url)
    }

    private fun updateBack() {
        val wv = _b?.webView ?: return
        backCallback.isEnabled = !isHidden && wv.canGoBack()
    }

    /** مؤشر الحماية: DNS مشفر + عدد الإعلانات المحجوبة في الصفحة الحالية. */
    private fun updateShield() {
        val bb = _b ?: return
        val dns = when {
            DnsVpnService.isRunning -> getString(R.string.shield_vpn)
            Prefs.browserUseDoh -> getString(R.string.shield_doh)
            else -> getString(R.string.shield_off)
        }
        bb.shield.text = if (Prefs.adBlockEnabled) getString(R.string.shield_fmt, dns, blockedOnPage) else dns
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        updateBack()
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.inflate(R.menu.browser_menu)
        val m = popup.menu
        m.findItem(R.id.opt_adblock).isChecked = Prefs.adBlockEnabled
        m.findItem(R.id.opt_doh).isChecked = Prefs.browserUseDoh
        m.findItem(R.id.opt_js).isChecked = Prefs.javaScriptEnabled
        m.findItem(R.id.opt_desktop).isChecked = Prefs.desktopMode
        popup.setOnMenuItemClickListener { item ->
            val wv = b.webView
            when (item.itemId) {
                R.id.opt_adblock -> Prefs.adBlockEnabled = !Prefs.adBlockEnabled
                R.id.opt_doh -> Prefs.browserUseDoh = !Prefs.browserUseDoh
                R.id.opt_js -> Prefs.javaScriptEnabled = !Prefs.javaScriptEnabled
                R.id.opt_desktop -> Prefs.desktopMode = !Prefs.desktopMode
                R.id.opt_proxy -> { showProxyDialog(); return@setOnMenuItemClickListener true }
                R.id.opt_set_home -> {
                    wv.url?.let { Prefs.homePage = it }
                    toast(R.string.home_saved)
                    return@setOnMenuItemClickListener true
                }
                R.id.opt_share -> {
                    wv.url?.let { LinkActions.show(requireActivity() as AppCompatActivity, it, false) }
                    return@setOnMenuItemClickListener true
                }
                R.id.opt_clear -> {
                    wv.clearCache(true)
                    wv.clearHistory()
                    CookieManager.getInstance().removeAllCookies(null)
                    WebStorage.getInstance().deleteAllData()
                    toast(R.string.data_cleared)
                    return@setOnMenuItemClickListener true
                }
                else -> return@setOnMenuItemClickListener false
            }
            WebViewSetup.apply(wv)
            updateShield()
            wv.reload()
            true
        }
        popup.show()
    }

    private fun showProxyDialog() {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            hint = "socks5://127.0.0.1:9050"
            setText(Prefs.proxyRule)
            setSingleLine()
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(ctx).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.proxy_title)
            .setMessage(if (ProxyHelper.isSupported) R.string.proxy_msg else R.string.proxy_unsupported)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ -> applyProxy(input.text.toString()) }
            .setNeutralButton(R.string.disable) { _, _ -> applyProxy("") }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyProxy(rule: String) {
        val err = ProxyHelper.apply(requireContext(), rule)
        if (err == null) {
            Prefs.proxyRule = rule.trim()
            toast(if (rule.isBlank()) R.string.proxy_off else R.string.proxy_on)
            b.webView.reload()
        } else {
            Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
        }
    }

    private fun hideKeyboard(v: View) {
        v.clearFocus()
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun toast(res: Int) = Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _b?.webView?.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        _b?.webView?.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        _b?.webView?.onResume()
    }

    override fun onDestroyView() {
        _b?.webView?.destroy()
        _b = null
        super.onDestroyView()
    }
}
