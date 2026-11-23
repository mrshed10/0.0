package app.safar.browser.translate.providers

import app.safar.browser.data.ProviderId
import app.safar.browser.translate.LlmPrompt
import app.safar.browser.translate.ProviderException
import app.safar.browser.translate.TranslationProvider
import app.safar.browser.translate.TranslationRequest
import app.safar.browser.translate.TranslationResult
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Claude عبر Anthropic Java SDK: ترجمة تراعي السياق والحوارات والعامية والمانغا.
 * effort=LOW لتقليل زمن الاستجابة (الترجمة لا تحتاج تفكيراً عميقاً)، ويمكن تغيير النموذج من الإعدادات.
 */
class ClaudeProvider(
    private val apiKey: () -> String?,
    private val model: () -> String,
) : TranslationProvider {
    override val id = ProviderId.CLAUDE
    override fun isConfigured() = !apiKey().isNullOrBlank()

    @Volatile private var client: AnthropicClient? = null
    @Volatile private var clientKey: String? = null

    private fun client(): AnthropicClient = synchronized(this) {
        val key = apiKey() ?: throw ProviderException("Claude API key missing")
        if (client == null || clientKey != key) {
            client = AnthropicOkHttpClient.builder().apiKey(key).build()
            clientKey = key
        }
        client!!
    }

    override suspend fun translate(req: TranslationRequest): TranslationResult = withContext(Dispatchers.IO) {
        val params = MessageCreateParams.builder()
            .model(model())
            .maxTokens(16000L)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .system(LlmPrompt.system(req))
            .addUserMessage(LlmPrompt.user(req))
            .build()

        val response = client().messages().create(params)

        // رفض من مصنفات الأمان: نرمي استثناءً ليجرّب الموجّه المزوّد التالي في السلسلة
        if (response.stopReason().map { it == StopReason.REFUSAL }.orElse(false)) {
            throw ProviderException("Claude refused this request")
        }
        val text = response.content()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("")
        TranslationResult(LlmPrompt.parse(text, req.texts), null, id)
    }
}
