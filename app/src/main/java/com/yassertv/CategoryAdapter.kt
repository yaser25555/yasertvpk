package com.yassertv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class SidebarCategory(
  val name: String,
  val count: Int = 0
)

class CategoryAdapter(
  private val onCategoryClick: (String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

  private var categories = listOf<SidebarCategory>()
  private var selectedPosition = 0

  fun submitList(list: List<SidebarCategory>) {
    categories = list
    notifyDataSetChanged()
  }

  fun setSelected(pos: Int) {
    val old = selectedPosition
    selectedPosition = pos
    notifyItemChanged(old)
    notifyItemChanged(pos)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val view = LayoutInflater.from(parent.context)
      .inflate(R.layout.item_sidebar_category, parent, false)
    return ViewHolder(view)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val cat = categories[position]
    val isSel = position == selectedPosition
    holder.nameView.text = cat.name
    holder.countView.text = cat.count.toString()
    holder.countView.visibility = if (cat.count > 0) View.VISIBLE else View.GONE
    holder.itemView.isSelected = isSel
    holder.itemView.setOnClickListener { onCategoryClick(cat.name); setSelected(position) }
    holder.itemView.setOnFocusChangeListener { v, hasFocus ->
      v.animate()
        .scaleX(if (hasFocus) 1.05f else 1f)
        .scaleY(if (hasFocus) 1.05f else 1f)
        .translationZ(if (hasFocus) 4f else 0f)
        .setDuration(120)
        .start()
    }
  }

  override fun getItemCount() = categories.size

  class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val nameView: TextView = view.findViewById(R.id.sidebarCategoryName)
    val countView: TextView = view.findViewById(R.id.sidebarCategoryCount)
  }
}
