package com.livetranslate.overlay.translate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.util.LruCache
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * خط المعالجة: صورة الشاشة → OCR → دمج فقاعات الكلام → تنظيف النص → كشف اللغة → ترجمة على الجهاز.
 *
 * السرعة:
 * - النماذج تُحمَّل وتُسخَّن في [prepare] قبل أول إطار.
 * - كل كتلة تُترجم بالتوازي وتُعرض فور جاهزيتها عبر [process] (onPartial) دون انتظار البقية.
 * - ذاكرة مؤقتة للنصوص المترجمة: الأسطر المتكررة أثناء التمرير تظهر فوراً.
 *
 * الدقة (خطوط الكوميكس/المانجا):
 * - الكتل ضعيفة الثقة يُعاد قراءتها بعد تكبيرها ×2-3 ورفع تباينها.
 * - [TextNormalizer] يصلح الحروف الكبيرة والخلط بين 0/O و 1/I.
 */
class TranslationEngine : Closeable {

    private val recognizers = mutableMapOf<Script, TextRecognizer>()
    private val translators = mutableMapOf<String, Translator>()
    private val cache = LruCache<String, String>(1500)
    private val languageId = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.05f).build(),
    )

    /** يجهّز حزم اللغات ويسخّن نموذج الـ OCR والمترجم (يُستدعى مرة قبل بدء الجلسة). */
    suspend fun prepare(source: String, target: String) {
        ModelStore.ensure(listOfNotNull(target, source.takeIf { it != Languages.AUTO }))
        val warm = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        runCatching { recognizer(Languages.scriptOf(source)).process(InputImage.fromBitmap(warm, 0)).await() }
        warm.recycle()
        val from = if (source == Languages.AUTO) "en" else source
        if (from != target) runCatching { translator(from, target)?.translate("Hello")?.await() }
    }

    suspend fun process(
        bitmap: Bitmap,
        source: String,
        target: String,
        onPartial: (List<TextPatch>) -> Unit,
    ): List<TextPatch> = coroutineScope {
        val script = Languages.scriptOf(source)
        val latin = script == Script.LATIN
        val text = recognizer(script).process(InputImage.fromBitmap(bitmap, 0)).await()
        val blocks = BlockMerger.merge(text.textBlocks.mapNotNull { it.toOcrBlock() })
        val joiner = if (Languages.joinsWithoutSpaces(script)) "" else " "
        val refineBudget = AtomicInteger(MAX_REFINES)
        val done = mutableListOf<TextPatch>()

        blocks.map { block ->
            async {
                var lines = block.lines.map { it.text }
                if (block.confidence < REFINE_BELOW && refineBudget.getAndDecrement() > 0) {
                    refine(bitmap, block, script)?.let { lines = it }
                }
                val original = TextNormalizer.normalize(lines, joiner, latin)
                if (!ScriptFilter.isWorthTranslating(original)) return@async null
                val from = resolveSourceLanguage(original, source, script, target) ?: return@async null
                val translated = translate(original, from, target) ?: return@async null
                if (translated.equals(original, ignoreCase = true)) return@async null
                val patch = withContext(Dispatchers.Default) { buildPatch(block, translated, bitmap) }
                    ?: return@async null
                // نعمل على الخيط الرئيسي، فلا حاجة لمزامنة
                done += patch
                onPartial(done.toList())
                patch
            }
        }.awaitAll().filterNotNull()
    }

    // ------------------------------------------------------------------ OCR

    private fun Text.TextBlock.toOcrBlock(): OcrBlock? {
        val ocrLines = lines.mapNotNull { line ->
            val b = line.boundingBox ?: return@mapNotNull null
            OcrLine(line.text, b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), line.confidence)
        }
        if (ocrLines.isEmpty()) return null
        val c = cornerPoints
        if (c == null || c.size != 4) return OcrBlock(ocrLines)
        val angle = Math.toDegrees(atan2((c[1].y - c[0].y).toDouble(), (c[1].x - c[0].x).toDouble())).toFloat()
        val corners = FloatArray(8).also { arr -> c.forEachIndexed { i, p -> arr[i * 2] = p.x.toFloat(); arr[i * 2 + 1] = p.y.toFloat() } }
        return OcrBlock(ocrLines, angle, corners)
    }

    /**
     * إعادة قراءة كتلة ضعيفة الثقة: قصّ + تكبير حتى يصبح ارتفاع السطر ~56px + تحويل لتدرّج رمادي بتباين عالٍ.
     * يفيد كثيراً مع الخطوط اليدوية الصغيرة في الكوميكس والنص فوق خلفيات ملوّنة.
     */
    private suspend fun refine(bitmap: Bitmap, block: OcrBlock, script: Script): List<String>? {
        if (!block.isStraight) return null
        val pad = (block.lineHeight * 0.6f).toInt().coerceAtLeast(6)
        val rect = Rect(
            (block.left.toInt() - pad).coerceAtLeast(0),
            (block.top.toInt() - pad).coerceAtLeast(0),
            (block.right.toInt() + pad).coerceAtMost(bitmap.width),
            (block.bottom.toInt() + pad).coerceAtMost(bitmap.height),
        )
        if (rect.width() < 8 || rect.height() < 8) return null
        val scale = (56f / block.lineHeight.coerceAtLeast(1f)).coerceIn(1.5f, 3f)
        val enhanced = withContext(Dispatchers.Default) { enhance(bitmap, rect, scale) }
        return try {
            val result = recognizer(script).process(InputImage.fromBitmap(enhanced, 0)).await()
            val lines = result.textBlocks.flatMap { it.lines }.sortedBy { it.boundingBox?.top ?: 0 }
            if (lines.isEmpty()) return null
            val confidence = lines.map { it.confidence }.average().toFloat()
            if (confidence > block.confidence + 0.05f) lines.map { it.text } else null
        } catch (e: Exception) {
            null
        } finally {
            enhanced.recycle()
        }
    }

    private fun enhance(source: Bitmap, rect: Rect, scale: Float): Bitmap {
        val w = (rect.width() * scale).toInt()
        val h = (rect.height() * scale).toInt()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val contrast = 1.6f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val gray = ColorMatrix().apply { setSaturation(0f) }
        gray.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(gray) }
        Canvas(out).drawBitmap(source, rect, Rect(0, 0, w, h), paint)
        return out
    }

    private fun recognizer(script: Script): TextRecognizer = recognizers.getOrPut(script) {
        when (script) {
            Script.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            Script.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            Script.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            Script.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            Script.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        }
    }

    // ------------------------------------------------------------------ اللغة والترجمة

    /**
     * يحدد لغة الكتلة، ويُرجع null إن لم تكن تحتاج ترجمة.
     * في الوضع التلقائي لا تُقبل إلا لغة حزمتها منزّلة مسبقاً؛ وإلا يُعامل النص اللاتيني كإنجليزي.
     * هذا يمنع كشف النصوص القصيرة/الكبيرة خطأً كـ hr أو vi ومحاولة تنزيل حزمها.
     */
    private suspend fun resolveSourceLanguage(text: String, source: String, script: Script, target: String): String? {
        val targetTag = ModelStore.tagOf(target) ?: return null
        if (source != Languages.AUTO) {
            if (script != Script.LATIN && !ScriptFilter.containsScript(text, script)) return null
            if (source == target || !ModelStore.isReady(source)) return null
            if (script == Script.LATIN) {
                val top = candidates(text).firstOrNull()
                if (top != null && top.first == targetTag && top.second >= 0.6f) return null
            }
            return source
        }
        val candidates = candidates(text)
        val top = candidates.firstOrNull()
        if (top != null && top.first == targetTag && top.second >= 0.5f) return null
        candidates.firstOrNull { (tag, conf) -> tag != targetTag && conf >= 0.3f && ModelStore.isReady(tag) }
            ?.let { return it.first }
        return if (script == Script.LATIN && targetTag != "en" && ScriptFilter.containsScript(text, Script.LATIN)) "en" else null
    }

    private suspend fun candidates(text: String): List<Pair<String, Float>> {
        val list = runCatching { languageId.identifyPossibleLanguages(text).await() }.getOrNull() ?: return emptyList()
        return list.mapNotNull { lang ->
            val tag = lang.languageTag
            if (tag == "und" || tag.contains("-Latn")) null else tag.substringBefore('-') to lang.confidence
        }.sortedByDescending { it.second }
    }

    private suspend fun translate(text: String, from: String, to: String): String? {
        val key = "$from>$to|$text"
        cache.get(key)?.let { return it }
        val translator = translator(from, to) ?: return null
        val result = runCatching { translator.translate(text).await() }.getOrNull() ?: return null
        cache.put(key, result)
        return result
    }

    /** يُرجع مترجماً فقط إن كانت الحزمتان منزّلتين؛ لا تنزيل هنا إطلاقاً. */
    private fun translator(from: String, to: String): Translator? {
        val src = ModelStore.tagOf(from) ?: return null
        val tgt = ModelStore.tagOf(to) ?: return null
        if (!ModelStore.isReady(src) || !ModelStore.isReady(tgt)) return null
        return translators.getOrPut("$src>$tgt") {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build())
        }
    }

    // ------------------------------------------------------------------ الرسم

    /** يحوّل الكتلة إلى صندوق بزاوية النص الأصلي وألوان مأخوذة من الصورة. */
    private fun buildPatch(block: OcrBlock, translated: String, bitmap: Bitmap): TextPatch? {
        val box = Rect(block.left.toInt(), block.top.toInt(), block.right.toInt(), block.bottom.toInt())
        val c = block.corners
        val straight = block.isStraight || c == null
        val cx: Float
        val cy: Float
        val w: Float
        val h: Float
        if (straight) {
            cx = box.exactCenterX(); cy = box.exactCenterY()
            w = box.width().toFloat(); h = box.height().toFloat()
        } else {
            cx = (c!![0] + c[2] + c[4] + c[6]) / 4f
            cy = (c[1] + c[3] + c[5] + c[7]) / 4f
            w = hypot(c[2] - c[0], c[3] - c[1])
            h = hypot(c[6] - c[0], c[7] - c[1])
        }
        if (w < 4f || h < 4f) return null
        val bg = ColorSampler.background(bitmap, box)
        val fg = ColorSampler.foreground(bitmap, box, bg)
        return TextPatch(
            translated = translated,
            centerX = cx, centerY = cy, width = w, height = h,
            angle = if (straight) 0f else block.angle,
            lineCount = block.lines.size.coerceAtLeast(1),
            background = bg,
            textColor = fg,
        )
    }

    override fun close() {
        recognizers.values.forEach { it.close() }
        translators.values.forEach { it.close() }
        languageId.close()
        recognizers.clear()
        translators.clear()
    }

    private companion object {
        const val REFINE_BELOW = 0.7f
        const val MAX_REFINES = 6
    }
}

