package com.deepsearch.hub.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deepsearch.hub.R
import com.deepsearch.hub.browser.MediaLinkExtractor.Kind
import com.deepsearch.hub.browser.MediaLinkExtractor.Link
import com.deepsearch.hub.databinding.ItemLinkBinding
import com.deepsearch.hub.databinding.SheetLinksBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/** لوحة سفلية تعرض الروابط المستخرجة مع فلترة (فيديو/صور/صفحات). */
object LinkSheet {

    fun show(activity: AppCompatActivity, links: List<Link>) {
        if (links.isEmpty()) {
            Toast.makeText(activity, R.string.no_links, Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = BottomSheetDialog(activity)
        val b = SheetLinksBinding.inflate(activity.layoutInflater)
        val adapter = Adapter { LinkActions.show(activity, it.url, it.kind == Kind.IMAGE) }
        b.recycler.layoutManager = LinearLayoutManager(activity)
        b.recycler.adapter = adapter

        fun apply(kind: Kind?) {
            val list = if (kind == null) links else links.filter { it.kind == kind }
            adapter.submit(list)
            b.count.text = activity.getString(R.string.links_count, list.size)
        }
        b.filters.setOnCheckedStateChangeListener { _, ids ->
            apply(
                when (ids.firstOrNull()) {
                    R.id.chip_video -> Kind.VIDEO
                    R.id.chip_image -> Kind.IMAGE
                    R.id.chip_page -> Kind.PAGE
                    else -> null
                }
            )
        }
        // افتراضياً: الفيديو إن وجد، وإلا الكل
        if (links.any { it.kind == Kind.VIDEO }) b.filters.check(R.id.chip_video) else b.filters.check(R.id.chip_all)
        apply(if (links.any { it.kind == Kind.VIDEO }) Kind.VIDEO else null)

        dialog.setContentView(b.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.show()
    }

    private class Adapter(private val onClick: (Link) -> Unit) : RecyclerView.Adapter<Adapter.VH>() {
        private var items: List<Link> = emptyList()

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<Link>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(val b: ItemLinkBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemLinkBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val link = items[position]
            val ctx = h.b.root.context
            h.b.kind.text = ctx.getString(
                when (link.kind) {
                    Kind.VIDEO -> R.string.kind_video
                    Kind.IMAGE -> R.string.kind_image
                    Kind.PAGE -> R.string.kind_page
                }
            )
            h.b.title.text = link.label.ifBlank { link.host }
            h.b.url.text = link.url
            h.b.root.setOnClickListener { onClick(link) }
        }
    }
}
