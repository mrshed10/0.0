package app.safar.browser.translate.ocr

import android.content.Context
import android.graphics.Bitmap
import app.safar.browser.translate.providers.Http
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Tesseract: يغطي ما لا يدعمه ML Kit (العربية، الفارسية، الروسية، العبرية، ...).
 * يُنزَّل ملف traineddata (نسخة fast) عند أول استخدام لكل لغة.
 */
class TesseractOcr(private val context: Context) : OcrEngine {
    private val mutex = Mutex()
    private val root = File(context.filesDir, "tesseract")
    private val tessdata = File(root, "tessdata")

    override suspend fun recognize(bitmap: Bitmap, langHint: String): List<OcrBlock> = mutex.withLock {
        withContext(Dispatchers.Default) {
            val lang = tessLang(langHint)
            ensureData(lang)
            val api = TessBaseAPI()
            try {
                check(api.init(root.absolutePath, lang)) { "Tesseract init failed for $lang" }
                api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                api.setImage(bitmap)
                api.getUTF8Text() // يشغّل التعرف
                val out = mutableListOf<OcrBlock>()
                val it = api.resultIterator ?: return@withContext emptyList()
                it.begin()
                do {
                    val level = TessBaseAPI.PageIteratorLevel.RIL_BLOCK
                    val text = it.getUTF8Text(level)?.trim().orEmpty()
                    val r = it.getBoundingRect(level)
                    if (text.isNotEmpty()) {
                        out += OcrBlock(text, Box(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()), text.lines())
                    }
                } while (it.next(level))
                it.delete()
                out
            } finally {
                api.recycle()
            }
        }
    }

    private suspend fun ensureData(lang: String) = withContext(Dispatchers.IO) {
        lang.split('+').forEach { l ->
            val f = File(tessdata, "$l.traineddata")
            if (f.exists() && f.length() > 0) return@forEach
            tessdata.mkdirs()
            val req = Request.Builder().url("https://github.com/tesseract-ocr/tessdata_fast/raw/main/$l.traineddata").build()
            Http.client.newCall(req).execute().use { res ->
                check(res.isSuccessful) { "Failed to download $l.traineddata: ${res.code}" }
                val tmp = File(tessdata, "$l.part")
                tmp.outputStream().use { o -> res.body!!.byteStream().copyTo(o) }
                tmp.renameTo(f)
            }
        }
    }

    companion object {
        fun tessLang(code: String) = when (code) {
            "ar" -> "ara"; "fa" -> "fas"; "ur" -> "urd"; "he" -> "heb"; "ru" -> "rus"; "uk" -> "ukr"
            "th" -> "tha"; "el" -> "ell"; "ja" -> "jpn+jpn_vert"; "zh" -> "chi_sim"; "ko" -> "kor"
            "auto" -> "eng"
            else -> "eng"
        }
    }
}
