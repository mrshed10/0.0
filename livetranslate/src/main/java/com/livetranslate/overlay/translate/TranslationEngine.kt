package com.livetranslate.overlay.translate

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.util.LruCache
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
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
import com.livetranslate.overlay.overlay.EngineStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * خط المعالجة: صورة الشاشة → OCR (ML Kit) → كشف اللغة → ترجمة على الجهاز → [TextPatch] جاهزة للرسم.
 * كل شيء يعمل محلياً؛ الإنترنت مطلوب فقط لتنزيل حزمة اللغة أول مرة.
 */
class TranslationEngine : Closeable {

    private val recognizers = mutableMapOf<Script, TextRecognizer>()
    private val translators = mutableMapOf<String, Translator>()
    private val readyModels = mutableSetOf<String>()
    private val modelMutex = Mutex()
    private val cache = LruCache<String, String>(600)
    private val languageId = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.45f).build(),
    )

    suspend fun process(
        bitmap: Bitmap,
        source: String,
        target: String,
        excluded: List<Rect>,
        onStatus: (EngineStatus) -> Unit,
    ): List<TextPatch> = coroutineScope {
        onStatus(EngineStatus.Working)
        val script = Languages.scriptOf(source)
        val text = recognizer(script).process(InputImage.fromBitmap(bitmap, 0)).await()
        val separator = if (Languages.joinsWithoutSpaces(script)) "" else " "

        text.textBlocks
            .filter { block ->
                val box = block.boundingBox ?: return@filter false
                excluded.none { Rect.intersects(it, box) }
            }
            .map { block ->
                async {
                    val original = block.lines.joinToString(separator) { it.text }.trim()
                    if (!ScriptFilter.isWorthTranslating(original)) return@async null
                    val from = resolveSourceLanguage(original, source, script, target) ?: return@async null
                    val translated = translate(original, from, target, onStatus) ?: return@async null
                    if (translated.equals(original, ignoreCase = true)) return@async null
                    withContext(Dispatchers.Default) { buildPatch(block, translated, bitmap) }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    /** يحدد لغة الكتلة فعلياً، ويُرجع null إذا كانت لا تحتاج ترجمة. */
    private suspend fun resolveSourceLanguage(text: String, source: String, script: Script, target: String): String? {
        if (source != Languages.AUTO) {
            // في اللغات ذات الكتابة الخاصة نتجاهل الكتل التي لا تحتوي حروف تلك الكتابة (أسماء لاتينية مثلاً)
            if (script != Script.LATIN && !ScriptFilter.containsScript(text, script)) return null
            if (script == Script.LATIN) {
                val detected = identify(text)
                if (detected != null && detected == target) return null
            }
            return source.takeIf { it != target }
        }
        val detected = identify(text) ?: return null
        if (detected == target) return null
        return detected.takeIf { TranslateLanguage.fromLanguageTag(it) != null }
    }

    private suspend fun identify(text: String): String? {
        val tag = runCatching { languageId.identifyLanguage(text).await() }.getOrNull() ?: return null
        if (tag == "und" || tag.contains("-Latn")) return null
        return tag.substringBefore('-')
    }

    private suspend fun translate(text: String, from: String, to: String, onStatus: (EngineStatus) -> Unit): String? {
        val key = "$from>$to|$text"
        cache.get(key)?.let { return it }
        val translator = ensureTranslator(from, to, onStatus) ?: return null
        val result = runCatching { translator.translate(text).await() }.getOrNull() ?: return null
        cache.put(key, result)
        return result
    }

    private suspend fun ensureTranslator(from: String, to: String, onStatus: (EngineStatus) -> Unit): Translator? =
        modelMutex.withLock {
            val src = TranslateLanguage.fromLanguageTag(from) ?: return@withLock null
            val tgt = TranslateLanguage.fromLanguageTag(to) ?: return@withLock null
            val pair = "$src>$tgt"
            val translator = translators.getOrPut(pair) {
                Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build())
            }
            if (pair !in readyModels) {
                onStatus(EngineStatus.Downloading(if (from == "en") to else from))
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                readyModels += pair
                onStatus(EngineStatus.Working)
            }
            translator
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

    /** يحوّل الكتلة إلى صندوق مدوَّر بزاوية النص الأصلي وألوان مأخوذة من الصورة. */
    private fun buildPatch(block: Text.TextBlock, translated: String, bitmap: Bitmap): TextPatch? {
        val box = block.boundingBox ?: return null
        val corners: Array<Point>? = block.cornerPoints
        val (cx, cy, w, h, angle) = if (corners != null && corners.size == 4) {
            val (p0, p1, p2, p3) = corners
            Geometry(
                (p0.x + p1.x + p2.x + p3.x) / 4f,
                (p0.y + p1.y + p2.y + p3.y) / 4f,
                hypot((p1.x - p0.x).toFloat(), (p1.y - p0.y).toFloat()),
                hypot((p3.x - p0.x).toFloat(), (p3.y - p0.y).toFloat()),
                Math.toDegrees(atan2((p1.y - p0.y).toDouble(), (p1.x - p0.x).toDouble())).toFloat(),
            )
        } else {
            Geometry(box.exactCenterX(), box.exactCenterY(), box.width().toFloat(), box.height().toFloat(), 0f)
        }
        if (w < 4f || h < 4f) return null
        val bg = ColorSampler.background(bitmap, box)
        val fg = ColorSampler.foreground(bitmap, box, bg)
        return TextPatch(
            translated = translated,
            centerX = cx, centerY = cy, width = w, height = h,
            angle = if (kotlin.math.abs(angle) < 2f) 0f else angle,
            lineCount = block.lines.size.coerceAtLeast(1),
            background = bg,
            textColor = fg,
        )
    }

    private data class Geometry(val cx: Float, val cy: Float, val w: Float, val h: Float, val angle: Float)

    override fun close() {
        recognizers.values.forEach { it.close() }
        translators.values.forEach { it.close() }
        languageId.close()
        recognizers.clear()
        translators.clear()
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
