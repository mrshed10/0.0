package app.safar.browser.translate

import app.safar.browser.data.ContentKind
import app.safar.browser.data.ProviderId

data class TranslationRequest(
    val texts: List<String>,
    /** "auto" أو رمز لغة ISO-639-1 */
    val source: String,
    val target: String,
    val kind: ContentKind,
    /** سياق إضافي: عنوان الصفحة، أو المقاطع السابقة في الحوار/الفيديو. */
    val context: String? = null,
    val instructions: String = "",
)

data class TranslationResult(
    val texts: List<String>,
    val detectedSource: String?,
    val provider: ProviderId,
)

class ProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface TranslationProvider {
    val id: ProviderId
    /** هل المزوّد جاهز (مفتاح API موجود، أو على الجهاز). */
    fun isConfigured(): Boolean
    fun supports(source: String, target: String): Boolean = true
    suspend fun translate(req: TranslationRequest): TranslationResult
}
