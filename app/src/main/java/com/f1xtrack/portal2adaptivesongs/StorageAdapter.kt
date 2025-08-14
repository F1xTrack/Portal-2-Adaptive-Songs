package com.f1xtrack.portal2adaptivesongs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StorageAdapter(
    private var items: List<StorageActivity.StorageItem>,
    private val onAction: (StorageActivity.StorageItem) -> Unit
) : RecyclerView.Adapter<StorageAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.txtName)
        val info: TextView = v.findViewById(R.id.txtInfo)
        val action: Button = v.findViewById(R.id.btnAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_storage_track, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.info.text = holder.itemView.context.getString(
            if (item.isUser) R.string.storage_imported_label else R.string.storage_builtin_label
        ) + if (!item.isUser) {
            " • " + (if (item.isHidden) holder.itemView.context.getString(R.string.storage_hide) else holder.itemView.context.getString(R.string.storage_unhide))
        } else ""

        if (item.isUser) {
            holder.action.text = holder.itemView.context.getString(R.string.storage_delete)
        } else {
            holder.action.text = holder.itemView.context.getString(if (item.isHidden) R.string.storage_unhide else R.string.storage_hide)
        }

        holder.action.setOnClickListener { onAction(item) }
    }

    fun update(newItems: List<StorageActivity.StorageItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
