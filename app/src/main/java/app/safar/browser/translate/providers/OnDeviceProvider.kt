package app.safar.browser.translate.providers

import app.safar.browser.data.ProviderId
import app.safar.browser.translate.ProviderException
import app.safar.browser.translate.TranslationProvider
import app.safar.browser.translate.TranslationRequest
import app.safar.browser.translate.TranslationResult
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/**
 * ترجمة على الجهاز (ML Kit): مجانية، خاصة تماماً، تعمل دون إنترنت بعد تنزيل نموذج اللغة (~30MB).
 * هي الافتراضي عند عدم وجود مفاتيح، والملاذ الأخير في سلسلة البدائل.
 */
class OnDeviceProvider(
    /** يُستدعى بـ true قبل تنزيل نموذج لغة (قد يستغرق دقيقة في أول مرة) و false بعده. */
    private val onModelDownload: (Boolean) -> Unit = {},
) : TranslationProvider {
    override val id = ProviderId.ON_DEVICE
    override fun isConfigured() = true

    private val translators = mutableMapOf<String, Translator>()
    private val langId = LanguageIdentification.getClient()

    override fun supports(source: String, target: String) =
        TranslateLanguage.fromLanguageTag(target) != null &&
            (source == "auto" || TranslateLanguage.fromLanguageTag(source) != null)

    override suspend fun translate(req: TranslationRequest): TranslationResult {
        val src = if (req.source == "auto") detect(req.texts) else req.source
        val srcLang = TranslateLanguage.fromLanguageTag(src) ?: throw ProviderException("ML Kit: unsupported source $src")
        val tgtLang = TranslateLanguage.fromLanguageTag(req.target) ?: throw ProviderException("ML Kit: unsupported target")
        if (srcLang == tgtLang) return TranslationResult(req.texts, src, id)

        val translator = synchronized(translators) {
            translators.getOrPut("$srcLang>$tgtLang") {
                Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(srcLang).setTargetLanguage(tgtLang).build())
            }
        }
        onModelDownload(true)
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        } finally {
            onModelDownload(false)
        }
        val out = req.texts.map { translator.translate(it).await() }
        return TranslationResult(out, src, id)
    }

    private suspend fun detect(texts: List<String>): String {
        val sample = texts.joinToString(" ").take(1000)
        val tag = runCatching { langId.identifyLanguage(sample).await() }.getOrDefault("und")
        if (tag != "und") return tag.substringBefore('-')
        // نص قصير أو مخرجات OCR: نخمّن من نظام الكتابة بدلاً من الفشل
        return app.safar.browser.translate.ScriptDetector.guess(sample)
            ?: throw ProviderException("ML Kit: could not detect language")
    }
}
