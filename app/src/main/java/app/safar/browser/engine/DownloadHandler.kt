package app.safar.browser.engine

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import app.safar.browser.data.BrowserSettings
import org.mozilla.geckoview.WebResponse

/** التنزيل عبر DownloadManager للنظام أو تسليم الرابط لمدير تحميل خارجي (ADM, 1DM, ...). */
class DownloadHandler(private val context: Context) {

    fun handle(response: WebResponse, settings: BrowserSettings, privateTab: Boolean) {
        val url = response.uri
        val disposition = response.headers["Content-Disposition"] ?: response.headers["content-disposition"]
        val mime = response.headers["Content-Type"] ?: response.headers["content-type"]
        runCatching { response.body?.close() } // لا نحتاج جسم Gecko؛ سنعيد الطلب

        if (url.startsWith("blob:") || url.startsWith("data:")) {
            Toast.makeText(context, "روابط blob/data غير مدعومة في هذه المرحلة", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = URLUtil.guessFileName(url, disposition, mime)

        if (settings.externalDownloader) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (settings.externalDownloaderPackage.isNotBlank()) intent.setPackage(settings.externalDownloaderPackage)
            val launched = runCatching { context.startActivity(Intent.createChooser(intent, "تنزيل باستخدام").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (launched) return
        }

        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setMimeType(mime)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(
                if (settings.downloadNotifications) DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                else DownloadManager.Request.VISIBILITY_HIDDEN
            )
        if (privateTab) req.setVisibleInDownloadsUi(false)
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        Toast.makeText(context, "بدأ التنزيل: $fileName", Toast.LENGTH_SHORT).show()
    }
}
