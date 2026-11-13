package com.livetranslate.overlay.translate

import android.graphics.Bitmap
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.Executors

/**
 * قارئ Tesseract لمنطقة محددة من الشاشة. TessBaseAPI غير آمن للخيوط المتعددة،
 * لذا يعمل كل شيء على خيط واحد مخصص. القراءة تتم على مناطق النص فقط (التي وجدها ML Kit)
 * وليس على الشاشة كاملة، فتبقى سريعة.
 */
class TessReader : Closeable {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private var api: TessBaseAPI? = null
    private var loaded: String? = null

    /** @param languages مثل "rus" أو "rus+ara". يُرجع الأسطر بإحداثيات الشاشة، أو null إن تعذّر. */
    suspend fun read(bitmap: Bitmap, region: Rect, languages: String): List<OcrLine>? = withContext(dispatcher) {
        val tess = ensure(languages) ?: return@withContext null
        val rect = Rect(region).apply { intersect(0, 0, bitmap.width, bitmap.height) }
        if (rect.width() < 8 || rect.height() < 8) return@withContext null

        // Tesseract يقرأ أفضل عندما يكون ارتفاع الحرف ~30-40px
        val scale = (40f / rect.height().coerceAtMost(40).coerceAtLeast(1)).coerceIn(1f, 3f)
        val crop = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
        val input = if (scale > 1.05f) {
            Bitmap.createScaledBitmap(crop, (rect.width() * scale).toInt(), (rect.height() * scale).toInt(), true)
                .also { crop.recycle() }
        } else crop

        try {
            tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
            tess.setImage(input)
            tess.getUTF8Text() // يشغّل القراءة
            val lines = mutableListOf<OcrLine>()
            val it = tess.resultIterator ?: return@withContext null
            try {
                it.begin()
                val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
                do {
                    val text = it.getUTF8Text(level)?.trim().orEmpty()
                    val box = it.getBoundingRect(level)
                    if (text.isNotEmpty() && box != null) {
                        lines += OcrLine(
                            text = text,
                            left = rect.left + box.left / scale,
                            top = rect.top + box.top / scale,
                            right = rect.left + box.right / scale,
                            bottom = rect.top + box.bottom / scale,
                            confidence = it.confidence(level) / 100f,
                        )
                    }
                } while (it.next(level))
            } finally {
                it.delete()
            }
            lines
        } catch (e: Exception) {
            null
        } finally {
            tess.clear()
            input.recycle()
        }
    }

    private fun ensure(languages: String): TessBaseAPI? {
        if (loaded == languages) return api
        api?.recycle()
        api = null
        loaded = null
        val fresh = TessBaseAPI()
        val ok = runCatching { fresh.init(OcrPackStore.dataPath, languages, TessBaseAPI.OEM_LSTM_ONLY) }.getOrDefault(false)
        if (!ok) {
            runCatching { fresh.recycle() }
            return null
        }
        api = fresh
        loaded = languages
        return fresh
    }

    override fun close() {
        executor.execute {
            api?.recycle()
            api = null
        }
        executor.shutdown()
    }
}
