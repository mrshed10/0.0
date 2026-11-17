package com.tvbrowser.aosp.extensions

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.tvbrowser.aosp.R
import com.tvbrowser.aosp.TvBrowserApp
import com.tvbrowser.aosp.ui.PopupHost
import org.mozilla.geckoview.WebExtension

/** Installed extensions: enable/disable, settings (options page), popup, uninstall. */
class ExtensionsActivity : Activity() {

    companion object {
        const val EXTRA_OPEN_URL = "open_url"
    }

    private val app get() = application as TvBrowserApp
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private val adapter = Adapter()
    private var items: List<WebExtension> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extensions)
        list = findViewById(R.id.ext_list)
        empty = findViewById(R.id.ext_empty)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> actions(items[pos]) }
        findViewById<View>(R.id.ext_close).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        val ext = app.extensions
        ext.messageSink = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        ext.popupHost = { e -> PopupHost(this).open(e) }
        ext.tabOpener = { url -> openInBrowser(url) }
        ext.changeListener = { reload() }
        reload()
    }

    override fun onPause() {
        app.extensions.changeListener = null
        super.onPause()
    }

    private fun reload() {
        app.extensions.list { exts ->
            items = exts.sortedBy { it.metaData.name?.lowercase() }
            adapter.notifyDataSetChanged()
            empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            if (items.isNotEmpty() && !list.hasFocus()) list.requestFocus()
        }
    }

    private fun openInBrowser(url: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_OPEN_URL, url))
        finish()
    }

    private fun actions(ext: WebExtension) {
        val m = ext.metaData
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        if (app.extensions.hasPopup(ext) && m.enabled) {
            labels += getString(R.string.ext_popup); acts += { app.extensions.openPopup(ext) }
        }
        if (m.optionsPageUrl != null && m.enabled) {
            labels += getString(R.string.ext_options); acts += { app.extensions.openOptions(ext) }
        }
        labels += getString(if (m.enabled) R.string.ext_disable else R.string.ext_enable)
        acts += { app.extensions.setEnabled(ext, !m.enabled) { reload() } }
        labels += getString(R.string.ext_uninstall)
        acts += {
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setMessage(getString(R.string.ext_uninstall_confirm, m.name))
                .setPositiveButton(android.R.string.ok) { _, _ -> app.extensions.uninstall(ext) { reload() } }
                .setNegativeButton(android.R.string.cancel, null).show()
        }

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("${m.name} ${m.version}")
            .setItems(labels.toTypedArray()) { _, which -> acts[which]() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.item_extension, parent, false)
            val ext = items[position]
            val m = ext.metaData
            v.findViewById<TextView>(R.id.ext_name).text = m.name ?: ext.id
            v.findViewById<TextView>(R.id.ext_info).text = getString(
                R.string.ext_info, m.version,
                getString(if (m.enabled) R.string.ext_state_on else R.string.ext_state_off),
                m.description.orEmpty().take(120),
            )
            val icon = v.findViewById<ImageView>(R.id.ext_icon)
            icon.setImageResource(R.drawable.ic_extension)
            icon.tag = ext.id
            m.icon.getBitmap(96).accept({ bmp ->
                if (bmp != null && icon.tag == ext.id) icon.setImageBitmap(bmp)
            }, {})
            v.alpha = if (m.enabled) 1f else 0.55f
            return v
        }
    }
}
