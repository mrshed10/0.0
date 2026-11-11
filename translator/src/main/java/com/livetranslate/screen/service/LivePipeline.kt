package com.livetranslate.screen.service

import android.os.SystemClock
import com.livetranslate.screen.capture.OcrEngine
import com.livetranslate.screen.capture.ScreenCaptureEngine
import com.livetranslate.screen.core.BlockGrouper
import com.livetranslate.screen.core.Box
import com.livetranslate.screen.core.Caption
import com.livetranslate.screen.core.ContentMode
import com.livetranslate.screen.core.DisplayMode
import com.livetranslate.screen.core.FrameSignature
import com.livetranslate.screen.core.ReadingOrder
import com.livetranslate.screen.core.RenderedBlock
import com.livetranslate.screen.core.SourceBlock
import com.livetranslate.screen.core.TextTools
import com.livetranslate.screen.overlay.OverlayController
import com.livetranslate.screen.settings.LiveConfig
import com.livetranslate.screen.settings.LiveSettings
import com.livetranslate.screen.settings.LiveState
import com.livetranslate.screen.translate.TranslationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * خط الترجمة الحي المستمر:
 *
 *  أحداث إمكانية الوصول ─┐
 *  تغيّر بصمة الإطار ────┼─► جدولة (debounce + حد أقصى للانتظار) ─► دورة: استخراج ← ترجمة ← رسم
 *  تحريك/تكبير المربع ───┘
 *
 * يعمل بلا توقف عبر التطبيقات والصفحات حتى يوقفه المستخدم.
 */
