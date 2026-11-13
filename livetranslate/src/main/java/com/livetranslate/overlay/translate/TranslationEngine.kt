package com.livetranslate.overlay.translate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * خط المعالجة:
 *   صورة الشاشة → عدة قراءات OCR متوازية (+ قراءة مُدارة للنص العمودي) → دمج أفضل النتائج
 *   → تنظيف النص → تحديد اللغة من نظام الكتابة/كشف اللغة → ترجمة على الجهاز → [TextPatch].
 *
 * الكشف التلقائي الشامل: ML Kit لا يملك نموذجاً واحداً لكل اللغات، فيعمل في الوضع التلقائي
 * اللاتيني + الياباني (كانا وكانجي) + الكوري دائماً بالتوازي، ويُضاف الصيني والهندي حين تظهر
 * حروفهما أو في جولة "استكشاف" دورية. [OcrFusion] يختار أفضل قراءة لكل منطقة.
 *
 * النص العمودي: تُقرأ لقطة مُدارة 90° فتصبح الأعمدة أسطراً أفقية عادية، ثم تُعاد الإحداثيات
 * للشاشة وتُرسم الترجمة أفقياً في صندوق موسّع.
 */
class TranslationEngine : Closeable {

    private val recognizers = mutableMapOf<Script, TextRecognizer>()
    private val executors = mutableMapOf<Script, ExecutorService>()
    private val translators = mutableMapOf<String, Translator>()
    private val cache = LruCache<String, String>(1500)
    private val languageId = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.05f).build(),
    )

    /** آخر وقت ظهرت فيه حروف كل نظام كتابة (لتشغيل نماذجه في الوضع التلقائي). */
    private val scriptSeenAt = mutableMapOf<Script, Long>()
    private var passCount = 0

    /** يجهّز حزم اللغات ويسخّن نماذج الـ OCR والمترجم (يُستدعى مرة قبل بدء الجلسة). */
    suspend fun prepare(source: String, target: String) {
        val failed = ModelStore.ensure(Languages.modelsFor(source, target))
        if (target in failed || (source != Languages.AUTO && source in failed)) {
            error("language pack download failed")
        }
        val warm = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        coroutineScope {
            plan(source, discovery = true).scripts.map { s ->
                async { runCatching { recognizer(s).process(InputImage.fromBitmap(warm, 0)).await() } }
            }.awaitAll()
        }
        warm.recycle()
        passCount = 0
        val from = if (source == Languages.AUTO) "en" else source
        if (from != target) runCatching { translator(from, target)?.translate("Hello")?.await() }
    }

    suspend fun process(
        bitmap: Bitmap,
        source: String,
        target: String,
        onPartial: (List<TextPatch>) -> Unit,
    ): List<TextPatch> = coroutineScope {
        val plan = plan(source, discovery = source == Languages.AUTO && passCount++ % DISCOVERY_EVERY == 0)

        val horizontal = plan.scripts.map { s -> async { safeRecognize { recognize(s, bitmap) } } }
        val vertical = plan.verticalScript?.let { s -> async { safeRecognize { recognizeRotated(s, bitmap) } } }
        val candidates = horizontal.awaitAll().flatten() + (vertical?.await() ?: emptyList())
        val blocks = if (plan.scripts.size == 1 && vertical == null) candidates else OcrFusion.fuse(candidates)
        noteScripts(blocks)

        val bounds = blocks.map { RectF4(it.left, it.top, it.right, it.bottom) }
        val refineBudget = AtomicInteger(MAX_REFINES)
        val done = mutableListOf<TextPatch>()

        blocks.mapIndexed { index, block ->
            async {
                var lines = block.lines.map { it.text }
                val script = ScriptFilter.dominantScript(block.text) ?: block.script
                if (!block.vertical && block.confidence < REFINE_BELOW && refineBudget.getAndDecrement() > 0) {
                    refine(bitmap, block)?.let { lines = it }
                }
                val joiner = if (Languages.joinsWithoutSpaces(script)) "" else " "
                val original = TextNormalizer.normalize(lines, joiner, latin = script == Script.LATIN)
                if (!ScriptFilter.isWorthTranslating(original)) return@async null
                val from = resolveSourceLanguage(original, source, script, target) ?: return@async null
                val translated = translate(original, from, target) ?: return@async null
                if (translated.equals(original, ignoreCase = true)) return@async null
                val screenWidth = bitmap.width.toFloat()
                val patch = withContext(Dispatchers.Default) {
                    buildPatch(block, bounds[index], bounds, translated, bitmap, screenWidth)
                } ?: return@async null
                // نعمل على الخيط الرئيسي، فلا حاجة لمزامنة
                done += patch
                onPartial(done.toList())
                patch
            }
        }.awaitAll().filterNotNull()
    }

    // ------------------------------------------------------------------ خطة القراءة

    private data class Plan(val scripts: List<Script>, val verticalScript: Script?)

    private fun recent(script: Script): Boolean {
        val at = scriptSeenAt[script] ?: return false
        return SystemClock.elapsedRealtime() - at < SCRIPT_MEMORY_MS
    }

    private fun plan(source: String, discovery: Boolean): Plan {
        if (source != Languages.AUTO) {
            val script = Languages.scriptOf(source)
            val vertical = script == Script.JAPANESE || script == Script.CHINESE
            return Plan(listOf(script), if (vertical) script else null)
        }
        val scripts = mutableListOf(Script.LATIN, Script.JAPANESE, Script.KOREAN)
        if (discovery || recent(Script.CHINESE)) scripts += Script.CHINESE
        if (discovery || recent(Script.DEVANAGARI)) scripts += Script.DEVANAGARI
        val cjk = recent(Script.JAPANESE) || recent(Script.CHINESE)
        val verticalScript = when {
            !discovery && !cjk -> null
            recent(Script.CHINESE) && !recent(Script.JAPANESE) -> Script.CHINESE
            else -> Script.JAPANESE
        }
        return Plan(scripts, verticalScript)
    }

    private fun noteScripts(blocks: List<OcrBlock>) {
        val now = SystemClock.elapsedRealtime()
        for (b in blocks) {
            val s = ScriptFilter.dominantScript(b.text) ?: continue
            if (s != Script.LATIN && ScriptFilter.nativeCount(b.text) >= 2) scriptSeenAt[s] = now
        }
    }

    // ------------------------------------------------------------------ OCR

    private suspend fun safeRecognize(block: suspend () -> List<OcrBlock>): List<OcrBlock> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private suspend fun recognize(script: Script, bitmap: Bitmap): List<OcrBlock> {
        val text = recognizer(script).process(InputImage.fromBitmap(bitmap, 0)).await()
        return BlockMerger.merge(text.textBlocks.mapNotNull { it.toOcrBlock(script) })
    }

    /**
     * قراءة النص العمودي: تدوير اللقطة 90° عكس عقارب الساعة يجعل كل عمود (أعلى→أسفل) سطراً (يسار→يمين)،
     * فيقرؤه النموذج كنص أفقي عادي. تُدمج الأعمدة في الإطار المُدار ثم تُعاد للشاشة.
     * تُبقى فقط الكتل التي تحتوي حروفاً صينية/يابانية (النص الأفقي العادي يصبح بعد التدوير هراءً).
     */
    private suspend fun recognizeRotated(script: Script, bitmap: Bitmap): List<OcrBlock> {
        val rotated = withContext(Dispatchers.Default) {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(-90f) }, false)
        }
        return try {
            val text = recognizer(script).process(InputImage.fromBitmap(rotated, 0)).await()
            val blocks = text.textBlocks.mapNotNull { it.toOcrBlock(script, keepAngle = false) }
            BlockMerger.merge(blocks)
                .filter { b ->
                    val s = ScriptFilter.dominantScript(b.text)
                    (s == Script.JAPANESE || s == Script.CHINESE) && ScriptFilter.nativeCount(b.text) >= 2
                }
                .map { OcrFusion.unrotate(it, bitmap.width.toFloat()) }
        } finally {
            rotated.recycle()
        }
    }

    private fun Text.TextBlock.toOcrBlock(script: Script, keepAngle: Boolean = true): OcrBlock? {
        val ocrLines = lines.mapNotNull { line ->
            val b = line.boundingBox ?: return@mapNotNull null
            OcrLine(line.text, b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), line.confidence)
        }
        if (ocrLines.isEmpty()) return null
        val c = cornerPoints
        if (!keepAngle || c == null || c.size != 4) return OcrBlock(ocrLines, script = script)
        val angle = Math.toDegrees(atan2((c[1].y - c[0].y).toDouble(), (c[1].x - c[0].x).toDouble())).toFloat()
        val corners = FloatArray(8).also { arr -> c.forEachIndexed { i, p -> arr[i * 2] = p.x.toFloat(); arr[i * 2 + 1] = p.y.toFloat() } }
        return OcrBlock(ocrLines, angle, corners, script)
    }

    /**
     * إعادة قراءة كتلة ضعيفة الثقة: قصّ + تكبير حتى يصبح ارتفاع السطر ~56px + تحويل لتدرّج رمادي بتباين عالٍ.
     * يفيد كثيراً مع الخطوط اليدوية الصغيرة في الكوميكس والنص فوق خلفيات ملوّنة.
     */
    private suspend fun refine(bitmap: Bitmap, block: OcrBlock): List<String>? {
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
            val result = recognizer(block.script).process(InputImage.fromBitmap(enhanced, 0)).await()
            val lines = result.textBlocks.flatMap { it.lines }.sortedBy { it.boundingBox?.top ?: 0 }
            if (lines.isEmpty()) return null
            val confidence = lines.map { it.confidence }.average().toFloat()
            if (confidence > block.confidence + 0.05f) lines.map { it.text } else null
        } catch (e: CancellationException) {
            throw e
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

    /** لكل نموذج خيط مستقل حتى تعمل القراءات المتعددة بالتوازي فعلاً. */
    private fun recognizer(script: Script): TextRecognizer = recognizers.getOrPut(script) {
        val executor = executors.getOrPut(script) { Executors.newSingleThreadExecutor() }
        when (script) {
            Script.LATIN -> TextRecognition.getClient(TextRecognizerOptions.Builder().setExecutor(executor).build())
            Script.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().setExecutor(executor).build())
            Script.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().setExecutor(executor).build())
            Script.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().setExecutor(executor).build())
            Script.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().setExecutor(executor).build())
        }
    }

    // ------------------------------------------------------------------ اللغة والترجمة

    /**
     * يحدد لغة الكتلة، ويُرجع null إن لم تكن تحتاج ترجمة.
     * - الكورية/اليابانية (بالكانا)/الهندية تُعرف من نظام الكتابة مباشرة دون أي تخمين.
     * - الحروف الصينية وحدها: صينية أو يابانية (كانجي) حسب كشف اللغة.
     * - اللاتينية: تُقبل اللغة المكتشفة فقط إن كانت حزمتها منزّلة، وإلا تُعامل كإنجليزية؛
     *   هذا يمنع كشف النصوص القصيرة خطأً كـ hr أو vi.
     * إن كانت لغة مؤكدة (غير لاتينية) بلا حزمة، تُنزَّل في الخلفية بصمت وتُترجم بعد اكتمالها.
     */
    private suspend fun resolveSourceLanguage(text: String, source: String, script: Script, target: String): String? {
        val targetTag = ModelStore.tagOf(target) ?: return null
        if (source != Languages.AUTO) {
            val sourceScript = Languages.scriptOf(source)
            if (sourceScript != Script.LATIN && !ScriptFilter.containsScript(text, sourceScript)) return null
            if (source == target || !ModelStore.isReady(source)) return null
            if (sourceScript == Script.LATIN) {
                if (script != Script.LATIN) return null
                val top = candidates(text).firstOrNull()
                if (top != null && top.first == targetTag && top.second >= 0.6f) return null
            }
            return source
        }
        val code = when (script) {
            Script.LATIN -> return resolveLatin(text, targetTag)
            Script.CHINESE -> candidates(text).firstOrNull { it.first == "zh" || it.first == "ja" }?.first ?: "zh"
            else -> Languages.languageOfScript(script) ?: return null
        }
        if (code == targetTag) return null
        if (!ModelStore.isReady(code)) {
            ModelStore.requestInBackground(code)
            return null
        }
        return code
    }

    private suspend fun resolveLatin(text: String, targetTag: String): String? {
        val candidates = candidates(text)
        val top = candidates.firstOrNull()
        if (top != null && top.first == targetTag && top.second >= 0.5f) return null
        candidates.firstOrNull { (tag, conf) -> tag != targetTag && conf >= 0.3f && ModelStore.isReady(tag) }
            ?.let { return it.first }
        return if (targetTag != "en") "en" else null
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

    /** يُرجع مترجماً فقط إن كانت الحزمتان منزّلتين؛ لا تنزيل هنا. */
    private fun translator(from: String, to: String): Translator? {
        val src = ModelStore.tagOf(from) ?: return null
        val tgt = ModelStore.tagOf(to) ?: return null
        if (!ModelStore.isReady(src) || !ModelStore.isReady(tgt)) return null
        return translators.getOrPut("$src>$tgt") {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build())
        }
    }

    // ------------------------------------------------------------------ الرسم

    /**
     * يحدد صندوق الترجمة بحيث يحل محل النص الأصلي تماماً:
     * - نص أفقي: نفس الصندوق، والفقاعات المتمركزة تُكتب في الوسط.
     * - نص مائل قليلاً (≤45°، داخل الصور): نفس الزاوية والأبعاد.
     * - نص عمودي أو جانبي: صندوق مستقيم يغطي الأصل، موسّع أفقياً دون لمس النصوص المجاورة،
     *   والترجمة أفقية متمركزة.
     */
    private fun buildPatch(
        block: OcrBlock,
        box: RectF4,
        allBounds: List<RectF4>,
        translated: String,
        bitmap: Bitmap,
        screenWidth: Float,
    ): TextPatch? {
        val sampleBox = Rect(box.left.toInt(), box.top.toInt(), box.right.toInt(), box.bottom.toInt())
        if (sampleBox.width() < 4 || sampleBox.height() < 4) return null
        val bg = ColorSampler.background(bitmap, sampleBox)
        val fg = ColorSampler.foreground(bitmap, sampleBox, bg)
        val c = block.corners
        val sideways = block.vertical || PatchGeometry.isUprightVertical(block.angle, box.width, box.height)
        val tilt = if (sideways || c == null) 0f else PatchGeometry.drawAngle(block.angle)

        if (sideways) {
            // حجم الحرف الأصلي في النص العمودي = عرض العمود
            val glyph = block.lines.map { it.right - it.left }.average().toFloat()
            val gap = glyph * 0.3f
            val neighbours = allBounds.filter { it != box }
            val wide = PatchGeometry.widenVertical(box, neighbours, screenWidth, screenWidth * 0.5f, gap)
            return TextPatch(
                translated = translated,
                centerX = wide.centerX, centerY = (wide.top + wide.bottom) / 2f,
                width = wide.width, height = wide.height,
                angle = 0f,
                glyphSize = max(glyph, 1f),
                padding = max(2f, glyph * 0.12f),
                centered = true,
                background = bg, textColor = fg,
            )
        }

        val glyph = block.lineHeight
        if (tilt != 0f && c != null) {
            return TextPatch(
                translated = translated,
                centerX = (c[0] + c[2] + c[4] + c[6]) / 4f,
                centerY = (c[1] + c[3] + c[5] + c[7]) / 4f,
                width = hypot(c[2] - c[0], c[3] - c[1]),
                height = hypot(c[6] - c[0], c[7] - c[1]),
                angle = tilt,
                glyphSize = glyph,
                padding = max(2f, glyph * 0.12f),
                centered = block.isCentered,
                background = bg, textColor = fg,
            )
        }
        return TextPatch(
            translated = translated,
            centerX = box.centerX, centerY = (box.top + box.bottom) / 2f,
            width = box.width, height = box.height,
            angle = 0f,
            glyphSize = glyph,
            padding = max(2f, min(glyph * 0.12f, 12f)),
            centered = block.isCentered,
            background = bg, textColor = fg,
        )
    }

    override fun close() {
        recognizers.values.forEach { it.close() }
        translators.values.forEach { it.close() }
        executors.values.forEach { it.shutdown() }
        languageId.close()
        recognizers.clear()
        translators.clear()
        executors.clear()
    }

    private companion object {
        const val REFINE_BELOW = 0.7f
        const val MAX_REFINES = 6
        const val DISCOVERY_EVERY = 5
        const val SCRIPT_MEMORY_MS = 90_000L
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
