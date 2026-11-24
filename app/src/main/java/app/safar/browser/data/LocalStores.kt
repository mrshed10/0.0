package app.safar.browser.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

@Serializable
data class Bookmark(val url: String, val title: String, val createdAt: Long = System.currentTimeMillis())

/** لقطة تبويب لاستعادة الجلسة. state = GeckoSession.SessionState (سجل التنقل والتمرير). */
@Serializable
data class TabSnapshot(val url: String, val title: String, val state: String? = null, val selected: Boolean = false)

/** مخزن JSON بسيط في ملف داخلي (خفيف، بدون قاعدة بيانات). */
open class JsonListStore<T>(context: Context, fileName: String, private val serializer: kotlinx.serialization.KSerializer<T>) {
    private val file = File(context.filesDir, fileName)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<T>> = _items

    private fun load(): List<T> = runCatching {
        AppJson.decodeFromString(ListSerializer(serializer), file.readText())
    }.getOrDefault(emptyList())

    suspend fun set(list: List<T>) = withContext(Dispatchers.IO) {
        _items.value = list
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(AppJson.encodeToString(ListSerializer(serializer), list))
        tmp.renameTo(file)
    }
}

class BookmarkStore(context: Context) : JsonListStore<Bookmark>(context, "bookmarks.json", Bookmark.serializer()) {
    suspend fun toggle(url: String, title: String) {
        val cur = items.value
        set(if (cur.any { it.url == url }) cur.filterNot { it.url == url } else cur + Bookmark(url, title))
    }
    fun contains(url: String) = items.value.any { it.url == url }
}

/** سجل التصفح (محلي فقط، لا يُسجل التصفح المتخفي). */
class HistoryStore(context: Context) : JsonListStore<HistoryEntry>(context, "history.json", HistoryEntry.serializer()) {
    suspend fun record(url: String, title: String) {
        if (!url.startsWith("http")) return
        set(HistoryLogic.add(items.value, HistoryEntry(url, title)))
    }
    suspend fun updateTitle(url: String, title: String) {
        val cur = items.value
        val i = cur.indexOfFirst { it.url == url }
        if (i >= 0 && cur[i].title != title && title.isNotBlank()) set(cur.toMutableList().also { it[i] = it[i].copy(title = title) })
    }
    suspend fun remove(entry: HistoryEntry) = set(items.value - entry)
    suspend fun clearSince(sinceMillis: Long) = set(items.value.filter { it.time < sinceMillis })
}

class SessionStore(context: Context) : JsonListStore<TabSnapshot>(context, "session.json", TabSnapshot.serializer())
