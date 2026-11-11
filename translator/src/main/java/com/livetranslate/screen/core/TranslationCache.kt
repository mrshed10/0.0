package com.livetranslate.screen.core

/**
 * ذاكرة مؤقتة LRU آمنة للخيوط لتجنب إعادة ترجمة النصوص المتكررة
 * (عناصر الواجهة الثابتة، الترجمات المرئية المتكررة...).
 */
class LruCache<K, V>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
    }

    @Synchronized fun get(key: K): V? = map[key]
    @Synchronized fun put(key: K, value: V) { map[key] = value }
    @Synchronized fun clear() = map.clear()
    @get:Synchronized val size: Int get() = map.size
}

class TranslationCache(maxSize: Int = 4000) {
    private val lru = LruCache<String, String>(maxSize)

    private fun key(src: String, tgt: String, text: String) = "$src\u0000$tgt\u0000$text"

    fun get(src: String, tgt: String, text: String): String? = lru.get(key(src, tgt, text))
    fun put(src: String, tgt: String, text: String, translated: String) = lru.put(key(src, tgt, text), translated)
    fun clear() = lru.clear()
    val size: Int get() = lru.size
}
