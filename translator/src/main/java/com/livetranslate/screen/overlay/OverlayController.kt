package com.livetranslate.screen.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.livetranslate.screen.R
import com.livetranslate.screen.capture.OverlayHider
import com.livetranslate.screen.core.Box
import com.livetranslate.screen.core.Caption
import com.livetranslate.screen.core.ContentMode
import com.livetranslate.screen.core.DisplayMode
import com.livetranslate.screen.core.RenderedBlock
import com.livetranslate.screen.settings.LiveConfig
import com.livetranslate.screen.translate.LanguagePacks
import kotlin.math.abs

/**
 * يدير النوافذ العائمة:
 * 1) طبقة الرسم بملء الشاشة (لا تلمس) — الترجمات + إطار المربع الزجاجي.
 * 2) شريط التحكم (المبدّل السريع للأوضاع) — قابل للسحب، ويحرّك المربع معه.
 * 3) مقبض تغيير الحجم في زاوية المربع.
 *
 * يُفضّل استخدام نافذة إمكانية الوصول (TYPE_ACCESSIBILITY_OVERLAY) عند توفرها لأنها
 * موثوقة لدى النظام (لا تحجب اللمس، ولا تتطلب شفافية 80%)، وإلا TYPE_APPLICATION_OVERLAY.
 */
class OverlayController(private val callbacks: Callbacks) : OverlayHider {

    interface Callbacks {
        fun onBubbleChanged(box: Box, finished: Boolean)
        fun onContentModeSelected(mode: ContentMode)
        fun onTogglePause()
        fun onToggleDisplay()
        fun onSwapDirection()
        fun onClose()
    }

    private var ctx: Context? = null
    private var wm: WindowManager? = null
    private var windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    private var canvas: TranslationCanvasView? = null
    private var bar: LinearLayout? = null
    private var handle: View? = null
    private var barParams: WindowManager.LayoutParams? = null
    private var handleParams: WindowManager.LayoutParams? = null

    private var displayMode = DisplayMode.FULL_SCREEN
    private var bubble = Box(0, 0, 0, 0)
    private var screen = Box(0, 0, 0, 0)
    private var floatingBarPos = Pair(-1, -1)
    private var dp = 1f

    private val chips = mutableMapOf<ContentMode, TextView>()
    private var pauseIcon: ImageView? = null
    private var displayIcon: ImageView? = null
    private var pairLabel: TextView? = null

    val isAttached: Boolean get() = canvas != null

