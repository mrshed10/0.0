package com.localcam.surveillance

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/** عنصر في القائمة. */
data class ClipItem(
    val file: File,
    val sizeBytes: Long,
    val locked: Boolean,
    val isRecordingNow: Boolean,
)

/** محوّل قائمة المقاطع: تشغيل بالضغط على العنصر، وزرّا القفل والحذف. */
class ClipsAdapter(
    private val onPlay: (ClipItem) -> Unit,
    private val onToggleLock: (ClipItem) -> Unit,
    private val onDelete: (ClipItem) -> Unit,
) : RecyclerView.Adapter<ClipsAdapter.ViewHolder>() {

    private var items: List<ClipItem> = emptyList()

    fun submit(list: List<ClipItem>) {
        items = list
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvDetails: TextView = view.findViewById(R.id.tvDetails)
        val btnLock: ImageButton = view.findViewById(R.id.btnLock)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_clip, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        holder.tvName.text = item.file.name.removeSuffix(".mp4")

        val size = Formatter.formatShortFileSize(ctx, item.sizeBytes)
        holder.tvDetails.text = when {
            item.isRecordingNow -> "● قيد التسجيل الآن"
            item.locked -> "$size  •  🔒 مقفل (محمي من الحذف التلقائي)"
            else -> size
        }

        holder.btnLock.setImageResource(if (item.locked) R.drawable.ic_lock else R.drawable.ic_lock_open)
        holder.btnLock.imageTintList = ContextCompat.getColorStateList(
            ctx, if (item.locked) R.color.locked_gold else R.color.white
        )

        // المقطع الجاري تسجيله لا يمكن تشغيله أو حذفه حتى يكتمل
        holder.itemView.alpha = if (item.isRecordingNow) 0.6f else 1f
        holder.btnDelete.isEnabled = !item.isRecordingNow
        holder.itemView.setOnClickListener { if (!item.isRecordingNow) onPlay(item) }
        holder.btnLock.setOnClickListener { onToggleLock(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }
}
