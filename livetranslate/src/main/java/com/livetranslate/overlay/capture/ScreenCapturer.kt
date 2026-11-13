package com.livetranslate.overlay.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import com.livetranslate.overlay.overlay.ScreenMetrics

/**
 * يلتقط الشاشة عبر MediaProjection → VirtualDisplay → ImageReader.
 *
 * - كل إطار جديد يُحوَّل إلى [FrameSignature] رخيصة (بحد أقصى كل [SIGNATURE_INTERVAL_MS])
 *   لاكتشاف التمرير والاستقرار.
 * - عند الحاجة فقط يُحوَّل الإطار التالي إلى Bitmap كامل عبر [requestGrab] ليُقرأ نصه.
 *
 * كل العمل الثقيل يتم على خيط خلفي؛ النتائج تُعاد على الخيط الرئيسي.
 */
class ScreenCapturer(
    private val context: Context,
    private val projection: MediaProjection,
    private val onSignature: (FrameSignature) -> Unit,
    private val onProjectionStopped: () -> Unit,
) {
    private val thread = HandlerThread("live-translate-capture").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())

    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var lastSignatureAt = 0L

    /** منطقة الفقاعة العائمة (بإحداثيات الشاشة) لاستبعادها من البصمة. */
    @Volatile var excludeBox: Box? = null

    /** وقت آخر إطار وصل (uptime) لمعرفة هل الشاشة ساكنة تماماً. */
    @Volatile var lastFrameAt = 0L
        private set

    @Volatile private var pendingGrab: ((Bitmap, FrameSignature) -> Unit)? = null
    @Volatile private var released = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // المستخدم أوقف المشاركة من شريط الحالة أو النظام أنهى الجلسة
            main.post { onProjectionStopped() }
        }
    }

    fun start() {
        // Android 14+: يجب تسجيل الـ callback قبل createVirtualDisplay
        projection.registerCallback(projectionCallback, worker)
        val m = ScreenMetrics.of(context)
        val r = newReader(m.width, m.height)
        reader = r
        display = projection.createVirtualDisplay(
            "live-translate",
            m.width, m.height, m.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, worker,
        )
    }

    /** يُستدعى عند تدوير الشاشة أو تغيّر حجمها. لا يمكن إنشاء VirtualDisplay ثانٍ، لذا نعيد تحجيمه. */
    fun onDisplayChanged() {
        worker.post {
            if (released) return@post
            val m = ScreenMetrics.of(context)
            val old = reader
            val r = newReader(m.width, m.height)
            reader = r
            display?.resize(m.width, m.height, m.densityDpi)
            display?.surface = r.surface
            old?.close()
        }
    }

    /** يطلب تحويل الإطار التالي إلى Bitmap (يُستدعى الرد على الخيط الرئيسي). */
    fun requestGrab(callback: (Bitmap, FrameSignature) -> Unit) {
        pendingGrab = callback
    }

    fun release() {
        released = true
        pendingGrab = null
        worker.post {
            display?.release()
            display = null
            reader?.close()
            reader = null
            runCatching { projection.unregisterCallback(projectionCallback) }
            runCatching { projection.stop() }
            thread.quitSafely()
        }
    }

    private fun newReader(width: Int, height: Int): ImageReader =
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ onImageAvailable(it) }, worker)
        }

    private fun onImageAvailable(r: ImageReader) {
        val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return
        try {
            if (released) return
            val now = SystemClock.uptimeMillis()
            lastFrameAt = now

            val grab = pendingGrab
            if (grab != null) {
                pendingGrab = null
                val signature = signatureOf(image)
                val bitmap = toBitmap(image)
                main.post { grab(bitmap, signature) }
                return
            }

            if (now - lastSignatureAt < SIGNATURE_INTERVAL_MS) return
            lastSignatureAt = now
            val signature = signatureOf(image)
            main.post { onSignature(signature) }
        } finally {
            image.close()
        }
    }

    private fun signatureOf(image: Image): FrameSignature {
        val plane = image.planes[0]
        return FrameSignature.compute(
            plane.buffer, image.width, image.height, plane.rowStride, plane.pixelStride, excludeBox,
        )
    }

    private fun toBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * image.width
        val padded = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888,
        )
        plane.buffer.rewind()
        padded.copyPixelsFromBuffer(plane.buffer)
        if (rowPadding == 0) return padded
        return Bitmap.createBitmap(padded, 0, 0, image.width, image.height).also { padded.recycle() }
    }

    private companion object {
        const val SIGNATURE_INTERVAL_MS = 110L
    }
}
