package com.tvbrowser.aosp.data

import com.tvbrowser.aosp.util.Prefs
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(val title: String, val url: String)

/** Favourite sites shown on the Quick Access screen. Stored as JSON in SharedPreferences. */
class Bookmarks(private val prefs: Prefs) {

    private val defaults = listOf(
        Bookmark("Google", "https://www.google.com/"),
        Bookmark("YouTube", "https://www.youtube.com/"),
        Bookmark("ويكيبيديا", "https://ar.wikipedia.org/"),
        Bookmark("الأخبار", "https://news.google.com/?hl=ar"),
        Bookmark("متجر الإضافات", "https://addons.mozilla.org/ar/firefox/extensions/"),
    )

    fun all(): List<Bookmark> {
        val json = prefs.bookmarksJson ?: return defaults
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Bookmark(o.optString("title"), o.getString("url"))
            }
        }.getOrDefault(defaults)
    }

    fun indexOf(url: String): Int = all().indexOfFirst { same(it.url, url) }

    fun add(b: Bookmark) = save(all() + b)

    fun update(index: Int, b: Bookmark) = save(all().toMutableList().also { if (index in it.indices) it[index] = b })

    fun remove(index: Int) = save(all().toMutableList().also { if (index in it.indices) it.removeAt(index) })

    fun move(index: Int, delta: Int) {
        val list = all().toMutableList()
        val to = index + delta
        if (index !in list.indices || to !in list.indices) return
        val item = list.removeAt(index)
        list.add(to, item)
        save(list)
    }

    fun reset() {
        prefs.bookmarksJson = null
    }

    private fun save(list: List<Bookmark>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("title", it.title).put("url", it.url)) }
        prefs.bookmarksJson = arr.toString()
    }

    private fun same(a: String, b: String) = a.trimEnd('/').equals(b.trimEnd('/'), ignoreCase = true)
}
