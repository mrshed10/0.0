package app.safar.browser.translate.providers

import app.safar.browser.data.ProviderId
import app.safar.browser.translate.TranslationProvider
import app.safar.browser.translate.TranslationRequest
import app.safar.browser.translate.TranslationResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Google Cloud Translation v2 (Basic): الأسرع للنصوص العامة والقوائم. */
class GoogleCloudProvider(private val apiKey: () -> String?) : TranslationProvider {
    override val id = ProviderId.GOOGLE
    override fun isConfigured() = !apiKey().isNullOrBlank()

    override suspend fun translate(req: TranslationRequest): TranslationResult {
        val out = ArrayList<String>(req.texts.size)
        var detected: String? = null
        // الحد الأقصى 128 مقطعاً لكل طلب
        for (chunk in req.texts.chunked(128)) {
            val body = buildJsonObject {
                putJsonArray("q") { chunk.forEach { add(JsonPrimitive(it)) } }
                put("target", req.target)
                put("format", "text")
                if (req.source != "auto") put("source", req.source)
            }
            val res = Http.postJson(
                "https://translation.googleapis.com/language/translate/v2",
                body,
                mapOf("X-Goog-Api-Key" to apiKey()!!),
            )
            res.jsonObject["data"]!!.jsonObject["translations"]!!.jsonArray.forEach { t ->
                val o = t.jsonObject
                out += o["translatedText"]!!.jsonPrimitive.content
                detected = detected ?: o["detectedSourceLanguage"]?.jsonPrimitive?.content
            }
        }
        return TranslationResult(out, detected, id)
    }
}
