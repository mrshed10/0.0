package app.safar.browser.translate.providers

import app.safar.browser.data.ProviderId
import app.safar.browser.translate.ProviderException
import app.safar.browser.translate.TranslationProvider
import app.safar.browser.translate.TranslationRequest
import app.safar.browser.translate.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * ترجمة Google المجانية (نقطة الويب العامة client=gtx) — تعمل بدون مفتاح API.
 * ليست واجهة رسمية مضمونة: قد تُقيَّد عند كثرة الطلبات، لذلك يلجأ الموجّه بعدها إلى ML Kit على الجهاز.
 */
class GoogleFreeProvider : TranslationProvider {
    override val id = ProviderId.GOOGLE_FREE
    override fun isConfigured() = true

    private val limiter = Semaphore(3)

    override suspend fun translate(req: TranslationRequest): TranslationResult = coroutineScope {
        // نجمع المقاطع بفاصل أسطر في طلبات ≤ 4000 حرف، ثم نقسم الرد. عند اختلاف العدد نترجم كل مقطع وحده.
        val clean = req.texts.map { it.replace('\n', ' ').trim() }
        val chunks = chunk(clean, 4000)
        var detected: String? = null
        val parts = chunks.map { chunk ->
            async {
                limiter.withPermit {
                    val (joined, lang) = call(chunk.joinToString("\n"), req.source, req.target)
                    detected = detected ?: lang
                    val lines = joined.split('\n')
                    if (lines.size == chunk.size) lines
                    else chunk.map { one -> call(one, req.source, req.target).first }
                }
            }
        }.awaitAll()
        TranslationResult(parts.flatten().map { it.trim() }, detected, id)
    }

    private suspend fun call(text: String, source: String, target: String): Pair<String, String?> =
        withContext(Dispatchers.IO) {
            val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
                .addQueryParameter("client", "gtx")
                .addQueryParameter("sl", if (source == "auto") "auto" else source)
                .addQueryParameter("tl", target)
                .addQueryParameter("dt", "t")
                .build()
            val req = Request.Builder().url(url)
                .post(FormBody.Builder().add("q", text).build())
                .header("User-Agent", "Mozilla/5.0 (Android)")
                .build()
            Http.client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw ProviderException("Google free HTTP ${res.code}")
                parse(body)
            }
        }

    companion object {
        /** الرد: [[["مترجم","أصل",...],...], null, "en", ...] */
        fun parse(body: String): Pair<String, String?> {
            val root = Json.parseToJsonElement(body) as? JsonArray ?: throw ProviderException("Google free: bad response")
            val sentences = root.getOrNull(0) as? JsonArray ?: throw ProviderException("Google free: no sentences")
            val text = sentences.joinToString("") { s ->
                ((s as? JsonArray)?.getOrNull(0) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull.orEmpty()
            }
            val lang = (root.getOrNull(2) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
            return text to lang
        }

        fun chunk(texts: List<String>, maxChars: Int): List<List<String>> {
            val out = mutableListOf<MutableList<String>>()
            var size = 0
            for (t in texts) {
                if (out.isEmpty() || size + t.length + 1 > maxChars) { out += mutableListOf<String>(); size = 0 }
                out.last() += t; size += t.length + 1
            }
            return out
        }
    }
}
