package app.safar.browser.translate

import android.graphics.Bitmap
import android.graphics.Rect
import app.safar.browser.data.BrowserSettings
import app.safar.browser.data.ContentKind
import app.safar.browser.data.ProviderId
import app.safar.browser.engine.await
import app.safar.browser.engine.geckoResult
import app.safar.browser.translate.ocr.Box
import app.safar.browser.translate.ocr.OcrRouter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
)

data class TranslatedBox(val box: Box, val original: String, val translated: String)

/**
 * الجسر بين إضافة الترجمة المدمجة (content.js) والتطبيق:
 *  - نصوص: السكربت يرسل مقاطع DOM المرئية فقط على دفعات -> ترجمة + ذاكرة مؤقتة -> يستبدلها في مكانها.
 *  - صور/مانغا: عند توقف التمرير يطلب السكربت لقطة -> GeckoView.capturePixels -> OCR -> ترجمة -> مربعات فوق الصورة.
 *  - فيديو: ترجمة مسارات الترجمة النصية (VTT) إن وجدت، وإلا OCR دوري لمنطقة الترجمة المحروقة.
 *  - المربع الزجاجي العائم: ترجمة منطقة محددة عند الطلب (نص DOM أولاً لأنه أسرع، ثم OCR).
 */
class LiveTranslateController(
    private val runtime: GeckoRuntime,
    private val scope: CoroutineScope,
    private val settings: () -> BrowserSettings,
    private val router: ProviderRouter,
    private val ocr: OcrRouter,
) {
    /** GeckoView المعروض حالياً (لالتقاط البكسلات). يضبطه الـ UI. */
    var geckoView: GeckoView? = null

    private var extension: WebExtension? = null
    private val ports = mutableMapOf<GeckoSession, MutableList<WebExtension.Port>>()
    private val innerWidth = mutableMapOf<GeckoSession, Double>()
    private val cache = TranslationCache()
    private val rectRequests = mutableMapOf<Int, CompletableDeferred<String>>()
    private val requestIds = AtomicInteger(1)
    private val lastSubtitle = mutableMapOf<GeckoSession, Pair<String, String>>()
    private val subtitleHistory = ArrayDeque<String>()

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
        ports.remove(session); innerWidth.remove(session); lastSubtitle.remove(session)
    }

    /** إرسال الإعدادات الحالية لكل الصفحات المفتوحة (تشغيل/إيقاف، نوع المحتوى، اللغات...). */
    fun pushConfig() {
        val cfg = configJson()
        ports.values.flatten().forEach { runCatching { it.postMessage(cfg) } }
        if (!settings().translate.enabled) _status.value = TranslateStatus()
    }

    fun clearCache() = cache.clear()

    private fun configJson(): JSONObject {
        val t = settings().translate
        return JSONObject()
            .put("cmd", "config")
            .put("enabled", t.enabled)
            .put("mode", t.mode.name)
            .put("kind", t.content.name)
            .put("source", t.source)
            .put("target", t.target)
    }

    private val delegate = object : WebExtension.MessageDelegate {
        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            val msg = message as? JSONObject ?: return null
            val session = sender.session
            return when (msg.optString("type")) {
                "translate" -> scope.geckoResult<Any> { handleTranslate(msg) }
                "captureViewport" -> scope.geckoResult<Any> { handleCaptureViewport(msg, session) }
                "captureRegion" -> scope.geckoResult<Any> { handleCaptureRegion(msg, session) }
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
                    if (m.optString("type") == "viewport" && port.sender.isTopLevel) {
                        innerWidth[session] = m.optDouble("innerWidth", 0.0)
                    }
                }
                override fun onDisconnect(port: WebExtension.Port) {
                    ports[session]?.remove(port)
                }
            })
            port.postMessage(configJson())
        }
    }

    // ---------------------------------------------------------------- ترجمة النصوص

    suspend fun translateCached(texts: List<String>, kind: ContentKind, context: String? = null): List<String> {
        val t = settings().translate
        val tag = kind.name
        val result = arrayOfNulls<String>(texts.size)
        val missing = LinkedHashSet<String>()
        texts.forEachIndexed { i, s ->
            val hit = cache.get(t.target, tag, s)
            if (hit != null) result[i] = hit else missing += s
        }
        if (missing.isNotEmpty()) {
            _status.value = _status.value.copy(busy = true)
            try {
                val req = TranslationRequest(missing.toList(), t.source, t.target, kind, context, t.llmInstructions)
                val res = router.translate(req, t.provider)
                missing.forEachIndexed { i, src -> cache.put(t.target, tag, src, res.texts[i]) }
                _status.value = TranslateStatus(false, res.provider, res.detectedSource, null)
            } catch (e: Throwable) {
                _status.value = _status.value.copy(busy = false, error = e.message)
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

    /** OCR + ترجمة لكل كتلة؛ الإحداثيات بالبكسل داخل الصورة الملتقطة. */
    suspend fun ocrTranslate(bitmap: Bitmap, crop: Rect?, kind: ContentKind): List<TranslatedBox> {
        val t = settings().translate
        val region = withContext(Dispatchers.Default) {
            if (crop == null) bitmap else {
                val r = Rect(crop).apply { intersect(0, 0, bitmap.width, bitmap.height) }
                if (r.width() <= 4 || r.height() <= 4) null else Bitmap.createBitmap(bitmap, r.left, r.top, r.width(), r.height())
            }
        } ?: return emptyList()
        val blocks = ocr.recognize(region, t.source, kind, t.ocr)
        if (blocks.isEmpty()) return emptyList()
        val translated = translateCached(blocks.map { it.text }, kind)
        val dx = crop?.left?.toFloat() ?: 0f
        val dy = crop?.top?.toFloat() ?: 0f
        return blocks.mapIndexed { i, b -> TranslatedBox(b.box.offset(dx, dy), b.text, translated[i]) }
    }

    private suspend fun handleCaptureViewport(msg: JSONObject, session: GeckoSession?): JSONObject {
        val bmp = capture(session) ?: return JSONObject().put("boxes", JSONArray())
        val css = msg.optDouble("innerWidth", bmp.width.toDouble()) / bmp.width
        return try {
            val boxes = ocrTranslate(bmp, null, ContentKind.MANGA)
            JSONObject().put("boxes", JSONArray(boxes.map { b ->
                JSONObject()
                    .put("x", b.box.left * css).put("y", b.box.top * css)
                    .put("w", b.box.width * css).put("h", b.box.height * css)
                    .put("text", b.translated).put("orig", b.original)
            }))
        } catch (e: Throwable) {
            JSONObject().put("boxes", JSONArray()).put("error", e.message)
        }
    }

    // ---------------------------------------------------------------- فيديو (ترجمة محروقة)

    private suspend fun handleCaptureRegion(msg: JSONObject, session: GeckoSession?): JSONObject {
        val bmp = capture(session) ?: return JSONObject().put("unchanged", true)
        val f = bmp.width / msg.optDouble("innerWidth", bmp.width.toDouble())
        val r = msg.getJSONObject("rect")
        val crop = Rect(
            (r.getDouble("x") * f).toInt(), (r.getDouble("y") * f).toInt(),
            ((r.getDouble("x") + r.getDouble("w")) * f).toInt(), ((r.getDouble("y") + r.getDouble("h")) * f).toInt(),
        )
        return try {
            val t = settings().translate
            val region = withContext(Dispatchers.Default) {
                Rect(crop).let { c ->
                    c.intersect(0, 0, bmp.width, bmp.height)
                    if (c.width() < 8 || c.height() < 8) null else Bitmap.createBitmap(bmp, c.left, c.top, c.width(), c.height())
                }
            } ?: return JSONObject().put("unchanged", true)
            val text = ocr.recognize(region, t.source, ContentKind.VIDEO, t.ocr).joinToString(" ") { it.text }.trim()
            val key = session ?: return JSONObject().put("unchanged", true)
            val prev = lastSubtitle[key]
            if (normalize(text) == normalize(prev?.first.orEmpty())) return JSONObject().put("unchanged", true)
            if (text.isEmpty()) {
                lastSubtitle[key] = "" to ""
                return JSONObject().put("text", "")
            }
            // سياق: آخر 3 أسطر ترجمة تساعد نماذج الذكاء الاصطناعي على اتساق الحوار
            val ctx = subtitleHistory.takeLast(3).joinToString("\n").takeIf { it.isNotBlank() }?.let { "Previous subtitle lines:\n$it" }
            val tr = translateCached(listOf(text), ContentKind.VIDEO, ctx).first()
            lastSubtitle[key] = text to tr
            subtitleHistory.addLast(text); if (subtitleHistory.size > 10) subtitleHistory.removeFirst()
            JSONObject().put("text", tr).put("orig", text)
        } catch (e: Throwable) {
            JSONObject().put("unchanged", true).put("error", e.message)
        }
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("""[\s\p{P}]+"""), "")

    // ---------------------------------------------------------------- المربع العائم

    /**
     * ترجمة منطقة من شاشة GeckoView (بكسلات العرض).
     * للنصوص: نطلب نص DOM داخل المستطيل من السكربت (أسرع وأدق وأقل استهلاكاً) ثم نلجأ لـ OCR.
     */
    suspend fun translateRegion(viewRect: Rect, session: GeckoSession?): String {
        val kind = settings().translate.content
        if (kind == ContentKind.TEXT && session != null) {
            val domText = requestRectText(viewRect, session)
            if (!domText.isNullOrBlank()) return translateCached(listOf(domText), ContentKind.TEXT).first()
        }
        val bmp = capture(session) ?: return ""
        val boxes = ocrTranslate(bmp, viewRect, if (kind == ContentKind.TEXT) ContentKind.MANGA else kind)
        return boxes.sortedWith(compareBy({ it.box.top }, { it.box.left })).joinToString("\n") { it.translated }
    }

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
