package app.safar.browser.translate.ocr

import android.graphics.Bitmap
import app.safar.browser.data.ContentKind
import app.safar.browser.data.OcrEngineId
import app.safar.browser.translate.ScriptDetector

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
        // دمج محافظ: أسطر الفقاعة الواحدة فقط. تجميع الفقاعة الكاملة يتم لاحقاً بحدودها الفعلية (BubbleAnalyzer)
        val gap = if (kind == ContentKind.MANGA) 6f else 10f
        val blocks = OcrPostProcessor.process(raw.map { it.copy(box = it.box.scaled(inv)) }, gap)
        val lang = if (sourceLang != "auto") sourceLang else ScriptDetector.guess(blocks.joinToString(" ") { it.text })
        return OcrResult(blocks, lang)
    }

    /**
     * اكتشاف نظام الكتابة تلقائياً:
     *  1) المعرّف الياباني (يقرأ اللاتينية أيضاً): إن غلبت الكانا/الكانجي ⇒ ياباني/صيني.
     *  2) وإلا الكوري: إن غلب الهانغول ⇒ كوري.
     *  3) وإلا لاتيني (إنجليزي...) بالمعرّف اللاتيني الأدق، فلا تُقرأ خطوط الرسم كحروف يابانية.
     * يُثبَّت الاختيار للموقع الحالي لتسريع اللقطات التالية.
     */
    private suspend fun autoDetect(bmp: Bitmap, kind: ContentKind): List<OcrBlock> {
        stickyScript?.let { s ->
            val r = mlKit.recognize(bmp, s)
            if (r.isNotEmpty()) { stickyHits = 0; return r }
            if (++stickyHits < 3) return r
            resetDetection()
        }
        val ja = runCatching { mlKit.recognize(bmp, "ja") }.getOrDefault(emptyList())
        val jaGuess = ScriptDetector.guess(ja.joinToString(" ") { it.text })
        if (jaGuess == "ja" || jaGuess == "zh") { stickyScript = jaGuess; return if (jaGuess == "ja") ja else mlKit.recognize(bmp, "zh") }
        val ko = runCatching { mlKit.recognize(bmp, "ko") }.getOrDefault(emptyList())
        if (ScriptDetector.guess(ko.joinToString(" ") { it.text }) == "ko") { stickyScript = "ko"; return ko }
        val latin = runCatching { mlKit.recognize(bmp, "latin") }.getOrDefault(emptyList())
        if (latin.isNotEmpty() || ja.isNotEmpty()) stickyScript = "latin"
        return latin
    }
}
