package com.livetranslate.screen.capture

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.livetranslate.screen.core.Box
import com.livetranslate.screen.core.OcrScript
import com.livetranslate.screen.core.ScriptDetector
import com.livetranslate.screen.core.SourceBlock
import com.livetranslate.screen.core.TextTools
import kotlinx.coroutines.tasks.await
import java.util.EnumMap

/** التعرف الضوئي على الحروف على الجهاز (ML Kit Text Recognition v2). */
class OcrEngine {

    private val clients = EnumMap<OcrScript, TextRecognizer>(OcrScript::class.java)

    @Synchronized
    private fun client(script: OcrScript): TextRecognizer = clients.getOrPut(script) {
        when (script) {
            OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrScript.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            OcrScript.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            OcrScript.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            OcrScript.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        }
    }

    /**
     * @param origin موضع الصورة على الشاشة؛ تُعاد الكتل بإحداثيات الشاشة.
     * @param sampleBackground أخذ لون الخلفية حول كل كتلة (وضع المانغا).
     */
    suspend fun recognize(bitmap: Bitmap, script: OcrScript, origin: Box, sampleBackground: Boolean): List<SourceBlock> =
        toBlocks(process(bitmap, script), bitmap, script, origin, sampleBackground)

    private suspend fun process(bitmap: Bitmap, script: OcrScript): Text =
        client(script).process(InputImage.fromBitmap(bitmap, 0)).await()

    /**
     * اكتشاف نظام الكتابة تلقائياً ثم إرجاع الكتل بالمحرك الصحيح.
     * @param detectLanguage أداة اكتشاف اللغة (للتحقق من أن القراءة اللاتينية نص حقيقي).
     * @return المحرك المكتشف والكتل، أو null إذا لم يُعثر على نص.
     */
    suspend fun recognizeAuto(
        bitmap: Bitmap,
        origin: Box,
        sampleBackground: Boolean,
        detectLanguage: suspend (String) -> String?,
    ): Pair<OcrScript, List<SourceBlock>>? {
        fun done(s: OcrScript, t: Text) = s to toBlocks(t, bitmap, s, origin, sampleBackground)

        val jp = process(bitmap, OcrScript.JAPANESE)
        when (ScriptDetector.fromJapanesePass(jp.text)) {
            OcrScript.JAPANESE -> return done(OcrScript.JAPANESE, jp)
            OcrScript.CHINESE -> return done(OcrScript.CHINESE, process(bitmap, OcrScript.CHINESE))
            else -> Unit
        }
        val latin = process(bitmap, OcrScript.LATIN)
        val lang = if (latin.text.isBlank()) null else detectLanguage(latin.text.take(400))
        if (ScriptDetector.isLatinConfident(latin.text, lang)) return done(OcrScript.LATIN, latin)
        val ko = process(bitmap, OcrScript.KOREAN)
        if (ScriptDetector.hasHangul(ko.text)) return done(OcrScript.KOREAN, ko)
        val dev = process(bitmap, OcrScript.DEVANAGARI)
        if (ScriptDetector.hasDevanagari(dev.text)) return done(OcrScript.DEVANAGARI, dev)
        return if (latin.text.isNotBlank()) done(OcrScript.LATIN, latin) else null
    }

    private fun toBlocks(result: Text, bitmap: Bitmap, script: OcrScript, origin: Box, sampleBackground: Boolean): List<SourceBlock> {
        return result.textBlocks.mapNotNull { block ->
            val r = block.boundingBox ?: return@mapNotNull null
            val text = TextTools.joinLines(block.lines.map { it.text }, script.isCjk)
            if (text.isBlank()) return@mapNotNull null
            SourceBlock(
                text = text,
                box = r.toBox().offset(origin.left, origin.top),
                lines = block.lines.mapNotNull { it.boundingBox?.toBox()?.offset(origin.left, origin.top) },
                backgroundColor = if (sampleBackground) ColorSampler.sampleAround(bitmap, r) else null,
            )
        }
    }

    @Synchronized
    fun close() {
        clients.values.forEach { it.close() }
        clients.clear()
    }

    private fun Rect.toBox() = Box(left, top, right, bottom)
}

/** يأخذ اللون الوسيط للبكسلات المحيطة بالنص (خلفية الفقاعة). */
object ColorSampler {
    fun sampleAround(bmp: Bitmap, r: Rect, margin: Int = 4): Int {
        val l = (r.left - margin).coerceIn(0, bmp.width - 1)
        val t = (r.top - margin).coerceIn(0, bmp.height - 1)
        val rr = (r.right + margin).coerceIn(0, bmp.width - 1)
        val b = (r.bottom + margin).coerceIn(0, bmp.height - 1)
        val reds = ArrayList<Int>(64); val greens = ArrayList<Int>(64); val blues = ArrayList<Int>(64)
        fun add(x: Int, y: Int) {
            val c = bmp.getPixel(x, y)
            reds += Color.red(c); greens += Color.green(c); blues += Color.blue(c)
        }
        val stepX = ((rr - l) / 12).coerceAtLeast(1)
        val stepY = ((b - t) / 12).coerceAtLeast(1)
        var x = l
        while (x <= rr) { add(x, t); add(x, b); x += stepX }
        var y = t
        while (y <= b) { add(l, y); add(rr, y); y += stepY }
        fun median(v: ArrayList<Int>) = v.sorted()[v.size / 2]
        return Color.rgb(median(reds), median(greens), median(blues))
    }
}
