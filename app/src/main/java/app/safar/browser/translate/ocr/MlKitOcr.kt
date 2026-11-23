package app.safar.browser.translate.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** ML Kit Text Recognition v2: سريع على الجهاز. يدعم اللاتينية واليابانية والصينية والكورية والديفاناغارية. */
class MlKitOcr : OcrEngine {
    private val recognizers = mutableMapOf<String, TextRecognizer>()

    private fun recognizer(script: String): TextRecognizer = synchronized(recognizers) {
        recognizers.getOrPut(script) {
            when (script) {
                "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                "zh" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                "ko" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                "hi" -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
                else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
        }
    }

    override suspend fun recognize(bitmap: Bitmap, langHint: String): List<OcrBlock> {
        val result = recognizer(scriptFor(langHint)).process(InputImage.fromBitmap(bitmap, 0)).await()
        return result.textBlocks.mapNotNull { b ->
            val r = b.boundingBox ?: return@mapNotNull null
            OcrBlock(
                text = b.text,
                box = Box(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()),
                lines = b.lines.map { it.text },
            )
        }
    }

    companion object {
        /** المعرّفات اليابانية/الصينية/الكورية تتعرف على اللاتينية أيضاً. */
        fun scriptFor(lang: String) = when (lang) {
            "ja" -> "ja"; "zh" -> "zh"; "ko" -> "ko"
            "hi", "mr", "ne" -> "hi"
            else -> "latin"
        }
        val SUPPORTED = setOf("ja", "zh", "ko", "hi", "mr", "ne", "en", "fr", "de", "es", "it", "pt", "id", "vi", "tr", "nl", "pl")
    }
}