class LivePipeline(
    private val scope: CoroutineScope,
    private val settings: LiveSettings,
    private val engine: TranslationEngine,
    private val ocr: OcrEngine,
    private val overlay: OverlayController,
    private val screenProvider: () -> Box,
    private val captureProvider: () -> ScreenCaptureEngine?,
    private val requestProjection: () -> Unit,
) {
    private var jobs = mutableListOf<Job>()
    private var timerJob: Job? = null
    private var passJob: Job? = null
    private var firstPendingAt = 0L
    private var lastSignature: Int? = null

    // اكتشاف تغيّر الإطار (أوضاع OCR)
    private var baseline: IntArray? = null
    private var previous: IntArray? = null
    private var graceTicks = 0

    @Volatile var paused = false
        private set

    private val cfg: LiveConfig get() = settings.config.value

    fun start() {
        stop()
        jobs += scope.launch {
            ScreenTextAccessibilityService.events.collect { change ->
                if (paused) return@collect
                when (change.kind) {
                    // تنقل أو تمرير: نخفي الترجمة القديمة فوراً كي لا تطفو فوق محتوى متحرك
                    UiChangeKind.NAVIGATION -> schedule(260, clear = true)
                    UiChangeKind.SCROLL -> schedule(220, clear = cfg.displayMode == DisplayMode.FULL_SCREEN || cfg.contentMode == ContentMode.TEXT)
                    UiChangeKind.CONTENT -> if (cfg.contentMode == ContentMode.TEXT) schedule(320)
                }
            }
        }
        jobs += scope.launch {
            settings.config
                .map { listOf(it.sourceLang, it.targetLang, it.secondaryLang, it.bidirectional, it.contentMode, it.displayMode, it.mangaRtl, it.ocrScript, it.excludeSystemUi) }
                .distinctUntilChanged()
                .drop(1)
                .collect { refresh(clear = true) }
        }
        jobs += scope.launch { frameWatcher() }
        jobs += scope.launch {
            // نبضة احتياطية للتطبيقات التي لا تبث أحداث تغيّر المحتوى
            while (isActive) {
                delay(2_000)
                if (!paused && cfg.contentMode == ContentMode.TEXT && passJob?.isActive != true) schedule(0)
            }
        }
        refresh(clear = true)
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        timerJob?.cancel(); passJob?.cancel()
    }

    fun setPaused(value: Boolean) {
        paused = value
        if (value) { timerJob?.cancel(); passJob?.cancel(); overlay.clear() } else refresh(clear = true)
    }

    fun refresh(clear: Boolean) {
        lastSignature = null
        baseline = null
        schedule(60, clear)
    }

    fun onBubbleChanged(finished: Boolean) {
        lastSignature = null
        baseline = null
        schedule(if (finished) 40 else 140, clear = cfg.contentMode != ContentMode.VIDEO)
    }

    /** جدولة دورة ترجمة مع دمج الأحداث المتلاحقة وحد أقصى للانتظار (800ms). */
    private fun schedule(delayMs: Long, clear: Boolean = false) {
        if (paused) return
        if (clear) { overlay.clear(); lastSignature = null }
        val now = SystemClock.uptimeMillis()
        if (timerJob?.isActive != true) firstPendingAt = now
        val fireAt = minOf(now + delayMs, firstPendingAt + MAX_WAIT_MS)
        timerJob?.cancel()
        timerJob = scope.launch {
            delay((fireAt - now).coerceAtLeast(0))
            passJob?.cancel() // الأحدث يفوز
            passJob = scope.launch { runCatching { runPass() } }
        }
    }

    private fun region(c: LiveConfig): Box {
        val screen = screenProvider()
        return if (c.displayMode == DisplayMode.BUBBLE) c.bubble.intersect(screen) ?: screen else screen
    }

    /** منطقة الالتقاط في وضع الفيديو بملء الشاشة: الثلث السفلي حيث الترجمات المرئية عادة. */
    private fun ocrZone(c: LiveConfig): Box {
        val r = region(c)
        return if (c.contentMode == ContentMode.VIDEO && c.displayMode == DisplayMode.FULL_SCREEN)
            Box(r.left, r.top + (r.height * 0.62f).toInt(), r.right, r.bottom) else r
    }

    private suspend fun runPass() {
        val c = cfg
        if (paused) return
        if (c.contentMode == ContentMode.TEXT) {
            val a11y = ScreenTextAccessibilityService.instance
            if (a11y == null) {
                status("فعّل خدمة إمكانية الوصول لقراءة النصوص")
                if (captureProvider() != null) ocrPass(c, ContentMode.TEXT)
                return
            }
            val area = region(c)
            val nodes = withContext(Dispatchers.Default) {
                a11y.snapshot(area, c.excludeSystemUi).filter { TextTools.isTranslatable(it.text) }
            }
            if (nodes.isEmpty() && c.ocrFallbackInTextMode && captureProvider() != null) {
                ocrPass(c, ContentMode.TEXT) // صور بلا نص قابل للقراءة ← OCR
            } else {
                renderTranslations(c, nodes, ContentMode.TEXT)
            }
        } else {
            ocrPass(c, c.contentMode)
        }
    }

    private suspend fun ocrPass(c: LiveConfig, mode: ContentMode) {
        val capture = captureProvider() ?: run {
            status("اسمح بالتقاط الشاشة لوضع الفيديو/المانغا")
            requestProjection()
            return
        }
        val zone = ocrZone(c)
        // وضع الفيديو يرسم الشريط خارج المنطقة ← لا حاجة لإخفاء الطبقات (بلا وميض)
        val hider = if (mode != ContentMode.VIDEO && (overlay.hasContent || c.displayMode == DisplayMode.FULL_SCREEN)) overlay else null
        val bitmap = capture.capture(zone, hider) ?: return
        val script = c.effectiveOcrScript
        val raw = try {
            ocr.recognize(bitmap, script, zone, sampleBackground = mode == ContentMode.MANGA)
        } finally {
            bitmap.recycle()
        }
        graceTicks = 2
        val blocks = raw.filter { TextTools.isTranslatable(it.text) }
        when (mode) {
            ContentMode.MANGA -> {
                val bubbles = BlockGrouper.group(blocks, c.mangaRtl, script.isCjk)
                renderTranslations(c, ReadingOrder.sort(bubbles, c.mangaRtl) { it.box }, mode)
            }
            ContentMode.VIDEO -> renderCaption(c, blocks, zone, script.isCjk)
            ContentMode.TEXT -> renderTranslations(c, BlockGrouper.group(blocks, false, script.isCjk, 0.5f), mode)
        }
    }

    private suspend fun renderCaption(c: LiveConfig, blocks: List<SourceBlock>, zone: Box, cjk: Boolean) {
        val ordered = ReadingOrder.sort(blocks, false) { it.box }
        val text = TextTools.joinLines(ordered.map { it.text }, cjk)
        val sig = text.hashCode()
        if (sig == lastSignature) return
        lastSignature = sig
        if (text.isBlank()) { overlay.clear(); return }
        val result = engine.translate(text, c, null)
        if (result == null) return
        val anchor = ordered.map { it.box }.reduceOrNull { a, b -> a.union(b) } ?: zone
        val captionAnchor = if (c.displayMode == DisplayMode.BUBBLE) region(c) else anchor
        overlay.render(emptyList(), Caption(result.text, TextTools.isRtlLanguage(result.route.target), captionAnchor), c.overlayOpacity)
        status(null)
    }

    private suspend fun renderTranslations(c: LiveConfig, sources: List<SourceBlock>, mode: ContentMode) {
        val sig = TextTools.signature(sources)
        if (sig == lastSignature) return
        lastSignature = sig
        if (sources.isEmpty()) {
            overlay.clear()
            LiveState.update { it.copy(lastFound = 0, lastTranslated = 0) }
            return
        }

        // لغة الصفحة الغالبة كتلميح للكتل القصيرة
        val sample = sources.sortedByDescending { it.text.length }.take(6).joinToString(" ") { it.text }.take(600)
        val pageHint = if (c.sourceLang == "auto") engine.detect(sample) else null

        val results = arrayOfNulls<RenderedBlock>(sources.size)
        fun build(i: Int, translated: String, target: String) = RenderedBlock(
            box = sources[i].box,
            text = translated,
            rtl = TextTools.isRtlLanguage(target),
            backgroundColor = sources[i].backgroundColor,
            order = if (mode == ContentMode.MANGA && c.showOrderBadges) i + 1 else null,
            centered = mode == ContentMode.MANGA,
        )
        // عرض فوري لما هو موجود في الذاكرة المؤقتة ثم إكمال الباقي على دفعات
        val pending = ArrayList<Int>()
        for (i in sources.indices) {
            val route = engine.resolveRoute(sources[i].text.trim(), c, pageHint)
            if (route == null) continue
            val hit = engine.peek(sources[i].text.trim(), route)
            if (hit != null) results[i] = build(i, hit, route.target) else pending += i
        }
        if (results.any { it != null }) overlay.render(results.filterNotNull(), null, c.overlayOpacity)
        for (chunk in pending.chunked(8)) {
            coroutineScope {
                chunk.map { i -> async { i to engine.translate(sources[i].text, c, pageHint) } }.awaitAll()
            }.forEach { (i, r) -> if (r != null) results[i] = build(i, r.text, r.route.target) }
            overlay.render(results.filterNotNull(), null, c.overlayOpacity)
        }
        if (results.all { it == null }) overlay.clear()
        val translated = results.count { it != null }
        LiveState.update {
            it.copy(
                lastFound = sources.size, lastTranslated = translated,
                lastError = if (translated > 0) null else it.lastError,
            )
        }
        status(null)
    }

    /** مراقبة الإطارات في أوضاع OCR: يكتشف تغيّر المحتوى (تمرير، صفحة جديدة، ترجمة مرئية جديدة). */
    private suspend fun frameWatcher() {
        while (scope.isActive) {
            val c = cfg
            val interval = when (c.contentMode) {
                ContentMode.VIDEO -> 280L
                ContentMode.MANGA -> 450L
                ContentMode.TEXT -> 700L
            }
            delay(interval)
            val ocrActive = c.contentMode.usesOcr || (c.ocrFallbackInTextMode && ScreenTextAccessibilityService.instance == null)
            val capture = captureProvider()
            if (paused || !ocrActive || capture == null) { baseline = null; continue }
            if (passJob?.isActive == true) continue
            val sig = capture.signature(ocrZone(c), overlay.occupiedRects()) ?: continue
            if (graceTicks > 0) { graceTicks--; baseline = sig; previous = sig; continue }
            val base = baseline
            if (base == null) { baseline = sig; previous = sig; continue }
            val changed = FrameSignature.difference(base, sig)
            val settling = FrameSignature.difference(previous, sig)
            previous = sig
            when {
                c.contentMode == ContentMode.VIDEO -> if (changed > 0.03f) { baseline = sig; schedule(0) }
                changed > 0.06f && settling < 0.02f -> { baseline = sig; schedule(0) } // استقر المحتوى الجديد
                changed > 0.06f -> { overlay.clear(); lastSignature = null } // ما زال يتحرك (تمرير) ← إخفاء الترجمات القديمة
            }
        }
    }

    private fun status(message: String?) {
        if (LiveState.status.value.message != message) LiveState.update { it.copy(message = message) }
    }

    companion object {
        private const val MAX_WAIT_MS = 800L
    }
}
