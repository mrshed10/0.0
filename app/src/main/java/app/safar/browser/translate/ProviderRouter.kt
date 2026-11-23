package app.safar.browser.translate

import app.safar.browser.data.ContentKind
import app.safar.browser.data.ProviderId

/**
 * الاختيار الذكي لمصدر الترجمة حسب نوع المحتوى وطبيعة النص، مع سلسلة بدائل تلقائية
 * عند غياب المفتاح أو فشل الطلب أو رفض النموذج.
 *
 *  - نصوص قصيرة/قوائم/أزرار  -> Google (أسرع وأرخص) ثم على الجهاز
 *  - فقرات طويلة بلغة يدعمها DeepL -> DeepL (صياغة أدق)
 *  - حوارات/عامية (نسبة اقتباسات عالية) -> نموذج ذكاء اصطناعي (Claude/Gemini)
 *  - مانغا/فيديو -> نموذج ذكاء اصطناعي (يفهم السياق والأسلوب والمؤثرات الصوتية)
 *  - دائماً: ML Kit على الجهاز كملاذ أخير (يعمل بلا إنترنت وبلا مفاتيح)
 */
class ProviderRouter(private val providers: Map<ProviderId, TranslationProvider>) {

    fun plan(req: TranslationRequest, preferred: ProviderId): List<ProviderId> {
        val order = if (preferred != ProviderId.AUTO) {
            listOf(preferred) + autoOrder(req).filterNot { it == preferred }
        } else {
            autoOrder(req)
        }
        return order.distinct().filter { id ->
            val p = providers[id]
            p != null && p.isConfigured() && p.supports(req.source, req.target)
        }
    }

    private fun autoOrder(req: TranslationRequest): List<ProviderId> {
        val llm = listOf(ProviderId.CLAUDE, ProviderId.GEMINI)
        return when (req.kind) {
            ContentKind.MANGA, ContentKind.VIDEO ->
                llm + listOf(ProviderId.DEEPL, ProviderId.GOOGLE, ProviderId.ON_DEVICE)
            ContentKind.TEXT -> when {
                // الحوار أولاً: أسطر الحوار قصيرة غالباً ولا يجب معاملتها كعناصر واجهة
                dialogueScore(req.texts) >= 0.4 ->
                    llm + listOf(ProviderId.DEEPL, ProviderId.GOOGLE, ProviderId.ON_DEVICE)
                isShortUiText(req.texts) ->
                    listOf(ProviderId.GOOGLE, ProviderId.ON_DEVICE, ProviderId.DEEPL) + llm
                else ->
                    listOf(ProviderId.DEEPL, ProviderId.GOOGLE) + llm + ProviderId.ON_DEVICE
            }
        }
    }

    suspend fun translate(req: TranslationRequest, preferred: ProviderId): TranslationResult {
        val plan = plan(req, preferred)
        if (plan.isEmpty()) throw ProviderException("لا يوجد مزود ترجمة مهيأ لهذه اللغات")
        var last: Throwable? = null
        for (id in plan) {
            try {
                val r = providers.getValue(id).translate(req)
                if (r.texts.size == req.texts.size) return r
                last = ProviderException("$id returned ${r.texts.size}/${req.texts.size} segments")
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                last = t
            }
        }
        throw ProviderException("فشلت كل المزودات: ${last?.message}", last)
    }

    companion object {
        fun isShortUiText(texts: List<String>): Boolean {
            if (texts.isEmpty()) return true
            val avg = texts.sumOf { it.length }.toDouble() / texts.size
            return avg < 40
        }

        private val dialogueMarks = Regex("""^[\s]*[«“"「『—–\-]|[」』”»"]\s*$|[!?！？]{2,}|…""")

        /** نسبة المقاطع التي تبدو حواراً (اقتباسات، شرطات حوار، تعجب متكرر). */
        fun dialogueScore(texts: List<String>): Double {
            if (texts.isEmpty()) return 0.0
            return texts.count { dialogueMarks.containsMatchIn(it) }.toDouble() / texts.size
        }
    }
}
