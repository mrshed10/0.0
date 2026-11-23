package app.safar.browser.translate.providers

import app.safar.browser.data.ProviderId
import app.safar.browser.translate.LlmPrompt
import app.safar.browser.translate.ProviderException
import app.safar.browser.translate.TranslationProvider
import app.safar.browser.translate.TranslationRequest
import app.safar.browser.translate.TranslationResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.addJsonObject

/** Gemini عبر REST (generateContent) مع طلب رد بصيغة JSON. */
class GeminiProvider(
    private val apiKey: () -> String?,
    private val model: () -> String,
) : TranslationProvider {
    override val id = ProviderId.GEMINI
    override fun isConfigured() = !apiKey().isNullOrBlank()

    override suspend fun translate(req: TranslationRequest): TranslationResult {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", LlmPrompt.system(req)) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", LlmPrompt.user(req)) } }
                }
            }
            putJsonObject("generationConfig") { put("responseMimeType", "application/json") }
        }
        val res = Http.postJson(
            "https://generativelanguage.googleapis.com/v1beta/models/${model()}:generateContent",
            body,
            mapOf("x-goog-api-key" to apiKey()!!),
        )
        val parts = res.jsonObject["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?: throw ProviderException("Gemini returned no candidates")
        val text = parts.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
        return TranslationResult(LlmPrompt.parse(text, req.texts), null, id)
    }
}
