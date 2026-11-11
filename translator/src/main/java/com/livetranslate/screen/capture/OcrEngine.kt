package com.livetranslate.screen.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
import com.livetranslate.screen.core.BlockGrouper
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

    /**
     * قراءة ثانية دقيقة لكل كتلة: قصّ الفقاعة مع هامش، تكبيرها حتى يصبح ارتفاع السطر ~40px،
     * تحويلها لرمادي مع رفع التباين، ثم إعادة التعرف عليها وحدها.
     * خطوط المانغا الصغيرة والمزخرفة تُقرأ بهذا أدق بكثير من القراءة على الصفحة كاملة.
     */
    suspend fun refine(bitmap: Bitmap, blocks: List<SourceBlock>, script: OcrScript, origin: Box): List<SourceBlock> {
        if (blocks.isEmpty()) return blocks
        val bounds = Box(0, 0, bitmap.width, bitmap.height)
        return blocks.mapIndexed { index, block ->
            if (index >= MAX_REFINE) return@mapIndexed block
            val lineH = BlockGrouper.medianLineHeight(listOf(block)).coerceAtLeast(1)
            val scale = (TARGET_LINE_PX / lineH).coerceIn(1f, 3f)
            val margin = (lineH * 0.6f).toInt().coerceAtLeast(6)
            val rel = block.box.offset(-origin.left, -origin.top).inflate(margin).intersect(bounds)
                ?: return@mapIndexed block
            val crop = prepare(bitmap, rel, scale)
            try {
                val text = runCatching { process(crop, script) }.getOrNull()
                    ?.textBlocks?.flatMap { it.lines }?.map { it.text }
                    ?.let { TextTools.joinLines(it, script.isCjk) }
                if (text.isNullOrBlank() || text.length < block.text.length / 2) block else block.copy(text = text)
            } finally {
                crop.recycle()
            }
        }
    }

    private fun prepare(src: Bitmap, rel: Box, scale: Float): Bitmap {
        val pad = 16 // هامش أبيض يحسّن دقة ML Kit عند حواف الصورة
        val w = (rel.width * scale).toInt() + 2 * pad
        val h = (rel.height * scale).toInt() + 2 * pad
        val out = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val k = 1.5f
        val t = (1f - k) * 128f
        val matrix = ColorMatrix(
            floatArrayOf(
                0.299f * k, 0.587f * k, 0.114f * k, 0f, t,
                0.299f * k, 0.587f * k, 0.114f * k, 0f, t,
                0.299f * k, 0.587f * k, 0.114f * k, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(
            src,
            Rect(rel.left, rel.top, rel.right, rel.bottom),
            Rect(pad, pad, w - pad, h - pad),
            paint,
        )
        return out
    }

    @Synchronized
    fun close() {
        clients.values.forEach { it.close() }
        clients.clear()
    }

    private fun Rect.toBox() = Box(left, top, right, bottom)

    private companion object {
        const val TARGET_LINE_PX = 40f
        const val MAX_REFINE = 30
    }
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
