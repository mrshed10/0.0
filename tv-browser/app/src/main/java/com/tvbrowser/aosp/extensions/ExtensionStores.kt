package com.tvbrowser.aosp.extensions

import com.tvbrowser.aosp.util.Prefs
import org.json.JSONArray
import org.json.JSONObject

/** An extension store = a website the user browses; installs are caught by ExtensionSources. */
data class ExtensionStore(val name: String, val url: String, val builtIn: Boolean)

/** One-click entries of the built-in (default) store. */
data class CatalogEntry(val name: String, val description: String, val source: ExtensionSource)

class ExtensionStores(private val prefs: Prefs) {

    val defaults = listOf(
        ExtensionStore("Firefox Add-ons (AMO)", "https://addons.mozilla.org/ar/firefox/extensions/", true),
        ExtensionStore("Chrome Web Store (Chrome · Brave · Kiwi)", "https://chromewebstore.google.com/category/extensions", true),
        ExtensionStore("Microsoft Edge Add-ons", "https://microsoftedge.microsoft.com/addons/Microsoft-Edge-Extensions-Home", true),
    )

    /** The ready-made built-in store: Mozilla-signed add-ons that work on the stable engine. */
    val catalog = listOf(
        CatalogEntry("uBlock Origin", "حاجب إعلانات خفيف جداً على الذاكرة (موصى به)", amo("ublock-origin")),
        CatalogEntry("Adblock Plus", "حاجب إعلانات شهير", amo("adblock-plus")),
        CatalogEntry("AdGuard AdBlocker", "حاجب إعلانات ومتتبعات", amo("adguard-adblocker")),
        CatalogEntry("SponsorBlock", "تخطي الفقرات الدعائية في يوتيوب", amo("sponsorblock")),
        CatalogEntry("Dark Reader", "وضع داكن لكل المواقع", amo("darkreader")),
        CatalogEntry("Privacy Badger", "حجب المتتبعات تلقائياً", amo("privacy-badger17")),
    )

    private fun amo(slug: String) = ExtensionSource.Xpi(ExtensionSources.amoLatestXpi(slug))

    fun all(): List<ExtensionStore> = defaults + custom()

    fun custom(): List<ExtensionStore> {
        val json = prefs.storesJson ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ExtensionStore(o.getString("name"), o.getString("url"), false)
            }
        }.getOrDefault(emptyList())
    }

    fun add(name: String, url: String) = save(custom() + ExtensionStore(name, url, false))

    fun remove(store: ExtensionStore) = save(custom().filterNot { it.url == store.url && it.name == store.name })

    private fun save(list: List<ExtensionStore>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url)) }
        prefs.storesJson = arr.toString()
    }
}
