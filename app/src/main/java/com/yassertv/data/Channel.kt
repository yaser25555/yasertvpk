package com.yassertv.data

data class LiveChannel(
  val num: Int = 0,
  val name: String = "",
  val streamId: Long = 0,
  val streamIcon: String = "",
  val categoryId: String = "",
  val added: String = ""
)

data class Movie(
  val num: Int = 0,
  val name: String = "",
  val streamId: Long = 0,
  val streamIcon: String = "",
  val rating: String = "0",
  val categoryId: String = "",
  val added: String = "",
  val containerExtension: String = "mp4"
)

data class Series(
  val seriesId: Long = 0,
  val name: String = "",
  val cover: String = "",
  val categoryId: String = "",
  val genre: String = "",
  val rating: String = "0",
  val releaseDate: String = ""
)

data class Episode(
  val id: Long = 0,
  val title: String = "",
  val season: Int = 0,
  val episodeNum: Int = 0,
  val containerExtension: String = "mp4",
  val movieImage: String = ""
)

// Item واحد لكل عنصر في الواجهة
data class MediaItem(
  val id: String,
  val type: String,  // "live", "movie", "series", "episode"
  val name: String,
  val image: String = "",
  val rating: String = "0",
  val year: String = "",
  val genre: String = "",
  val categoryId: String = ""
) {
  fun streamUrl(): String = when (type) {
    "live" -> "${Config.STREAMING_URL}/live/${Config.USERNAME}/${Config.PASSWORD}/${id}.ts"
    "movie" -> "${Config.STREAMING_URL}/movie/${Config.USERNAME}/${Config.PASSWORD}/${id}.mp4"
    "episode" -> "${Config.STREAMING_URL}/series/${Config.USERNAME}/${Config.PASSWORD}/${id}.mp4"
    else -> ""
  }
}
