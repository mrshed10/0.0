package com.tvtools.floatingmenu.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.tvtools.floatingmenu.R
import com.tvtools.floatingmenu.data.AppEntry

/** شبكة كل التطبيقات المثبتة مع علامة ✓ على المختارة. */
class AppPickerAdapter(private val onToggle: (AppEntry, Int) -> Unit) :
    RecyclerView.Adapter<AppPickerAdapter.Holder>() {

    private var items: List<AppEntry> = emptyList()
    private var selected: Set<String> = emptySet()

    fun submit(apps: List<AppEntry>, selectedPackages: Set<String>) {
        items = apps
        selected = selectedPackages
        notifyDataSetChanged()
    }

    /** تحديث عنصر واحد مع payload حتى لا يضيع التركيز من العنصر الحالي. */
    fun updateSelection(selectedPackages: Set<String>, position: Int) {
        selected = selectedPackages
        notifyItemChanged(position, PAYLOAD_SELECTION)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_tile, parent, false)
        FocusEffects.apply(view)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = items[position]
        val isSelected = app.packageName in selected
        val context = holder.itemView.context
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        holder.badge.setImageResource(R.drawable.ic_check_circle)
        holder.badge.imageTintList = ColorStateList.valueOf(context.getColor(R.color.success))
        holder.badge.isVisible = isSelected
        holder.itemView.isActivated = isSelected
        holder.itemView.contentDescription = app.label
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onToggle(items[pos], pos)
        }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.img_icon)
        val label: TextView = view.findViewById(R.id.txt_label)
        val badge: ImageView = view.findViewById(R.id.img_badge)
    }

    private companion object {
        const val PAYLOAD_SELECTION = "selection"
    }
}
