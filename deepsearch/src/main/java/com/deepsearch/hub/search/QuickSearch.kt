package com.deepsearch.hub.search

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deepsearch.hub.Prefs
import com.deepsearch.hub.R
import com.deepsearch.hub.dns.DohClient
import com.deepsearch.hub.util.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/** بحث عكسي فوري عن صورة من رابط (من المتصفح أو من نتائج بحث سابقة). */
object QuickSearch {

    fun fromImageUrl(activity: AppCompatActivity, url: String) {
        Toast.makeText(activity, R.string.fetching_image, Toast.LENGTH_SHORT).show()
        activity.lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { download(activity.cacheDir, url) }
                SearchResultsActivity.start(activity, file, Prefs.selectedEngines)
            } catch (e: Exception) {
                Toast.makeText(activity, activity.getString(R.string.error_x, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun download(cacheDir: File, url: String): File {
        val req = Request.Builder().url(url).build()
        val bytes = DohClient.httpClient().newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body!!.bytes()
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IOException("Not an image")
        val out = File(File(cacheDir, "work"), "remote_${System.currentTimeMillis()}.jpg")
        MediaUtils.saveJpeg(bmp, out)
        return out
    }
}
