package com.tvbrowser.aosp.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import com.tvbrowser.aosp.R
import com.tvbrowser.aosp.data.Bookmark
import com.tvbrowser.aosp.data.Bookmarks
import com.tvbrowser.aosp.util.Prefs

/** Add / edit / delete / reorder favourites. D-pad friendly AlertDialogs. */
class BookmarkDialogs(
    private val activity: Activity,
    private val bookmarks: Bookmarks,
    private val prefs: Prefs,
    private val onChanged: () -> Unit,
) {
    private fun builder() = AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)

    /** [index] = null → add new. */
    fun edit(index: Int?, prefill: Bookmark? = null) {
        val existing = index?.let { bookmarks.all().getOrNull(it) } ?: prefill
        val title = EditText(activity).apply {
            hint = activity.getString(R.string.bm_title_hint)
            setSingleLine()
            setText(existing?.title.orEmpty())
        }
        val url = EditText(activity).apply {
            hint = "https://"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(existing?.url.orEmpty())
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(title)
            addView(url)
        }
        builder()
            .setTitle(if (index == null) R.string.bm_add else R.string.bm_edit)
            .setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                val u = prefs.resolveInput(url.text.toString())
                if (u == Prefs.QUICK_ACCESS_URL) return@setPositiveButton
                val b = Bookmark(title.text.toString().ifBlank { hostOf(u) }, u)
                if (index == null) bookmarks.add(b) else bookmarks.update(index, b)
                onChanged()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Options for one favourite (from the Quick Access tile or the manager). */
    fun options(index: Int, open: (String) -> Unit) {
        val b = bookmarks.all().getOrNull(index) ?: return
        val items = arrayOf(
            activity.getString(R.string.bm_open),
            activity.getString(R.string.bm_edit),
            activity.getString(R.string.bm_move_up),
            activity.getString(R.string.bm_move_down),
            activity.getString(R.string.bm_delete),
        )
        builder().setTitle(b.title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> open(b.url)
                    1 -> edit(index)
                    2 -> { bookmarks.move(index, -1); onChanged() }
                    3 -> { bookmarks.move(index, 1); onChanged() }
                    4 -> confirmDelete(index)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun confirmDelete(index: Int) {
        val b = bookmarks.all().getOrNull(index) ?: return
        builder().setMessage(activity.getString(R.string.bm_delete_confirm, b.title))
            .setPositiveButton(R.string.bm_delete) { _, _ -> bookmarks.remove(index); onChanged() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Full manager list (used from Settings). */
    fun manage(open: (String) -> Unit) {
        val list = bookmarks.all()
        val labels = list.map { "★  ${it.title}" }.toMutableList()
        labels += activity.getString(R.string.bm_add_item)
        labels += activity.getString(R.string.bm_reset)
        builder().setTitle(R.string.bm_manage)
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which < list.size -> options(which, open)
                    which == list.size -> edit(null)
                    else -> { bookmarks.reset(); onChanged() }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        fun hostOf(url: String): String =
            runCatching { android.net.Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url
    }
}
