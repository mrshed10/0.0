package app.safar.browser.translate.ocr

import android.graphics.Bitmap
import app.safar.browser.data.ContentKind
import app.safar.browser.data.OcrEngineId

/**
 * اختيار محرك OCR + تطبيق خطوة التحسين قبل/بعد الاستخراج.
 * تلقائي: ML Kit للغات التي يدعمها (أسرع)، و Tesseract للعربية/الفارسية/السيريلية...
 * مانغا بلغة "تلقائي": نفترض اليابانية (معرّفها يقرأ اللاتينية أيضاً).
 */
class OcrRouter(private val mlKit: MlKitOcr, private val tesseract: TesseractOcr) {

    suspend fun recognize(src: Bitmap, sourceLang: String, kind: ContentKind, engine: OcrEngineId): List<OcrBlock> {
        val lang = if (sourceLang == "auto" && kind == ContentKind.MANGA) "ja" else sourceLang
        val chosen: OcrEngine = when (engine) {
            OcrEngineId.MLKIT -> mlKit
            OcrEngineId.TESSERACT -> tesseract
            OcrEngineId.AUTO -> if (lang == "auto" || lang in MlKitOcr.SUPPORTED) mlKit else tesseract
        }
        val prepared = ImagePreprocessor.prepare(src)
        val raw = chosen.recognize(prepared.bitmap, lang)
        val inv = 1f / prepared.scale
        val back = raw.map { it.copy(box = it.box.scaled(inv)) }
        val gap = if (kind == ContentKind.MANGA) 22f else 12f
        return OcrPostProcessor.process(back, gap)
    }
}
