package com.livetranslate.screen.capture

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import com.livetranslate.screen.core.Box
import com.livetranslate.screen.core.FrameSignature
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** إخفاء/إظهار طبقاتنا العائمة لحظة الالتقاط كي لا يقرأ OCR ترجماتنا نفسها. */
interface OverlayHider {
    suspend fun hideForCapture()
    suspend fun restoreAfterCapture()
}

/**
 * وحدة الالتقاط المباشر (MediaProjection): تدفق مستمر لإطارات الشاشة إلى ImageReader.
 * يُحتفظ دائماً بآخر إطار فقط، وتُحسب منه بصمة خفيفة لاكتشاف التغيير،
 * ولا يُنسخ إلى Bitmap إلا عند الحاجة الفعلية إلى OCR.
 */
class ScreenCaptureEngine(
    private val projection: MediaProjection,
    initialSize: ScreenSize,
    private val onStopped: () -> Unit,
) {
    private val thread = HandlerThread("live-capture").apply { start() }
    private val handler = Handler(thread.looper)
    private val dispatcher = handler.asCoroutineDispatcher()

    @Volatile var size: ScreenSize = initialSize
        private set
    @Volatile private var frameSeq = 0L
    private var reader: ImageReader
    private var latest: Image? = null
    private var display: VirtualDisplay? = null
    private var scratch: Bitmap? = null
    @Volatile private var released = false

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            if (!released) onStopped()
        }
    }

    init {
        // يجب تسجيل الـ Callback قبل createVirtualDisplay (Android 14+)
        projection.registerCallback(callback, handler)
        reader = newReader(initialSize)
        display = projection.createVirtualDisplay(
            "live-translate", initialSize.width, initialSize.height, initialSize.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, handler,
        )
    }

    private fun newReader(s: ScreenSize): ImageReader =
        ImageReader.newInstance(s.width, s.height, PixelFormat.RGBA_8888, 3).apply {
            setOnImageAvailableListener({ r ->
                val img = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                latest?.close()
                latest = img
                frameSeq++
            }, handler)
        }

    /** عند تدوير الشاشة: إعادة ضبط أبعاد العرض الافتراضي. */
    fun resize(s: ScreenSize) {
        handler.post {
            if (released || s == size) return@post
            size = s
            val old = reader
            latest?.close(); latest = null
            reader = newReader(s)
            display?.resize(s.width, s.height, s.densityDpi)
            display?.surface = reader.surface
            old.close()
            scratch?.recycle(); scratch = null
        }
    }

    /** بصمة منخفضة الدقة للمنطقة المحددة مع استبعاد مستطيلات طبقاتنا. */
    suspend fun signature(zone: Box, mask: List<Box>): IntArray? = withContext(dispatcher) {
        val img = latest ?: return@withContext null
        val z = zone.intersect(size.bounds) ?: return@withContext null
        val plane = runCatching { img.planes[0] }.getOrNull() ?: return@withContext null
        val buf = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val limit = buf.limit()
        val cols = 24
        val rows = (24f * z.height / z.width.coerceAtLeast(1)).toInt().coerceIn(6, 48)
        FrameSignature.compute(z, cols, rows, mask) { x, y ->
            val off = y * rowStride + x * pixelStride
            if (off + 2 >= limit) 0 else FrameSignature.luminance(
                buf.get(off).toInt() and 0xff, buf.get(off + 1).toInt() and 0xff, buf.get(off + 2).toInt() and 0xff,
            )
        }
    }

    /**
     * التقاط المنطقة كصورة. إن مُرّر [hider] تُخفى طبقاتنا وننتظر إطاراً جديداً نظيفاً.
     */
    suspend fun capture(zone: Box, hider: OverlayHider?): Bitmap? {
        if (hider == null) return withContext(dispatcher) { copyLatest(zone) }
        val before = frameSeq
        hider.hideForCapture()
        try {
            withTimeoutOrNull(250) { while (frameSeq <= before) delay(8) }
            delay(34) // إطاران إضافيان لضمان اكتمال تركيب الشاشة بدون طبقاتنا
            return withContext(dispatcher) { copyLatest(zone) }
        } finally {
            withContext(NonCancellable) { hider.restoreAfterCapture() }
        }
    }

    @SuppressLint("UseKtx")
    private fun copyLatest(zone: Box): Bitmap? {
        val img = latest ?: return null
        val z = zone.intersect(size.bounds) ?: return null
        val plane = img.planes[0]
        val pixelStride = plane.pixelStride
        val rowWidth = plane.rowStride / pixelStride
        val h = img.height
        val full = scratch?.takeIf { it.width == rowWidth && it.height == h && !it.isRecycled }
            ?: Bitmap.createBitmap(rowWidth, h, Bitmap.Config.ARGB_8888).also { scratch?.recycle(); scratch = it }
        val buf = plane.buffer
        buf.rewind()
        full.copyPixelsFromBuffer(buf)
        val right = minOf(z.right, img.width)
        val bottom = minOf(z.bottom, h)
        if (right <= z.left || bottom <= z.top) return null
        return Bitmap.createBitmap(full, z.left, z.top, right - z.left, bottom - z.top)
    }

    fun release() {
        if (released) return
        released = true
        handler.post {
            runCatching { display?.release() }
            display = null
            latest?.close(); latest = null
            runCatching { reader.close() }
            scratch?.recycle(); scratch = null
            runCatching { projection.unregisterCallback(callback) }
            runCatching { projection.stop() }
            thread.quitSafely()
        }
    }
}
