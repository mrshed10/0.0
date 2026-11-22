package com.tvtools.floatingmenu.ui

import android.content.res.ColorStateList
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.tvtools.floatingmenu.R
import com.tvtools.floatingmenu.data.AppEntry

/** شبكة التطبيقات المفضلة داخل القائمة العائمة + مربع (+) للإضافة في النهاية. */
class FavoritesAdapter(private val listener: Listener) :
    RecyclerView.Adapter<FavoritesAdapter.Holder>() {

    interface Listener {
        fun onAppClick(app: AppEntry)
        fun onAppRemove(app: AppEntry)
        fun onAddClick()
        fun onRequestEditMode()
    }

    private val items = mutableListOf<AppEntry>()

    var editMode = false
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_MODE)
        }

    val appCount: Int get() = items.size

    fun submit(apps: List<AppEntry>) {
        items.clear()
        items.addAll(apps)
        notifyDataSetChanged()
    }

    fun remove(app: AppEntry) {
        val index = items.indexOf(app)
        if (index >= 0) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    override fun getItemCount() = items.size + 1

    override fun getItemViewType(position: Int) = if (position == items.size) TYPE_ADD else TYPE_APP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_tile, parent, false)
        FocusEffects.apply(view)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val context = holder.itemView.context
        if (getItemViewType(position) == TYPE_ADD) {
            holder.icon.setImageResource(R.drawable.ic_add)
            holder.icon.imageTintList = ColorStateList.valueOf(context.getColor(R.color.accent))
            holder.label.setText(R.string.add_app)
            holder.badge.isVisible = false
            holder.itemView.contentDescription = context.getString(R.string.add_app)
            holder.itemView.setOnClickListener { listener.onAddClick() }
            holder.itemView.setOnLongClickListener(null)
            holder.itemView.setOnContextClickListener(null)
            holder.itemView.setOnKeyListener(null)
            return
        }

        val app = items[position]
        holder.icon.imageTintList = null
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        holder.itemView.contentDescription = app.label
        holder.badge.setImageResource(R.drawable.ic_remove_circle)
        holder.badge.imageTintList = ColorStateList.valueOf(context.getColor(R.color.danger))
        holder.badge.isVisible = editMode

        holder.itemView.setOnClickListener {
            if (editMode) listener.onAppRemove(app) else listener.onAppClick(app)
        }
        // ضغطة مطولة بالريموت أو نقرة يمين بالماوس => وضع التعديل
        holder.itemView.setOnLongClickListener {
            listener.onRequestEditMode()
            true
        }
        holder.itemView.setOnContextClickListener {
            listener.onRequestEditMode()
            true
        }
        // Delete / Backspace في الكيبورد => حذف مباشر
        holder.itemView.setOnKeyListener { _, keyCode, event ->
            val isDelete = keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL
            if (isDelete && event.action == KeyEvent.ACTION_UP) listener.onAppRemove(app)
            isDelete
        }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.img_icon)
        val label: TextView = view.findViewById(R.id.txt_label)
        val badge: ImageView = view.findViewById(R.id.img_badge)
    }

    private companion object {
        const val TYPE_APP = 0
        const val TYPE_ADD = 1
        const val PAYLOAD_MODE = "mode"
    }
}
