package app.safar.browser.translate

/** ذاكرة LRU للترجمات: تقلل الطلبات عند العودة لنفس الصفحة أو تكرار عناصر الواجهة. */
class TranslationCache(private val maxEntries: Int = 4000) {
    private val map = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > maxEntries
    }

    private fun key(target: String, kindTag: String, text: String) = "$target\u0001$kindTag\u0001$text"

    @Synchronized fun get(target: String, kindTag: String, text: String): String? = map[key(target, kindTag, text)]

    @Synchronized fun put(target: String, kindTag: String, text: String, value: String) {
        map[key(target, kindTag, text)] = value
    }

    @Synchronized fun clear() = map.clear()
}
