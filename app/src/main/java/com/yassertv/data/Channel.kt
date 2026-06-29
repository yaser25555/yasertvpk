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

data class MediaItem(
  val id: String,
  val type: String,
  val name: String,
  val image: String = "",
  val rating: String = "0",
  val year: String = "",
  val genre: String = "",
  val categoryId: String = ""
) {
  fun streamUrl(): String {
    val s = Config.activeServer
    val base = s.streamingUrl
    val u = s.username
    val p = s.password
    return when (type) {
      "live" -> "$base/live/$u/$p/$id.ts"
      "movie" -> "$base/movie/$u/$p/$id.mp4"
      "episode" -> "$base/series/$u/$p/$id.mp4"
      else -> ""
    }
  }
}