/** تقدير لون خلفية النص ولون الحروف من الصورة الملتقطة. */
internal object ColorSampler {

    /** متوسط ألوان حلقة رفيعة حول صندوق النص مباشرة. */
    fun background(bitmap: Bitmap, box: Rect): Int {
        val pad = 3
        val l = (box.left - pad).coerceIn(0, bitmap.width - 1)
        val r = (box.right + pad).coerceIn(0, bitmap.width - 1)
        val t = (box.top - pad).coerceIn(0, bitmap.height - 1)
        val b = (box.bottom + pad).coerceIn(0, bitmap.height - 1)
        var rs = 0L; var gs = 0L; var bs = 0L; var n = 0
        fun add(x: Int, y: Int) {
            val c = bitmap.getPixel(x, y)
            rs += Color.red(c); gs += Color.green(c); bs += Color.blue(c); n++
        }
        val steps = 24
        for (i in 0..steps) {
            val x = l + (r - l) * i / steps
            val y = t + (b - t) * i / steps
            add(x, t); add(x, b); add(l, y); add(r, y)
        }
        return Color.rgb((rs / n).toInt(), (gs / n).toInt(), (bs / n).toInt())
    }

    /** البكسل الأبعد لوناً عن الخلفية داخل الصندوق (غالباً لون الحروف)، مع ضمان تباين كافٍ. */
    fun foreground(bitmap: Bitmap, box: Rect, bg: Int): Int {
        var best = if (luminance(bg) > 0.5f) Color.BLACK else Color.WHITE
        var bestDist = 0
        val cols = 16
        val rows = 6
        for (j in 1 until rows) for (i in 1 until cols) {
            val x = (box.left + box.width() * i / cols).coerceIn(0, bitmap.width - 1)
            val y = (box.top + box.height() * j / rows).coerceIn(0, bitmap.height - 1)
            val c = bitmap.getPixel(x, y)
            val d = distance(c, bg)
            if (d > bestDist) { bestDist = d; best = c }
        }
        val contrast = kotlin.math.abs(luminance(best) - luminance(bg))
        return if (contrast < 0.35f) (if (luminance(bg) > 0.5f) Color.BLACK else Color.WHITE) else best
    }

    private fun distance(a: Int, b: Int): Int {
        val dr = Color.red(a) - Color.red(b)
        val dg = Color.green(a) - Color.green(b)
        val db = Color.blue(a) - Color.blue(b)
        return dr * dr + dg * dg + db * db
    }

    private fun luminance(c: Int) = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
}
