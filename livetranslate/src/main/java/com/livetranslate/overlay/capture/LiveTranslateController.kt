package com.livetranslate.overlay.capture

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.livetranslate.overlay.overlay.EngineStatus
import com.livetranslate.overlay.overlay.StageView
import com.livetranslate.overlay.translate.TextPatch
import com.livetranslate.overlay.translate.TranslationEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * منطق "مرّر وترجم" المستمر:
 *
 *  WAITING ──(الشاشة مستقرة ‎350ms)──▶ PROCESSING ──(OCR+ترجمة)──▶ SHOWING
 *     ▲                                    │                          │
 *     └──────── لمس/تمرير أو تغيّر محتوى الشاشة ◀──────────────────────┘
 *
 * أثناء WAITING تكون الترجمة مخفية، فلا تظهر في الإطار الملتقط (لا حلقة تغذية راجعة).
 * إذا عادت الشاشة لنفس المحتوى السابق تُعرض الترجمة المحفوظة فوراً دون OCR.
 */
class LiveTranslateController(
    private val scope: CoroutineScope,
    private val capturer: ScreenCapturer,
    private val engine: TranslationEngine,
    private val stage: StageView,
    private val languages: () -> Pair<String, String>,
    private val excludedRects: () -> List<Rect>,
    private val onStatus: (EngineStatus) -> Unit,
) {
    private enum class State { PAUSED, WAITING, PROCESSING, SHOWING }

    private val main = Handler(Looper.getMainLooper())
    private var state = State.PAUSED

    private var lastSignature: FrameSignature? = null
    private var stableSince = 0L

    private var shownAt = 0L
    private var shownBaseline: FrameSignature? = null

    private var processedSignature: FrameSignature? = null
    private var processedPatches: List<TextPatch>? = null
    private var grabRequestedAt = 0L
    private var dirtyDuringProcessing = false
    private var job: Job? = null

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            main.postDelayed(this, TICK_MS)
        }
    }

    fun start() {
        main.removeCallbacks(ticker)
        main.post(ticker)
        enterWaiting()
    }

    /** إيقاف مؤقت (مثلاً أثناء فتح بطاقة اختيار اللغة). */
    fun pause() {
        job?.cancel()
        state = State.PAUSED
        stage.setPatchesVisible(false)
    }

    /** استئناف؛ إن تغيّرت اللغات تُمسح النتائج المحفوظة. */
    fun resume(languagesChanged: Boolean) {
        if (languagesChanged) {
            processedSignature = null
            processedPatches = null
        }
        enterWaiting()
    }

    fun stop() {
        main.removeCallbacks(ticker)
        job?.cancel()
        state = State.PAUSED
        stage.clearPatches()
    }

    /** المستخدم لمس الشاشة خارج الفقاعة (بداية تمرير غالباً). */
    fun onOutsideTouch() {
        when (state) {
            State.SHOWING -> enterWaiting()
            State.PROCESSING -> dirtyDuringProcessing = true
            State.WAITING -> {
                lastSignature = null
                stableSince = SystemClock.uptimeMillis()
            }
            State.PAUSED -> Unit
        }
    }

    /** النافذة العائمة تحركت أو تغيّر حجم الشاشة: النتائج القديمة لم تعد صالحة. */
    fun invalidate() {
        processedSignature = null
        processedPatches = null
        if (state != State.PAUSED) enterWaiting()
    }

    fun onSignature(signature: FrameSignature) {
        val now = SystemClock.uptimeMillis()
        when (state) {
            State.WAITING -> {
                if (signature.differsFrom(lastSignature)) {
                    lastSignature = signature
                    stableSince = now
                }
            }
            State.SHOWING -> {
                if (now - shownAt < SHOW_SETTLE_MS) return
                val baseline = shownBaseline
                if (baseline == null) {
                    shownBaseline = signature
                } else if (signature.differsFrom(baseline)) {
                    // المحتوى تغيّر دون لمس (فيديو، دوّار صور، تمرير بلوحة المفاتيح...)
                    enterWaiting()
                }
            }
            else -> Unit
        }
    }

    private fun tick() {
        val now = SystemClock.uptimeMillis()
        when (state) {
            State.WAITING -> {
                if (lastSignature != null && now - stableSince >= STABLE_MS) {
                    onStable()
                } else if (now - capturer.lastFrameAt > IDLE_FRAME_MS) {
                    // الشاشة ساكنة تماماً ولا تصل إطارات (الإطار المضيء غير متحرك): نطلب إطاراً
                    stage.nudge()
                }
            }
            State.PROCESSING -> {
                if (job == null && now - grabRequestedAt > IDLE_FRAME_MS) stage.nudge()
            }
            else -> Unit
        }
    }

    private fun enterWaiting() {
        job?.cancel()
        job = null
        state = State.WAITING
        stage.setPatchesVisible(false)
        lastSignature = null
        stableSince = SystemClock.uptimeMillis()
    }

    private fun onStable() {
        val cached = processedPatches
        if (cached != null && lastSignature?.differsFrom(processedSignature) == false) {
            show(cached)
            return
        }
        state = State.PROCESSING
        dirtyDuringProcessing = false
        grabRequestedAt = SystemClock.uptimeMillis()
        capturer.requestGrab { bitmap, signature ->
            if (state != State.PROCESSING) {
                bitmap.recycle()
                return@requestGrab
            }
            job = scope.launch {
                val (source, target) = languages()
                val patches = try {
                    engine.process(bitmap, source, target, excludedRects(), onStatus)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onStatus(EngineStatus.Error(e.localizedMessage ?: e.javaClass.simpleName))
                    null
                } finally {
                    bitmap.recycle()
                }
                job = null
                if (state != State.PROCESSING) return@launch
                if (patches == null) {
                    // خطأ: ننتظر تغيّر الشاشة قبل المحاولة مجدداً
                    processedSignature = signature
                    processedPatches = emptyList()
                    show(emptyList(), keepStatus = true)
                    return@launch
                }
                if (dirtyDuringProcessing) {
                    enterWaiting()
                    return@launch
                }
                processedSignature = signature
                processedPatches = patches
                show(patches)
            }
        }
    }

    private fun show(patches: List<TextPatch>, keepStatus: Boolean = false) {
        stage.setPatches(patches)
        stage.setPatchesVisible(true)
        state = State.SHOWING
        shownAt = SystemClock.uptimeMillis()
        shownBaseline = null
        if (!keepStatus) onStatus(EngineStatus.Ready)
    }

    private companion object {
        const val TICK_MS = 120L
        const val STABLE_MS = 350L
        const val SHOW_SETTLE_MS = 300L
        const val IDLE_FRAME_MS = 400L
    }
}
