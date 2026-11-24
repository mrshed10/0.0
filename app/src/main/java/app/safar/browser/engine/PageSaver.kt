package app.safar.browser.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoSession
import java.io.File

/** حفظ الصفحة الحالية محلياً كملف PDF في مجلد التنزيلات (Download/Safar) للرجوع إليها أو مشاركتها. */
object PageSaver {
    suspend fun saveAsPdf(context: Context, session: GeckoSession, title: String): Uri {
        val stream = session.saveAsPdf().await() ?: throw IllegalStateException("تعذر إنشاء PDF") // GV-API
        val name = sanitize(title.ifBlank { "page" }) + ".pdf"
        return withContext(Dispatchers.IO) {
            stream.use { input ->
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Safar")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("تعذر إنشاء الملف")
                    context.contentResolver.openOutputStream(uri)!!.use { input.copyTo(it) }
                    uri
                } else {
                    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Safar").apply { mkdirs() }
                    val f = File(dir, name)
                    f.outputStream().use { input.copyTo(it) }
                    Uri.fromFile(f)
                }
            }
        }
    }

    fun sanitize(s: String) = s.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), "_").take(80).trim()
}
