package com.tvtools.controlpanel

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

/** صف في قائمة: عنوان + وصف + أيقونة + إجراء عند الضغط على OK. */
class Row(
    val title: CharSequence,
    val subtitle: CharSequence,
    val icon: Drawable?,
    val onClick: () -> Unit,
)

class RowAdapter(context: Context, private var rows: List<Row>) : BaseAdapter() {

    /** استبدال المحتوى (يُستخدم عند تصفية نتائج البحث). */
    fun submit(newRows: List<Row>) {
        rows = newRows
        notifyDataSetChanged()
    }

    private val inflater = LayoutInflater.from(context)

    override fun getCount() = rows.size
    override fun getItem(position: Int) = rows[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_row, parent, false)
        val row = rows[position]
        view.findViewById<TextView>(R.id.title).text = row.title
        view.findViewById<TextView>(R.id.subtitle).text = row.subtitle
        val icon = view.findViewById<ImageView>(R.id.icon)
        if (row.icon != null) {
            icon.setImageDrawable(row.icon)
            icon.visibility = View.VISIBLE
        } else {
            icon.visibility = View.GONE
        }
        return view
    }
}
