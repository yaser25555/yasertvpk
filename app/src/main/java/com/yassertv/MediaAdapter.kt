package com.yassertv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.yassertv.data.MediaItem

class MediaAdapter(
  private val onItemClick: (MediaItem) -> Unit,
  private val tvMode: Boolean = false,
  private val onFavClick: ((MediaItem) -> Unit)? = null,
  private val isFav: ((MediaItem) -> Boolean)? = null
) : RecyclerView.Adapter<MediaAdapter.ViewHolder>() {

  companion object {
    private const val TYPE_PHONE = 0
    private const val TYPE_LIVE_CARD = 1
    private const val TYPE_POSTER = 2
  }

  private var items = listOf<MediaItem>()

  fun getItem(position: Int): MediaItem = items[position]

  fun submitList(list: List<MediaItem>) {
    items = list
    notifyDataSetChanged()
  }

  override fun getItemViewType(position: Int): Int {
    if (!tvMode || items[position].type == "category") return TYPE_PHONE
    return when (items[position].type) {
      "movie", "series", "episode" -> TYPE_POSTER
      else -> TYPE_LIVE_CARD
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val layoutRes = when (viewType) {
      TYPE_LIVE_CARD -> R.layout.item_media_live
      TYPE_POSTER -> R.layout.item_media_poster
      else -> R.layout.item_media
    }
    val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
    return ViewHolder(view)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val item = items[position]
    holder.title.text = item.name

    if (item.type == "category") {
      holder.number.visibility = View.GONE
      holder.image.visibility = View.GONE
      holder.rating.visibility = View.GONE
      holder.fav.visibility = View.GONE
      holder.itemView.setPadding(
        holder.itemView.context.resources.getDimensionPixelSize(R.dimen.grid_spacing) * 2,
        holder.itemView.context.resources.getDimensionPixelSize(R.dimen.grid_spacing),
        holder.itemView.context.resources.getDimensionPixelSize(R.dimen.grid_spacing) * 2,
        holder.itemView.context.resources.getDimensionPixelSize(R.dimen.grid_spacing)
      )
      holder.itemView.setOnClickListener { onItemClick(item) }
      holder.itemView.setOnFocusChangeListener { view, hasFocus ->
        view.animate()
          .scaleX(if (hasFocus) 1.03f else 1f)
          .scaleY(if (hasFocus) 1.03f else 1f)
          .translationZ(if (hasFocus) 6f else 0f)
          .setDuration(120)
          .start()
      }
      return
    }

    val chNum = if (item.num > 0) item.num else position + 1
    holder.number.text = chNum.toString()
    holder.number.visibility = View.VISIBLE

    if (item.type == "live") {
      holder.image.visibility = View.GONE
    } else {
      holder.image.visibility = View.VISIBLE
    }
    val rating = item.rating.toFloatOrNull() ?: 0f
    if (rating > 0f) {
      holder.rating.text = "★ ${item.rating}"
      holder.rating.visibility = View.VISIBLE
    } else {
      holder.rating.visibility = View.GONE
    }

    if (onFavClick != null && isFav != null) {
      holder.fav.text = if (isFav(item)) "★" else "☆"
      holder.fav.visibility = View.VISIBLE
      holder.fav.setOnClickListener { onFavClick(item) }
      holder.fav.setOnFocusChangeListener { v, hasFocus ->
        v.animate().scaleX(if (hasFocus) 1.2f else 1f).scaleY(if (hasFocus) 1.2f else 1f).setDuration(120).start()
      }
    } else {
      holder.fav.visibility = View.GONE
    }

    val isPoster = item.type == "movie" || item.type == "series" || item.type == "episode"
    holder.image.scaleType = if (isPoster || tvMode) ScaleType.CENTER_CROP else ScaleType.FIT_CENTER
    holder.image.setPadding(0, 0, 0, 0)

    if (tvMode && !isPoster) {
      holder.image.setPadding(
        holder.image.context.resources.getDimensionPixelSize(R.dimen.grid_spacing),
        holder.image.context.resources.getDimensionPixelSize(R.dimen.grid_spacing),
        holder.image.context.resources.getDimensionPixelSize(R.dimen.grid_spacing),
        holder.image.context.resources.getDimensionPixelSize(R.dimen.grid_spacing)
      )
    } else if (!isPoster && !tvMode) {
      val pad = 8
      holder.image.setPadding(pad, pad, pad, pad)
    }

    val request = Glide.with(holder.image.context)
      .load(item.image)
      .placeholder(R.drawable.logo)
      .error(R.drawable.logo)

    if (isPoster || (tvMode && getItemViewType(position) == TYPE_POSTER)) {
      request.centerCrop().into(holder.image)
    } else if (tvMode) {
      request.fitCenter().into(holder.image)
    } else if (isPoster) {
      request.centerCrop().into(holder.image)
    } else {
      request.fitCenter().into(holder.image)
    }

    holder.itemView.setOnClickListener { onItemClick(item) }
    holder.itemView.setOnFocusChangeListener { view, hasFocus ->
      if (hasFocus) {
        val scale = if (tvMode) 1.08f else 1.06f
        view.animate()
          .scaleX(scale)
          .scaleY(scale)
          .translationZ(if (tvMode) 12f else 8f)
          .setDuration(150)
          .start()
        val pos = holder.bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
          (view.parent as? RecyclerView)?.post { (view.parent as RecyclerView).smoothScrollToPosition(pos) }
        }
      } else {
        view.animate()
          .scaleX(1f)
          .scaleY(1f)
          .translationZ(0f)
          .setDuration(150)
          .start()
      }
    }
  }

  override fun getItemCount() = items.size

  class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val number: TextView = view.findViewById(R.id.itemNumber)
    val image: ImageView = view.findViewById(R.id.itemImage)
    val title: TextView = view.findViewById(R.id.itemTitle)
    val rating: TextView = view.findViewById(R.id.itemRating)
    val fav: TextView = view.findViewById(R.id.btnFav)
  }
}
