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

/** DeepL API: صياغة أدق للفقرات في اللغات المدعومة. المفاتيح المجانية تنتهي بـ ":fx". */
class DeepLProvider(private val apiKey: () -> String?) : TranslationProvider {
    override val id = ProviderId.DEEPL
    override fun isConfigured() = !apiKey().isNullOrBlank()

    override fun supports(source: String, target: String) =
        target in SUPPORTED && (source == "auto" || source in SUPPORTED)

    override suspend fun translate(req: TranslationRequest): TranslationResult {
        val key = apiKey()!!
        val host = if (key.endsWith(":fx")) "api-free.deepl.com" else "api.deepl.com"
        val out = ArrayList<String>(req.texts.size)
        var detected: String? = null
        for (chunk in req.texts.chunked(50)) {
            val body = buildJsonObject {
                putJsonArray("text") { chunk.forEach { add(JsonPrimitive(it)) } }
                put("target_lang", targetCode(req.target))
                if (req.source != "auto") put("source_lang", req.source.uppercase())
                put("preserve_formatting", true)
                req.context?.let { put("context", it) }
            }
            val res = Http.postJson("https://$host/v2/translate", body, mapOf("Authorization" to "DeepL-Auth-Key $key"))
            res.jsonObject["translations"]!!.jsonArray.forEach { t ->
                out += t.jsonObject["text"]!!.jsonPrimitive.content
                detected = detected ?: t.jsonObject["detected_source_language"]?.jsonPrimitive?.content?.lowercase()
            }
        }
        return TranslationResult(out, detected, id)
    }

    companion object {
        val SUPPORTED = setOf(
            "ar", "bg", "cs", "da", "de", "el", "en", "es", "et", "fi", "fr", "he", "hu", "id", "it",
            "ja", "ko", "lt", "lv", "nb", "nl", "pl", "pt", "ro", "ru", "sk", "sl", "sv", "th", "tr",
            "uk", "vi", "zh",
        )

        fun targetCode(lang: String) = when (lang) {
            "en" -> "EN-US"
            "pt" -> "PT-BR"
            "zh" -> "ZH-HANS"
            else -> lang.uppercase()
        }
    }
}
