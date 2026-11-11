package com.livetranslate.screen.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.livetranslate.screen.core.LanguageRouter
import com.livetranslate.screen.core.LruCache
import com.livetranslate.screen.core.Route
import com.livetranslate.screen.core.TranslationCache
import com.livetranslate.screen.settings.LiveConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

data class TranslationResult(val text: String, val route: Route)

/**
 * محرك الترجمة على الجهاز (ML Kit):
 * - تعرّف تلقائي على لغة كل كتلة بشكل مستقل (Language ID).
 * - مجمّع مترجمات لكل زوج لغات + ذاكرة مؤقتة LRU للنتائج.
 * - تنزيل حزم اللغات في الخلفية دون حجب خط المعالجة الحي.
 */
class TranslationEngine(
    private val scope: CoroutineScope,
    private val onModelReady: () -> Unit,
    private val onDownloadingChanged: (Set<String>) -> Unit,
    private val onError: (String) -> Unit = {},
) {
    private val languageId = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.45f).build()
    )
    private val cache = TranslationCache()
    private val detectCache = LruCache<String, String>(3000)

    private val translators = object : LinkedHashMap<String, Translator>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Translator>?): Boolean {
            if (size > 4 && eldest != null) { eldest.value.close(); return true }
            return false
        }
    }
    private val models = ConcurrentHashMap<String, Deferred<Boolean>>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val downloading = ConcurrentHashMap.newKeySet<String>()

    /** تعرّف على اللغة، أو null إذا كانت غير محددة. */
    suspend fun detect(text: String): String? {
        val key = text.take(200)
        detectCache.get(key)?.let { return it.ifEmpty { null } }
        val tag = runCatching { languageId.identifyLanguage(key).await() }.getOrNull()
        val lang = LanguageRouter.normalize(tag)
        detectCache.put(key, lang ?: "")
        return lang
    }

    /** نتيجة من الذاكرة المؤقتة فقط (للعرض الفوري قبل اكتمال الدفعة). */
    fun peek(text: String, route: Route): String? = cache.get(route.source, route.target, text)

    suspend fun resolveRoute(text: String, cfg: LiveConfig, pageHint: String?): Route? {
        val detected = if (cfg.sourceLang == LanguageRouter.AUTO) {
            // النصوص القصيرة جداً غير موثوقة في التعرف ← نستخدم لغة الصفحة الغالبة
            if (text.length < 12 && pageHint != null) pageHint else detect(text) ?: pageHint
        } else null
        val route = LanguageRouter.route(detected, cfg.sourceLang, cfg.targetLang, cfg.secondaryLang, cfg.bidirectional)
            ?: return null
        if (TranslateLanguage.fromLanguageTag(route.source) == null ||
            TranslateLanguage.fromLanguageTag(route.target) == null
        ) return null
        return route
    }

    suspend fun translate(text: String, cfg: LiveConfig, pageHint: String?): TranslationResult? {
        val clean = text.trim()
        val route = resolveRoute(clean, cfg, pageHint) ?: return null
        cache.get(route.source, route.target, clean)?.let { return TranslationResult(it, route) }
        val translator = translatorFor(route)
        if (!ensureModel(route, translator, cfg.wifiOnlyDownloads)) return null
        val task = translator.translate(clean)
        // تُحفظ النتيجة في الذاكرة المؤقتة حتى لو أُلغيت الدورة الحالية
        task.addOnSuccessListener { cache.put(route.source, route.target, clean, it) }
        val out = runCatching { task.await() }
            .onFailure { onError("فشلت الترجمة: ${it.message ?: it.javaClass.simpleName}") }
            .getOrNull() ?: return null
        return TranslationResult(out, route)
    }

    @Synchronized
    private fun translatorFor(route: Route): Translator {
        val key = "${route.source}>${route.target}"
        return translators.getOrPut(key) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.fromLanguageTag(route.source)!!)
                    .setTargetLanguage(TranslateLanguage.fromLanguageTag(route.target)!!)
                    .build()
            )
        }
    }

    private suspend fun ensureModel(route: Route, translator: Translator, wifiOnly: Boolean): Boolean {
        val key = "${route.source}>${route.target}"
        val failed = failedAt[key]
        if (failed != null && System.currentTimeMillis() - failed < 30_000) return false
        val deferred = models.getOrPut(key) {
            scope.async {
                val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
                val outcome = runCatching { translator.downloadModelIfNeeded(conditions).await() }
                val ok = outcome.isSuccess
                outcome.exceptionOrNull()?.let {
                    onError("تعذّر تنزيل حزمة اللغة (${route.source} → ${route.target}): ${it.message ?: it.javaClass.simpleName}")
                }
                if (downloading.remove(key)) onDownloadingChanged(downloading.toSet())
                if (ok) {
                    failedAt.remove(key)
                    onModelReady()
                } else {
                    failedAt[key] = System.currentTimeMillis()
                    models.remove(key)
                }
                ok
            }
        }
        // إذا كان النموذج موجوداً فسيكتمل خلال أجزاء من الثانية؛ وإلا يستمر التنزيل في الخلفية
        val ready = withTimeoutOrNull(350) { deferred.await() }
        if (ready == null) {
            if (downloading.add(key)) onDownloadingChanged(downloading.toSet())
            return false
        }
        return ready
    }

    fun clearCache() = cache.clear()

    @Synchronized
    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
        languageId.close()
    }
}
