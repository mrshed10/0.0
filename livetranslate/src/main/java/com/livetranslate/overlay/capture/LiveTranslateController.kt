package com.livetranslate.overlay.capture

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
 *  WAITING ──(لا حركة ‎180ms)──▶ PROCESSING ──(كل كتلة تظهر فور ترجمتها)──▶ SHOWING
 *     ▲                               │                                      │
 *     └────────── تحرّك محتوى الشاشة خارج مناطق الترجمة ◀──────────────────────┘
 *
 * - الحركة تُكتشف بمقارنة بصمة كل إطار ببصمة الإطار الذي تُرجم، مع تجاهل المناطق المغطاة
 *   بترجمتنا؛ فالنقر العادي لا يُخفي الترجمة، والتمرير يخفيها خلال إطار واحد.
 * - أثناء WAITING تكون الترجمة مخفية فلا تظهر في اللقطة (لا حلقة تغذية راجعة).
 * - إن عادت الشاشة لمحتوى سبق ترجمته تُعرض النتيجة المحفوظة فوراً دون OCR.
 */
class LiveTranslateController(
    private val scope: CoroutineScope,
    private val capturer: ScreenCapturer,
    private val engine: TranslationEngine,
    private val stage: StageView,
    private val languages: () -> Pair<String, String>,
    private val excludedRects: () -> List<android.graphics.Rect>,
    private val onStatus: (EngineStatus) -> Unit,
) {
    private enum class State { PAUSED, WAITING, PROCESSING, SHOWING }

    private val main = Handler(Looper.getMainLooper())
    private var state = State.PAUSED

    /** آخر بصمة وصلت ووقت آخر تغيّر فعلي في الشاشة. */
    private var lastSignature: FrameSignature? = null
    private var lastChangeAt = 0L

    /** بصمة الإطار الذي تُعرض ترجمته الآن (أو تجري معالجته). */
    private var shownSignature: FrameSignature? = null
    private var grabRequestedAt = 0L

    /** ذاكرة صغيرة لآخر الشاشات المترجمة (للرجوع السريع أثناء التمرير ذهاباً وإياباً). */
    private val history = ArrayDeque<Pair<FrameSignature, List<TextPatch>>>()
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
        stage.nudge() // نحتاج إطاراً أول حتى لو كانت الشاشة ساكنة
    }

    val isPaused get() = state == State.PAUSED

    /** إيقاف مؤقت من الزر العائم: تُخفى الترجمة ويتوقف العمل، والتقاط الشاشة يبقى جاهزاً. */
    fun pause() {
        job?.cancel()
        job = null
        state = State.PAUSED
        shownSignature = null
        stage.setPatchesVisible(false)
        onStatus(EngineStatus.Ready)
    }

    fun resume() {
        if (state != State.PAUSED) return
        enterWaiting()
        stage.nudge()
    }

    fun stop() {
        main.removeCallbacks(ticker)
        job?.cancel()
        state = State.PAUSED
        stage.clearPatches()
    }

    /** تغيّرت اللغات أو اتجاه الشاشة: النتائج المحفوظة لم تعد صالحة. */
    fun invalidate() {
        history.clear()
        if (state != State.PAUSED) {
            enterWaiting()
            stage.nudge()
        }
    }

    fun onSignature(signature: FrameSignature) {
        val now = SystemClock.uptimeMillis()
        if (signature.differsFrom(lastSignature)) lastChangeAt = now
        lastSignature = signature

        if (state == State.PROCESSING || state == State.SHOWING) {
            val reference = shownSignature ?: return
            val mask = FrameSignature.maskFor(signature.width, signature.height, stage.visibleBoxes())
            if (signature.differsFrom(reference, mask)) {
                // المحتوى تحرّك (تمرير/صفحة جديدة/فيديو): نخفي فوراً ونعيد عند الاستقرار
                enterWaiting()
            }
        }
    }

    private fun tick() {
        val now = SystemClock.uptimeMillis()
        when (state) {
            State.WAITING -> {
                if (lastSignature == null) {
                    stage.nudge()
                } else if (now - lastChangeAt >= STABLE_MS) {
                    onStable()
                }
            }
            State.PROCESSING -> {
                // لم تصل اللقطة (الشاشة ساكنة ولا ينتج النظام إطارات): نجبره على إطار
                if (shownSignature == null && now - grabRequestedAt > NUDGE_AFTER_MS) {
                    grabRequestedAt = now
                    stage.nudge()
                }
            }
            else -> Unit
        }
    }

    private fun enterWaiting() {
        job?.cancel()
        job = null
        state = State.WAITING
        shownSignature = null
        stage.setPatchesVisible(false)
        lastChangeAt = SystemClock.uptimeMillis()
    }

    private fun onStable() {
        val current = lastSignature
        val cached = history.firstOrNull { current != null && !current.differsFrom(it.first) }
        if (cached != null) {
            shownSignature = cached.first
            show(cached.second)
            return
        }
        state = State.PROCESSING
        shownSignature = null
        grabRequestedAt = SystemClock.uptimeMillis()
        capturer.requestGrab { bitmap, signature -> onGrab(bitmap, signature) }
        stage.nudge() // يضمن وصول إطار فوراً حتى لو كانت الشاشة ساكنة
    }

    private fun onGrab(bitmap: android.graphics.Bitmap, signature: FrameSignature) {
        if (state != State.PROCESSING || shownSignature != null) {
            bitmap.recycle()
            return
        }
        shownSignature = signature
        onStatus(EngineStatus.Working)
        job = scope.launch {
            val (source, target) = languages()
            val patches = try {
                engine.process(bitmap, source, target, excludedRects()) { partial ->
                    // عرض تدريجي: كل كتلة تظهر بمجرد ترجمتها
                    if (state == State.PROCESSING) {
                        stage.setPatches(partial)
                        stage.setPatchesVisible(true)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onStatus(EngineStatus.Error(e.localizedMessage ?: e.javaClass.simpleName))
                emptyList()
            } finally {
                bitmap.recycle()
            }
            job = null
            if (state != State.PROCESSING) return@launch
            remember(signature, patches)
            show(patches)
        }
    }

    private fun remember(signature: FrameSignature, patches: List<TextPatch>) {
        history.addFirst(signature to patches)
        while (history.size > HISTORY_SIZE) history.removeLast()
    }

    private fun show(patches: List<TextPatch>) {
        stage.setPatches(patches)
        stage.setPatchesVisible(true)
        state = State.SHOWING
        onStatus(EngineStatus.Ready)
    }

    private companion object {
        const val TICK_MS = 30L
        const val STABLE_MS = 180L
        const val NUDGE_AFTER_MS = 120L
        const val HISTORY_SIZE = 12
    }
}
