package app.safar.browser.translate

import android.graphics.Bitmap
import android.graphics.Rect
import app.safar.browser.data.BrowserSettings
import app.safar.browser.data.ContentKind
import app.safar.browser.data.ProviderId
import app.safar.browser.data.RTL_LANGS
import app.safar.browser.engine.await
import app.safar.browser.engine.geckoResult
import app.safar.browser.translate.ocr.Box
import app.safar.browser.translate.ocr.BubbleAnalyzer
import app.safar.browser.translate.ocr.OcrBlock
import app.safar.browser.translate.ocr.OcrPostProcessor
import app.safar.browser.translate.ocr.OcrRouter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import java.util.concurrent.atomic.AtomicInteger

data class TranslateStatus(
    val busy: Boolean = false,
    val lastProvider: ProviderId? = null,
    val detectedSource: String? = null,
    val error: String? = null,
    /** رسالة معلومات مؤقتة (مثل تنزيل نموذج اللغة لأول مرة). */
    val note: String? = null,
    /** آخر دورة ترجمة مستمرة (للتأكد بصرياً أنها تعمل). */
    val info: String? = null,
)

/** فقاعة مترجمة. الإحداثيات بالبكسل داخل اللقطة. */
data class TranslatedBox(
    val box: Box,
    val original: String,
    val translated: String,
    val clean: Box? = null,
    val textArea: Box = box,
    val bg: Int = 0xFFFFFF,
    val uniform: Boolean = false,
    val darkText: Boolean = true,
    /** عدد أسطر النص الأصلي (لتقدير حجم خطه). */
    val lines: Int = 1,
)

/**
 * الجسر بين إضافة الترجمة المدمجة (content.js) والتطبيق:
 *  - نصوص: السكربت يرسل مقاطع DOM المرئية فقط على دفعات ⇒ ترجمة + ذاكرة مؤقتة ⇒ استبدال في مكانها.
 *  - مانغا/صور: لقطة عند توقف التمرير ⇒ OCR للشاشة كاملة ⇒ تحليل الفقاعات ⇒ ترجمة بترتيب القراءة
 *    ⇒ السكربت يطلي النص الأصلي بلون الفقاعة ويكتب الترجمة داخلها.
 *  - فيديو: مسارات VTT أو OCR دوري للترجمة المحروقة.
 *  - المربع العائم: يختار الفقاعات/النصوص التي يقع مركزها داخله (لا يقص النص).
 */
