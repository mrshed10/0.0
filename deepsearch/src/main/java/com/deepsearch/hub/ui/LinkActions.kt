package com.deepsearch.hub.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.deepsearch.hub.R
import com.deepsearch.hub.browser.Downloader
import com.deepsearch.hub.search.QuickSearch
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** قائمة إجراءات موحّدة لأي رابط: فتح، تحميل، نسخ، مشاركة، بحث عكسي عن الصورة. */
object LinkActions {

    fun show(activity: AppCompatActivity, url: String, isImage: Boolean) {
        val actions = mutableListOf<Pair<Int, () -> Unit>>(
            R.string.act_open_hub to { MainActivity.openUrl(activity, url) },
            R.string.act_download to { Downloader.enqueue(activity, url) },
            R.string.act_copy to { copy(activity, url) },
            R.string.act_share to {
                activity.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), null)
                )
            },
        )
        if (isImage) actions.add(1, R.string.act_reverse_search to { QuickSearch.fromImageUrl(activity, url) })

        MaterialAlertDialogBuilder(activity)
            .setTitle(url.take(200))
            .setItems(actions.map { activity.getString(it.first) }.toTypedArray()) { _, i -> actions[i].second() }
            .show()
    }

    /** ضغطة مطوّلة على صورة أو رابط داخل أي WebView. */
    fun installLongPress(webView: WebView, activity: AppCompatActivity) {
        webView.setOnLongClickListener {
            val r = webView.hitTestResult
            val extra = r.extra ?: return@setOnLongClickListener false
            when (r.type) {
                WebView.HitTestResult.IMAGE_TYPE,
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> { show(activity, extra, isImage = true); true }
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> { show(activity, extra, isImage = false); true }
                else -> false
            }
        }
    }

    fun copy(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("link", text))
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
    }
}
