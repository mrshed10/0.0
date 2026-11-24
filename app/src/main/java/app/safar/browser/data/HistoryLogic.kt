package app.safar.browser.data

import kotlinx.serialization.Serializable

@Serializable
data class HistoryEntry(val url: String, val title: String, val time: Long = System.currentTimeMillis())

/** منطق السجل (دوال نقية قابلة للاختبار). */
object HistoryLogic {
    const val MAX = 3000

    /** الأحدث أولاً؛ زيارة متكررة لنفس الرابط تُنقل للأعلى بدل التكرار. */
    fun add(list: List<HistoryEntry>, e: HistoryEntry): List<HistoryEntry> =
        (listOf(e) + list.filterNot { it.url == e.url }).take(MAX)

    fun search(list: List<HistoryEntry>, q: String): List<HistoryEntry> =
        if (q.isBlank()) list else list.filter { it.title.contains(q, true) || it.url.contains(q, true) }

    /** أكثر المواقع زيارة (لاختصارات صفحة التبويب الجديد). */
    fun topSites(list: List<HistoryEntry>, n: Int): List<HistoryEntry> =
        list.groupBy { runCatching { java.net.URI(it.url).host }.getOrNull() ?: it.url }
            .entries.sortedByDescending { it.value.size }.take(n).map { it.value.first() }
}

