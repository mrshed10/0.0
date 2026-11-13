package com.livetranslate.overlay.translate

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * حزم قراءة Tesseract (ملفات traineddata) للغات التي لا يقرؤها ML Kit:
 * الروسية وبقية السيريلية، العربية/الفارسية/الأردية، العبرية، اليونانية، التايلاندية، واللغات الهندية الأخرى.
 * تُنزَّل مرة واحدة (نسخة tessdata_fast، 1–10 MB) وتُحفظ داخل التطبيق.
 */
object OcrPackStore {
    private const val BASE_URL = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/"

    private lateinit var root: File
    private val mutex = Mutex()

    private val _installed = MutableStateFlow<Set<String>>(emptySet())
    val installed: StateFlow<Set<String>> = _installed.asStateFlow()

    private val _downloading = MutableStateFlow<String?>(null)
    val downloading: StateFlow<String?> = _downloading.asStateFlow()

    fun init(context: Context) {
        if (::root.isInitialized) return
        root = File(context.filesDir, "tesseract")
        File(root, "tessdata").mkdirs()
        refresh()
    }

    /** المسار الذي يُمرَّر لـ TessBaseAPI.init (المجلد الأب لـ tessdata). */
    val dataPath: String get() = root.absolutePath

    fun isInstalled(tess: String) = tess in _installed.value

    private fun refresh() {
        _installed.value = File(root, "tessdata").listFiles()
            ?.filter { it.name.endsWith(".traineddata") && it.length() > 0 }
            ?.map { it.name.removeSuffix(".traineddata") }
            ?.toSet()
            ?: emptySet()
    }

    /** ينزّل الحزم الناقصة؛ يُرجع ما فشل تنزيله. */
    suspend fun ensure(packs: Collection<String>): List<String> = mutex.withLock {
        val failed = mutableListOf<String>()
        for (pack in packs.distinct()) {
            if (isInstalled(pack)) continue
            _downloading.value = pack
            try {
                download(pack)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += pack
            } finally {
                _downloading.value = null
            }
        }
        refresh()
        failed
    }

    private suspend fun download(pack: String) = withContext(Dispatchers.IO) {
        val target = File(root, "tessdata/$pack.traineddata")
        val partial = File(root, "tessdata/$pack.traineddata.part")
        val connection = URL("$BASE_URL$pack.traineddata").openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            connection.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
            check(partial.renameTo(target)) { "rename failed" }
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }
}
