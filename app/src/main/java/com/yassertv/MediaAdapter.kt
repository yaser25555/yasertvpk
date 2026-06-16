package com.yassertv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.yassertv.data.MediaItem

class MediaAdapter(
  private val onItemClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaAdapter.ViewHolder>() {

  private var items = listOf<MediaItem>()

  fun submitList(list: List<MediaItem>) {
    items = list
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val view = LayoutInflater.from(parent.context)
      .inflate(R.layout.item_media, parent, false)
    return ViewHolder(view)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val item = items[position]
    holder.title.text = item.name
    holder.rating.text = if (item.rating.toFloatOrNull() ?: 0f > 0) "★ ${item.rating}" else ""

    Glide.with(holder.image.context)
      .load(item.image)
      .placeholder(R.drawable.icon)
      .error(R.drawable.icon)
      .into(holder.image)

    holder.itemView.setOnClickListener { onItemClick(item) }
  }

  override fun getItemCount() = items.size

  class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val image: ImageView = view.findViewById(R.id.itemImage)
    val title: TextView = view.findViewById(R.id.itemTitle)
    val rating: TextView = view.findViewById(R.id.itemRating)
  }
}