    /** يُنشئ النوافذ. [windowContext] سياق خدمة إمكانية الوصول (إن وُجد) أو الخدمة الأمامية. */
    fun attach(windowContext: Context, useAccessibilityOverlay: Boolean, screen: Box, mode: DisplayMode, bubble: Box) {
        detach()
        ctx = windowContext
        wm = windowContext.getSystemService(WindowManager::class.java)
        windowType = if (useAccessibilityOverlay) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        dp = windowContext.resources.displayMetrics.density
        this.screen = screen
        this.displayMode = mode
        this.bubble = bubble

        val c = TranslationCanvasView(windowContext)
        val cp = baseParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        )
        if (!useAccessibilityOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: النوافذ غير الموثوقة التي يمر اللمس عبرها يجب ألا تتجاوز شفافية 0.8
            cp.alpha = 0.8f
        }
        c.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> relayoutControls() }
        wm!!.addView(c, cp)
        canvas = c

        val b = buildBar(windowContext)
        val bp = baseParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, 0)
        wm!!.addView(b, bp)
        bar = b; barParams = bp

        val h = buildHandle(windowContext)
        val size = (30 * dp).toInt()
        val hp = baseParams(size, size, 0)
        wm!!.addView(h, hp)
        handle = h; handleParams = hp

        applyDisplayMode(mode, bubble)
    }

    private fun baseParams(w: Int, h: Int, extraFlags: Int) = WindowManager.LayoutParams(
        w, h, windowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or extraFlags,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    fun applyDisplayMode(mode: DisplayMode, bubble: Box) {
        displayMode = mode
        this.bubble = bubble
        canvas?.bubble = if (mode == DisplayMode.BUBBLE) bubble else null
        handle?.visibility = if (mode == DisplayMode.BUBBLE) View.VISIBLE else View.GONE
        displayIcon?.setImageResource(if (mode == DisplayMode.BUBBLE) R.drawable.ic_fullscreen else R.drawable.ic_bubble)
        relayoutControls()
    }

    fun onScreenChanged(screen: Box, bubble: Box) {
        this.screen = screen
        if (floatingBarPos.first >= 0) {
            floatingBarPos = Pair(floatingBarPos.first.coerceAtMost(screen.width - 100), floatingBarPos.second.coerceAtMost(screen.height - 100))
        }
        applyDisplayMode(displayMode, bubble)
    }

    fun updateControls(cfg: LiveConfig, paused: Boolean) {
        chips.forEach { (mode, view) -> styleChip(view, mode == cfg.contentMode) }
        pauseIcon?.setImageResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause)
        val src = if (cfg.sourceLang == "auto") "تلقائي" else short(cfg.sourceLang)
        pairLabel?.text = "$src ← ${short(cfg.targetLang)}"
    }

    private fun short(code: String): String = LanguagePacks.displayName(code).take(7)

    fun render(blocks: List<RenderedBlock>, caption: Caption?, opacity: Float) {
        canvas?.setContent(blocks, caption, opacity)
    }

    fun clear() { canvas?.clearContent() }

    val hasContent: Boolean get() = canvas?.hasContent == true

    /** المستطيلات التي نشغلها على الشاشة (لاستبعادها من بصمة الإطار). */
    fun occupiedRects(): List<Box> {
        val out = ArrayList<Box>()
        canvas?.let { out += it.occupiedRects() }
        windowBox(bar, barParams)?.let { out += it }
        if (displayMode == DisplayMode.BUBBLE) windowBox(handle, handleParams)?.let { out += it }
        return out
    }

    private fun windowBox(v: View?, p: WindowManager.LayoutParams?): Box? {
        if (v == null || p == null || v.width == 0) return null
        val (ox, oy) = canvas?.windowOrigin ?: (0 to 0)
        return Box(p.x + ox, p.y + oy, p.x + ox + v.width, p.y + oy + v.height)
    }

    override suspend fun hideForCapture() {
        canvas?.visibility = View.INVISIBLE
        bar?.alpha = 0f
        handle?.alpha = 0f
    }

    override suspend fun restoreAfterCapture() {
        canvas?.visibility = View.VISIBLE
        bar?.alpha = 1f
        handle?.alpha = 1f
    }

    fun detach() {
        val w = wm
        listOfNotNull(canvas, bar, handle).forEach { v -> runCatching { w?.removeViewImmediate(v) } }
        canvas = null; bar = null; handle = null; barParams = null; handleParams = null
        chips.clear(); pauseIcon = null; displayIcon = null; pairLabel = null
        wm = null; ctx = null
    }

    // ———————————————————— التخطيط ————————————————————

    private fun relayoutControls() {
        val w = wm ?: return
        val b = bar ?: return
        val bp = barParams ?: return
        val (ox, oy) = canvas?.windowOrigin ?: (0 to 0)
        b.measure(
            View.MeasureSpec.makeMeasureSpec(screen.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val bw = b.measuredWidth
        val bh = b.measuredHeight
        val gap = (6 * dp).toInt()
        if (displayMode == DisplayMode.BUBBLE) {
            val x = bubble.left.coerceIn(0, (screen.width - bw).coerceAtLeast(0))
            val above = bubble.top - bh - gap
            val y = if (above >= (24 * dp).toInt()) above else bubble.bottom + gap
            bp.x = x - ox; bp.y = y - oy
            handleParams?.let { hp ->
                hp.x = bubble.right - hp.width / 2 - ox
                hp.y = bubble.bottom - hp.height / 2 - oy
                handle?.let { runCatching { w.updateViewLayout(it, hp) } }
            }
        } else {
            if (floatingBarPos.first < 0) floatingBarPos = Pair((screen.width - bw) / 2, (screen.height * 0.08f).toInt())
            bp.x = floatingBarPos.first - ox; bp.y = floatingBarPos.second - oy
        }
        runCatching { w.updateViewLayout(b, bp) }
    }

    // ———————————————————— شريط التحكم ————————————————————

    @SuppressLint("ClickableViewAccessibility")
    private fun buildBar(c: Context): LinearLayout {
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            val pad = (5 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = 22 * dp
                setColor(0xE61B1F26.toInt())
                setStroke((1 * dp).toInt(), 0x40FFFFFF)
            }
            elevation = 8 * dp
        }
        val dragListener = DragListener()
        root.setOnTouchListener(dragListener)

        root.addView(icon(c, R.drawable.ic_drag).apply { setOnTouchListener(dragListener); contentDescription = "سحب" })
        listOf(
            ContentMode.TEXT to R.string.mode_text,
            ContentMode.VIDEO to R.string.mode_video,
            ContentMode.MANGA to R.string.mode_manga,
        ).forEach { (mode, label) ->
            val chip = TextView(c).apply {
                setText(label)
                textSize = 13f
                setTextColor(Color.WHITE)
                val h = (10 * dp).toInt(); val v = (5 * dp).toInt()
                setPadding(h, v, h, v)
                setOnClickListener { callbacks.onContentModeSelected(mode) }
            }
            styleChip(chip, false)
            chips[mode] = chip
            root.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = (3 * dp).toInt()
            })
        }
        pairLabel = TextView(c).apply {
            textSize = 12f
            setTextColor(0xFFB9C7E6.toInt())
            val h = (8 * dp).toInt()
            setPadding(h, 0, h, 0)
            setOnClickListener { callbacks.onSwapDirection() }
            contentDescription = "عكس اتجاه الترجمة"
        }
        root.addView(pairLabel)
        pauseIcon = icon(c, R.drawable.ic_pause).apply { setOnClickListener { callbacks.onTogglePause() }; contentDescription = "إيقاف مؤقت" }
        root.addView(pauseIcon)
        displayIcon = icon(c, R.drawable.ic_bubble).apply { setOnClickListener { callbacks.onToggleDisplay() }; contentDescription = "تبديل الوضع" }
        root.addView(displayIcon)
        root.addView(icon(c, R.drawable.ic_close).apply { setOnClickListener { callbacks.onClose() }; contentDescription = "إيقاف الترجمة" })
        return root
    }

    private fun icon(c: Context, res: Int) = ImageView(c).apply {
        setImageResource(res)
        val p = (6 * dp).toInt()
        setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams((34 * dp).toInt(), (34 * dp).toInt())
    }

    private fun styleChip(v: TextView, selected: Boolean) {
        v.background = GradientDrawable().apply {
            cornerRadius = 14 * dp
            setColor(if (selected) 0xFF2F6BFF.toInt() else 0x22FFFFFF)
        }
        v.alpha = if (selected) 1f else 0.85f
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildHandle(c: Context): View = View(c).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xF2FFFFFF.toInt())
            setStroke((3 * dp).toInt(), 0xFF2F6BFF.toInt())
        }
        contentDescription = "تغيير حجم المربع"
        setOnTouchListener(ResizeListener())
    }

    /** سحب الشريط: يحرّك المربع العائم (وضع المربع) أو الشريط وحده (وضع الشاشة الكاملة). */
    private inner class DragListener : View.OnTouchListener {
        private var startX = 0f; private var startY = 0f
        private var startBubble = bubble
        private var startBar = floatingBarPos
        private var moved = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX; startY = e.rawY; startBubble = bubble; moved = false
                    startBar = barScreenPos()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - startX).toInt(); val dy = (e.rawY - startY).toInt()
                    if (!moved && abs(dx) + abs(dy) < 6 * dp) return true
                    moved = true
                    if (displayMode == DisplayMode.BUBBLE) {
                        val nb = startBubble.offset(dx, dy).clampInto(screen, startBubble.width, startBubble.height)
                        setBubbleInternal(nb, false)
                    } else {
                        floatingBarPos = Pair(startBar.first + dx, startBar.second + dy)
                        relayoutControls()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (moved && displayMode == DisplayMode.BUBBLE) callbacks.onBubbleChanged(bubble, true)
                }
            }
            return true
        }
    }

    private fun barScreenPos(): Pair<Int, Int> {
        val bp = barParams ?: return floatingBarPos
        val (ox, oy) = canvas?.windowOrigin ?: (0 to 0)
        return Pair(bp.x + ox, bp.y + oy)
    }

    private inner class ResizeListener : View.OnTouchListener {
        private var startX = 0f; private var startY = 0f
        private var start = bubble
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = e.rawX; startY = e.rawY; start = bubble }
                MotionEvent.ACTION_MOVE -> {
                    val minW = (120 * dp).toInt(); val minH = (72 * dp).toInt()
                    val r = (start.right + (e.rawX - startX).toInt()).coerceIn(start.left + minW, screen.right)
                    val b = (start.bottom + (e.rawY - startY).toInt()).coerceIn(start.top + minH, screen.bottom)
                    setBubbleInternal(Box(start.left, start.top, r, b), false)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> callbacks.onBubbleChanged(bubble, true)
            }
            return true
        }
    }

    private fun setBubbleInternal(b: Box, finished: Boolean) {
        bubble = b
        canvas?.bubble = b
        relayoutControls()
        callbacks.onBubbleChanged(b, finished)
    }
}
