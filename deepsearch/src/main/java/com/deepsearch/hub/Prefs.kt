package com.deepsearch.hub

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** إعدادات التطبيق (SharedPreferences بسيطة وسريعة). */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("hub_prefs", Context.MODE_PRIVATE)
    }

    var dohProviderId: String
        get() = sp.getString("doh_provider", "cloudflare")!!
        set(v) = sp.edit { putString("doh_provider", v) }

    var customDohUrl: String
        get() = sp.getString("doh_custom", "")!!
        set(v) = sp.edit { putString("doh_custom", v) }

    /** تمرير طلبات المتصفح الداخلي عبر DoH (عندما لا يكون VPN-DNS مفعّلاً). */
    var browserUseDoh: Boolean
        get() = sp.getBoolean("browser_doh", true)
        set(v) = sp.edit { putBoolean("browser_doh", v) }

    var adBlockEnabled: Boolean
        get() = sp.getBoolean("adblock", true)
        set(v) = sp.edit { putBoolean("adblock", v) }

    var javaScriptEnabled: Boolean
        get() = sp.getBoolean("js", true)
        set(v) = sp.edit { putBoolean("js", v) }

    var desktopMode: Boolean
        get() = sp.getBoolean("desktop", false)
        set(v) = sp.edit { putBoolean("desktop", v) }

    /** قاعدة بروكسي للـ WebView مثل: socks5://127.0.0.1:9050 (Orbot) أو http://host:8080 */
    var proxyRule: String
        get() = sp.getString("proxy", "")!!
        set(v) = sp.edit { putString("proxy", v) }

    var homePage: String
        get() = sp.getString("home", "https://duckduckgo.com")!!
        set(v) = sp.edit { putString("home", v) }

    var selectedEngines: Set<String>
        get() = sp.getStringSet("engines", null) ?: setOf("google", "yandex", "bing", "tineye")
        set(v) = sp.edit { putStringSet("engines", v) }
}
