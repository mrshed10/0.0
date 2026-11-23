package app.safar.browser.translate.ocr

import android.graphics.Bitmap
import app.safar.browser.data.ContentKind
import app.safar.browser.data.OcrEngineId
import app.safar.browser.translate.ScriptDetector
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class OcrResult(val blocks: List<OcrBlock>, /** لغة مخمّنة من النص المستخرج (أو null) */ val lang: String?)

/**
 * اختيار محرك OCR + خطوة التحسين قبل/بعد الاستخراج.
 *  - لغة محددة: ML Kit إن كان يدعمها، وإلا Tesseract (العربية/الفارسية/الروسية...).
 *  - مانغا بلغة "تلقائي": نشغّل المعرّفين الياباني والكوري معاً على اللقطة الأولى ونختار الأنسب
 *    (كلاهما يقرأ اللاتينية أيضاً ⇒ يغطي الإنجليزية)، ثم نثبّت الاختيار لهذا الموقع لتوفير الوقت.
 */
class OcrRouter(private val mlKit: MlKitOcr, private val tesseract: TesseractOcr) {
    @Volatile private var stickyScript: String? = null
    @Volatile private var stickyHits = 0

    /** يُستدعى عند الانتقال لموقع آخر. */
    fun resetDetection() { stickyScript = null; stickyHits = 0 }

    suspend fun recognize(src: Bitmap, sourceLang: String, kind: ContentKind, engine: OcrEngineId): OcrResult {
        val prepared = ImagePreprocessor.prepare(src)
        val raw: List<OcrBlock> = when {
            engine == OcrEngineId.TESSERACT -> tesseract.recognize(prepared.bitmap, sourceLang)
            sourceLang != "auto" && engine == OcrEngineId.AUTO && sourceLang !in MlKitOcr.SUPPORTED ->
                tesseract.recognize(prepared.bitmap, sourceLang)
            sourceLang != "auto" -> mlKit.recognize(prepared.bitmap, sourceLang)
            else -> autoDetect(prepared.bitmap, kind)
        }
        val inv = 1f / prepared.scale
        val gap = if (kind == ContentKind.MANGA) 22f else 12f
        val blocks = OcrPostProcessor.process(raw.map { it.copy(box = it.box.scaled(inv)) }, gap)
        val lang = if (sourceLang != "auto") sourceLang else ScriptDetector.guess(blocks.joinToString(" ") { it.text })
        return OcrResult(blocks, lang)
    }

    private suspend fun autoDetect(bmp: Bitmap, kind: ContentKind): List<OcrBlock> {
        stickyScript?.let { s ->
            val r = mlKit.recognize(bmp, s)
            // إن لم يعد يجد شيئاً مراراً (فصل بلغة أخرى؟) نعيد الاكتشاف
            if (r.isNotEmpty()) { stickyHits = 0; return r }
            if (++stickyHits < 3) return r
            resetDetection()
        }
        val candidates = if (kind == ContentKind.MANGA) listOf("ja", "ko") else listOf("ja", "ko", "zh")
        val results = coroutineScope {
            candidates.map { c -> async { c to runCatching { mlKit.recognize(bmp, c) }.getOrDefault(emptyList()) } }
                .map { it.await() }
        }
        val best = results.maxByOrNull { (script, blocks) -> ScriptDetector.nativeScore(blocks.joinToString(" ") { it.text }, script) }
            ?: return emptyList()
        if (best.second.isNotEmpty()) stickyScript = best.first
        return best.second
    }
}