class LiveTranslateController(
    private val runtime: GeckoRuntime,
    private val scope: CoroutineScope,
    private val settings: () -> BrowserSettings,
    private val router: ProviderRouter,
    private val ocr: OcrRouter,
    /** الصفحة مصوّرة بينما الوضع "نصوص" ⇒ التبديل لوضع الصور/المانغا. */
    private val onSuggestManga: () -> Unit = {},
    /** مستوى التكبير المحفوظ لموقع (من رابط الصفحة). */
    private val zoomFor: (String?) -> Int = { 100 },
) {
    var geckoView: GeckoView? = null

    private var extension: WebExtension? = null
    private val ports = mutableMapOf<GeckoSession, MutableList<WebExtension.Port>>()
    private val innerWidth = mutableMapOf<GeckoSession, Double>()
    private val cache = TranslationCache()
    private val rectRequests = mutableMapOf<Int, CompletableDeferred<String>>()
    private val requestIds = AtomicInteger(1)
    private val lastSubtitle = mutableMapOf<GeckoSession, Pair<String, String>>()
    private val subtitleHistory = ArrayDeque<String>()
    /** لقطة واحدة في كل مرة (OCR ثقيل). */
    private val captureLock = Mutex()
    /** بصمة آخر لقطة لكل تبويب: لا نعيد ترجمة نفس الشاشة إن لم يتغير محتواها أو موقعها. */
    private val lastHash = mutableMapOf<GeckoSession, Long>()
    private val hideAcks = mutableMapOf<Int, CompletableDeferred<Unit>>()
    private val pageTextRequests = mutableMapOf<Int, CompletableDeferred<Pair<String, String>>>()

    private val _status = MutableStateFlow(TranslateStatus())
    val status: StateFlow<TranslateStatus> = _status

    suspend fun install(existing: List<GeckoSession>) {
        extension = runtime.webExtensionController
            .ensureBuiltIn("resource://android/assets/extensions/livetranslate/", "livetranslate@safar.app")
            .await()
        existing.forEach { attach(it) }
    }

    fun attach(session: GeckoSession) {
        val ext = extension ?: return
        session.webExtensionController.setMessageDelegate(ext, delegate, APP)
    }

    fun detach(session: GeckoSession) {
        ports.remove(session); innerWidth.remove(session); lastSubtitle.remove(session); lastHash.remove(session)
    }

    private fun topPorts(session: GeckoSession?) = session?.let { s -> ports[s]?.filter { it.sender.isTopLevel } }.orEmpty()

    /** مسح ترجمات المربع العائم من الصفحة فوراً (عند بدء تحريكه/تكبيره). */
    fun clearFloatingOverlays(session: GeckoSession?) {
        topPorts(session).forEach { runCatching { it.postMessage(JSONObject().put("cmd", "clearOverlays").put("scope", "float")) } }
    }

    /**
     * إخفاء طبقة الترجمة في الصفحة ⇒ انتظار تأكيد السكربت بعد إعادة الرسم ⇒ تنفيذ الالتقاط ⇒ إعادة الإظهار.
     * يضمن أن OCR يقرأ الصفحة الأصلية فقط ولا يعيد ترجمة ترجماتنا.
     */
    private suspend fun <T> withOverlaysHidden(session: GeckoSession?, block: suspend () -> T): T {
        val targets = topPorts(session)
        if (targets.isEmpty()) return block()
        val id = requestIds.getAndIncrement()
        val ack = CompletableDeferred<Unit>()
        hideAcks[id] = ack
        targets.forEach { runCatching { it.postMessage(JSONObject().put("cmd", "hide").put("id", id)) } }
        withTimeoutOrNull(800) { ack.await() }
        hideAcks.remove(id)
        try {
            return block()
        } finally {
            targets.forEach { runCatching { it.postMessage(JSONObject().put("cmd", "show")) } }
        }
    }

    /** بصمة سريعة للصورة (شبكة 32×32 من الإضاءة المكمّاة) لاكتشاف "لم يتغير شيء". */
    private fun quickHash(bmp: Bitmap, region: Rect? = null): Long {
        val r = region ?: Rect(0, 0, bmp.width, bmp.height)
        var h = 1125899906842597L
        for (gy in 0 until 32) for (gx in 0 until 32) {
            val x = (r.left + (r.width() - 1) * gx / 31).coerceIn(0, bmp.width - 1)
            val y = (r.top + (r.height() - 1) * gy / 31).coerceIn(0, bmp.height - 1)
            h = 31 * h + (BubbleAnalyzer.lum(bmp.getPixel(x, y)) shr 4)
        }
        return h * 31 + r.hashCode()
    }

    /** عند الانتقال لموقع آخر: نعيد اكتشاف لغة المانغا. */
    fun onNavigated() = ocr.resetDetection()

    fun setNote(note: String?) { _status.value = _status.value.copy(note = note) }

    fun pushConfig() {
        val cfg = configJson()
        ports.values.flatten().forEach { runCatching { it.postMessage(cfg) } }
        if (!settings().translate.enabled) _status.value = TranslateStatus()
    }

    fun clearCache() = cache.clear()

    /** تطبيق مستوى تكبير على الصفحة الحالية فوراً. */
    fun setZoom(session: GeckoSession?, pct: Int) {
        topPorts(session).forEach { runCatching { it.postMessage(JSONObject().put("cmd", "zoom").put("value", pct)) } }
    }

    /** نص الصفحة ولغتها (للاستماع إلى الصفحة). */
    suspend fun requestPageText(session: GeckoSession?): Pair<String, String>? {
        val port = topPorts(session).firstOrNull() ?: return null
        val id = requestIds.getAndIncrement()
        val d = CompletableDeferred<Pair<String, String>>()
        pageTextRequests[id] = d
        port.postMessage(JSONObject().put("cmd", "pageText").put("id", id))
        return withTimeoutOrNull(4000) { d.await() }.also { pageTextRequests.remove(id) }
    }

    private fun configJson(): JSONObject {
        val t = settings().translate
        return JSONObject()
            .put("cmd", "config")
            .put("enabled", t.enabled)
            .put("mode", t.mode.name)
            .put("kind", t.content.name)
            .put("source", t.source)
            .put("target", t.target)
            .put("rtl", t.target in RTL_LANGS)
            .put("style", t.overlayStyle.name)
            .put("opacity", t.overlayOpacity.coerceIn(0, 35))
            .put("visible", t.overlaysVisible)
    }

    private val delegate = object : WebExtension.MessageDelegate {
        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            val msg = message as? JSONObject ?: return null
            val session = sender.session
            return when (msg.optString("type")) {
                "translate" -> scope.geckoResult<Any> { handleTranslate(msg) }
                "captureViewport" -> scope.geckoResult<Any> { handleCaptureViewport(msg, session) }
                "captureRegion" -> scope.geckoResult<Any> { handleCaptureRegion(msg, session) }
                "suggestManga" -> {
                    if (sender.isTopLevel) onSuggestManga()
                    GeckoResult.fromValue(JSONObject())
                }
                "hiddenAck" -> {
                    hideAcks.remove(msg.optInt("id"))?.complete(Unit)
                    GeckoResult.fromValue(JSONObject())
                }
                "rectTextResult" -> {
                    rectRequests.remove(msg.optInt("id"))?.complete(msg.optString("text"))
                    GeckoResult.fromValue(JSONObject())
                }
                else -> null
            }
        }

        override fun onConnect(port: WebExtension.Port) {
            val session = port.sender.session ?: return
            ports.getOrPut(session) { mutableListOf() }.add(port)
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    val m = message as? JSONObject ?: return
                    // طلبات السكربت عبر المنفذ (Port) بدلاً من sendNativeMessage: نفس القناة التي يعمل بها المربع العائم
                    if (m.has("rpc")) { handleRpc(m, session, port); return }
                    if (m.optString("type") == "viewport" && port.sender.isTopLevel) {
                        // عرض منطقة العرض المرئية (visualViewport) بوحدات CSS — يراعي التكبير بالإصبعين
                        innerWidth[session] = m.optDouble("vw", m.optDouble("innerWidth", 0.0))
                    }
                }
                override fun onDisconnect(port: WebExtension.Port) {
                    ports[session]?.remove(port)
                }
            })
            port.postMessage(configJson().put("zoom", zoomFor(port.sender.url)))
        }
    }

    private fun handleRpc(m: JSONObject, session: GeckoSession, port: WebExtension.Port) {
        val id = m.optInt("rpc")
        scope.launch(Dispatchers.Main) {
            val data: JSONObject = try {
                when (m.optString("type")) {
                    "translate" -> handleTranslate(m)
                    "captureViewport" -> handleCaptureViewport(m, session)
                    "captureRegion" -> handleCaptureRegion(m, session)
                    "hiddenAck" -> { hideAcks.remove(m.optInt("id"))?.complete(Unit); JSONObject() }
                    "rectTextResult" -> { rectRequests.remove(m.optInt("id"))?.complete(m.optString("text")); JSONObject() }
                    "suggestManga" -> { if (port.sender.isTopLevel) onSuggestManga(); JSONObject() }
                    "pageTextResult" -> { pageTextRequests.remove(m.optInt("id"))?.complete(m.optString("text") to m.optString("lang")); JSONObject() }
                    else -> JSONObject().put("error", "unknown")
                }
            } catch (e: Throwable) {
                JSONObject().put("error", e.message ?: "error")
            }
            runCatching { port.postMessage(JSONObject().put("cmd", "rpc").put("rpc", id).put("data", data)) }
        }
    }

    // ---------------------------------------------------------------- ترجمة النصوص

    suspend fun translateCached(
        texts: List<String>,
        kind: ContentKind,
        context: String? = null,
        sourceOverride: String? = null,
    ): List<String> {
        val t = settings().translate
        val tag = kind.name
        val result = arrayOfNulls<String>(texts.size)
        val missing = LinkedHashSet<String>()
        texts.forEachIndexed { i, s ->
            val hit = cache.get(t.target, tag, s)
            if (hit != null) result[i] = hit else missing += s
        }
        if (missing.isNotEmpty()) {
            _status.value = _status.value.copy(busy = true, error = null)
            try {
                val source = if (t.source == "auto") sourceOverride ?: "auto" else t.source
                val req = TranslationRequest(missing.toList(), source, t.target, kind, context, t.llmInstructions)
                val res = router.translate(req, t.provider)
                missing.forEachIndexed { i, src -> cache.put(t.target, tag, src, res.texts[i]) }
                _status.value = TranslateStatus(false, res.provider, res.detectedSource ?: sourceOverride, null, _status.value.note)
            } catch (e: Throwable) {
                _status.value = _status.value.copy(busy = false, error = e.message ?: "خطأ غير معروف")
                throw e
            }
        }
        return texts.mapIndexed { i, s -> result[i] ?: cache.get(t.target, tag, s) ?: s }
    }

    private suspend fun handleTranslate(msg: JSONObject): JSONObject {
        val texts = msg.getJSONArray("texts").toStrings()
        val kind = runCatching { ContentKind.valueOf(msg.optString("kind", "TEXT")) }.getOrDefault(ContentKind.TEXT)
        return try {
            val ctx = msg.optString("title").takeIf { it.isNotBlank() }?.let { "Page title: $it" }
            JSONObject().put("translations", JSONArray(translateCached(texts, kind, ctx)))
        } catch (e: Throwable) {
            JSONObject().put("error", e.message ?: "error")
        }
    }

    // ---------------------------------------------------------------- صور / مانغا

    private suspend fun capture(session: GeckoSession?): Bitmap? {
        val view = geckoView ?: return null
        if (session != null && view.session !== session) return null // فقط التبويب الظاهر
        return view.capturePixels().await()
    }

    /** ترتيب القراءة: اليابانية من اليمين لليسار، وغيرها من اليسار لليمين، صفاً بعد صف. */
    private fun readingOrder(blocks: List<OcrBlock>, lang: String?): List<OcrBlock> {
        val row = 90f
        return if (lang == "ja") blocks.sortedWith(compareBy({ (it.box.top / row).toInt() }, { -it.box.right }))
        else blocks.sortedWith(compareBy({ (it.box.top / row).toInt() }, { it.box.left }))
    }

    /**
     * OCR للشاشة كاملة ⇒ تصفية الرموز العشوائية ⇒ تحليل الفقاعات ⇒ تجميع كتل الفقاعة الواحدة داخل حدودها الفعلية
     * ⇒ ترجمة بترتيب القراءة.
     * @param region (اختياري) يختار الفقاعات التي يقع مركزها داخله (المربع العائم).
     * @param covered مناطق مترجمة مسبقاً (بكسلات اللقطة): لا نعيد ترجمتها.
     */
    suspend fun ocrTranslate(bitmap: Bitmap, kind: ContentKind, region: Rect? = null, covered: List<Box> = emptyList()): List<TranslatedBox> {
        val t = settings().translate
        val res = ocr.recognize(bitmap, t.source, kind, t.ocr)
        fun center(b: Box) = Pair((b.left + b.right) / 2, (b.top + b.bottom) / 2)
        var blocks = res.blocks.filter { b ->
            val (cx, cy) = center(b.box)
            covered.none { c -> cx >= c.left && cx <= c.right && cy >= c.top && cy <= c.bottom }
        }
        if (blocks.isEmpty()) return emptyList()

        val w = bitmap.width; val h = bitmap.height
        val px = withContext(Dispatchers.Default) { IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) } }

        // تحليل أولي لكل كتلة (مع استبعاد الكتل الأخرى من حلقة الخلفية) ثم تجميع الكتل التي تشترك في فقاعة واحدة
        val merged = withContext(Dispatchers.Default) {
            val boxes = blocks.map { it.box }
            val first = boxes.mapIndexed { i, b -> BubbleAnalyzer.analyze(px, w, h, b, boxes.filterIndexed { j, _ -> j != i }) }
            BubbleAnalyzer.groupByBubble(boxes, first, px, w, h, allowColumns = res.lang == "ja" || res.lang == "zh").map { idx ->
                if (idx.size == 1) blocks[idx[0]]
                else {
                    val parts = idx.map { blocks[it] }.sortedWith(compareBy({ it.box.top }, { if (res.lang == "ja") -it.box.right else it.box.left }))
                    val union = parts.map { it.box }.reduce { a, b -> a.union(b) }
                    OcrBlock(OcrPostProcessor.joinLines(parts.map { it.text }), union, parts.flatMap { it.lines })
                }
            }
        }
        blocks = merged
        if (region != null) {
            blocks = blocks.filter { b ->
                val (cx, cy) = center(b.box)
                cx >= region.left && cx <= region.right && cy >= region.top && cy <= region.bottom
            }
        }
        if (blocks.isEmpty()) return emptyList()
        blocks = readingOrder(blocks, res.lang)
        val ctx = if (kind == ContentKind.MANGA) "Speech bubbles and captions from one comic/manga page, in reading order." else null
        val translated = translateCached(blocks.map { it.text }, kind, ctx, res.lang)

        val bubbles = withContext(Dispatchers.Default) {
            val boxes = blocks.map { it.box }
            boxes.mapIndexed { i, b -> BubbleAnalyzer.analyze(px, w, h, b, boxes.filterIndexed { j, _ -> j != i }) }
        }
        // مساحات الكتابة لا تتراكب أبداً: إن تداخلت مساحتا فقاعتين نعود لمحيط نص كل منهما
        val areas = bubbles.map { it.textArea }.toMutableList()
        for (i in areas.indices) for (j in i + 1 until areas.size) {
            if (BubbleAnalyzer.iou(areas[i], areas[j]) > 0.05f) { areas[i] = blocks[i].box; areas[j] = blocks[j].box }
        }
        return blocks.mapIndexed { i, b ->
            val bb = bubbles[i]
            TranslatedBox(b.box, b.text, translated[i], bb.clean, areas[i], bb.bg, bb.uniform, bb.darkText, b.lines.size.coerceAtLeast(1))
        }
    }

    private fun boxesJson(boxes: List<TranslatedBox>, css: Double): JSONArray = JSONArray(boxes.map { b ->
        fun rect(r: Box) = JSONObject()
            .put("x", r.left * css).put("y", r.top * css).put("w", r.width * css).put("h", r.height * css)
        JSONObject()
            .put("text", b.translated).put("orig", b.original)
            .put("box", rect(b.box))
            .put("area", rect(b.textArea))
            .put("clean", b.clean?.let { rect(it) } ?: JSONObject.NULL)
            .put("bg", BubbleAnalyzer.hex(b.bg))
            .put("uniform", b.uniform)
            .put("dark", b.darkText)
            // ارتفاع سطر النص الأصلي (CSS px): أساس حجم خط الترجمة كي يبقى متناسقاً مع الرسم
            .put("lh", b.box.height / b.lines * css)
    })

    /** الترجمة المستمرة: السكربت أخفى طبقته بنفسه قبل الطلب، فاللقطة نظيفة. */
    private suspend fun handleCaptureViewport(msg: JSONObject, session: GeckoSession?): JSONObject = captureLock.withLock {
        val bmp = capture(session) ?: return@withLock JSONObject().put("boxes", JSONArray()).put("error", "no-capture")
        val vw = msg.optDouble("vw", msg.optDouble("innerWidth", bmp.width.toDouble()))
        val css = vw / bmp.width
        val hash = withContext(Dispatchers.Default) { quickHash(bmp) }
        if (!msg.optBoolean("force") && session != null && lastHash[session] == hash) {
            return@withLock JSONObject().put("unchanged", true)
        }
        // المناطق المترجمة مسبقاً (CSS نسبة للشاشة) ⇒ بكسلات اللقطة
        val covered = msg.optJSONArray("covered")?.let { arr ->
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Box((o.getDouble("x") / css).toFloat(), (o.getDouble("y") / css).toFloat(),
                    ((o.getDouble("x") + o.getDouble("w")) / css).toFloat(), ((o.getDouble("y") + o.getDouble("h")) / css).toFloat())
            }
        }.orEmpty()
        try {
            _status.value = _status.value.copy(info = "مستمرة: جارٍ القراءة…")
            val boxes = ocrTranslate(bmp, ContentKind.MANGA, covered = covered)
            session?.let { lastHash[it] = hash }
            _status.value = _status.value.copy(info = "مستمرة: ${boxes.size} فقاعة")
            JSONObject().put("boxes", boxesJson(boxes, css))
        } catch (e: Throwable) {
            JSONObject().put("boxes", JSONArray()).put("error", e.message ?: "error")
        }
    }

    // ---------------------------------------------------------------- فيديو (ترجمة محروقة)

    private suspend fun handleCaptureRegion(msg: JSONObject, session: GeckoSession?): JSONObject = captureLock.withLock {
        val unchanged = JSONObject().put("unchanged", true)
        val bmp = capture(session) ?: return@withLock unchanged
        val f = bmp.width / msg.optDouble("innerWidth", bmp.width.toDouble())
        val r = msg.getJSONObject("rect")
        val crop = Rect(
            (r.getDouble("x") * f).toInt(), (r.getDouble("y") * f).toInt(),
            ((r.getDouble("x") + r.getDouble("w")) * f).toInt(), ((r.getDouble("y") + r.getDouble("h")) * f).toInt(),
        )
        try {
            val t = settings().translate
            val region = withContext(Dispatchers.Default) {
                crop.intersect(0, 0, bmp.width, bmp.height)
                if (crop.width() < 8 || crop.height() < 8) null else Bitmap.createBitmap(bmp, crop.left, crop.top, crop.width(), crop.height())
            } ?: return@withLock unchanged
            val res = ocr.recognize(region, t.source, ContentKind.VIDEO, t.ocr)
            val text = res.blocks.joinToString(" ") { it.text }.trim()
            val key = session ?: return@withLock unchanged
            val prev = lastSubtitle[key]
            if (normalize(text) == normalize(prev?.first.orEmpty())) return@withLock unchanged
            if (text.isEmpty()) {
                lastSubtitle[key] = "" to ""
                return@withLock JSONObject().put("text", "")
            }
            val ctx = subtitleHistory.takeLast(3).joinToString("\n").takeIf { it.isNotBlank() }?.let { "Previous subtitle lines:\n$it" }
            val tr = translateCached(listOf(text), ContentKind.VIDEO, ctx, res.lang).first()
            lastSubtitle[key] = text to tr
            subtitleHistory.addLast(text); if (subtitleHistory.size > 10) subtitleHistory.removeFirst()
            JSONObject().put("text", tr).put("orig", text).put("rtl", t.target in RTL_LANGS)
        } catch (e: Throwable) {
            JSONObject().put("unchanged", true).put("error", e.message)
        }
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("""[\s\p{P}]+"""), "")

    // ---------------------------------------------------------------- المربع العائم

    /**
     * ترجمة منطقة من شاشة GeckoView (بكسلات العرض).
     *  - نصوص: نص DOM داخل المستطيل (أسرع وأدق)، ثم OCR إن لم يوجد.
     *  - مانغا/صور: OCR للشاشة كاملة ثم اختيار الفقاعات التي يقع مركزها داخل المربع
     *    (فلا تُقص الكلمات عند الحواف)، وتُرسم الترجمة داخل الفقاعات نفسها في الصفحة.
     * @return نص الترجمة لعرضه في المربع (أو ملخص عند الرسم داخل الصفحة).
     */
    suspend fun translateRegion(viewRect: Rect, session: GeckoSession?): RegionResult {
        val kind = settings().translate.content
        if (kind == ContentKind.TEXT && session != null) {
            val domText = requestRectText(viewRect, session)
            if (!domText.isNullOrBlank()) return RegionResult(translateCached(listOf(domText), ContentKind.TEXT).first(), 0)
        }
        return captureLock.withLock {
            val bmp = withOverlaysHidden(session) { capture(session) } ?: return@withLock RegionResult("", 0)
            val boxes = ocrTranslate(bmp, ContentKind.MANGA, viewRect)
            if (boxes.isNotEmpty() && kind != ContentKind.TEXT && session != null) {
                val iw = innerWidth[session]?.takeIf { it > 0 }
                val css = (iw ?: bmp.width.toDouble()) / bmp.width
                topPorts(session).forEach {
                    // replace=float: يمسح ترجمات المربع السابقة قبل رسم الجديدة (لا تراكم)
                    it.postMessage(JSONObject().put("cmd", "overlays").put("scope", "float").put("boxes", boxesJson(boxes, css)))
                }
            }
            RegionResult(boxes.joinToString("\n") { it.translated }, boxes.size)
        }
    }

    data class RegionResult(val text: String, val placedInPage: Int)

    private suspend fun requestRectText(viewRect: Rect, session: GeckoSession): String? {
        val view = geckoView ?: return null
        val iw = innerWidth[session]?.takeIf { it > 0 } ?: return null
        val css = iw / view.width
        val port = ports[session]?.firstOrNull { it.sender.isTopLevel } ?: return null
        val id = requestIds.getAndIncrement()
        val deferred = CompletableDeferred<String>()
        rectRequests[id] = deferred
        port.postMessage(
            JSONObject().put("cmd", "rectText").put("id", id).put(
                "rect", JSONObject()
                    .put("x", viewRect.left * css).put("y", viewRect.top * css)
                    .put("w", viewRect.width() * css).put("h", viewRect.height() * css)
            )
        )
        return withTimeoutOrNull(1500) { deferred.await() }.also { rectRequests.remove(id) }
    }

    companion object {
        const val APP = "safar"
    }
}

private fun JSONArray.toStrings(): List<String> = List(length()) { optString(it) }
