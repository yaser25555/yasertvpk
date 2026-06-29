package com.yassertv.data

import com.google.gson.annotations.SerializedName

data class LiveCategory(
  @field:SerializedName("category_id") val categoryId: String = "",
  @field:SerializedName("category_name") val categoryName: String = "",
  @field:SerializedName("parent_id") val parentId: String = "0"
)

data class LiveChannel(
  val num: Int? = 0,
  val name: String? = "",
  @field:SerializedName("stream_id") val streamId: Long? = 0,
  @field:SerializedName("stream_icon") val streamIcon: String? = "",
  @field:SerializedName("category_id") val categoryId: String? = "",
  val added: String? = ""
)

data class Movie(
  val num: Int? = 0,
  val name: String? = "",
  @field:SerializedName("stream_id") val streamId: Long? = 0,
  @field:SerializedName("stream_icon") val streamIcon: String? = "",
  val rating: String? = "0",
  @field:SerializedName("category_id") val categoryId: String? = "",
  val added: String? = "",
  @field:SerializedName("container_extension") val containerExtension: String? = "mp4"
)

data class Series(
  @field:SerializedName("series_id") val seriesId: Long? = 0,
  val name: String? = "",
  val cover: String? = "",
  @field:SerializedName("category_id") val categoryId: String? = "",
  val genre: String? = "",
  val rating: String? = "0",
  @field:SerializedName("release_date") val releaseDate: String? = ""
)

data class Episode(
  val id: Long = 0,
  val title: String = "",
  val season: Int = 0,
  @SerializedName("episode_num") val episodeNum: Int = 0,
  @SerializedName("container_extension") val containerExtension: String = "mp4",
  @SerializedName("movie_image") val movieImage: String = ""
)

data class SeriesInfoResponse(
  val info: SeriesInfo? = null,
  val episodes: Map<String, List<EpisodeInfo>>? = null
)

data class SeriesInfo(
  val name: String? = "",
  val cover: String? = "",
  val plot: String? = "",
  val genre: String? = "",
  val rating: String? = "0"
)

data class EpisodeInfo(
  val id: String? = "",
  val title: String? = "",
  @SerializedName("episode_num") val episodeNum: String? = "",
  @SerializedName("container_extension") val containerExtension: String? = "mp4",
  val info: EpisodeInfoDetail? = null
)

data class EpisodeInfoDetail(
  @SerializedName("movie_image") val movieImage: String? = "",
  val duration: String? = ""
)

data class MediaItem(
  val id: String,
  val type: String,
  val name: String,
  val image: String = "",
  val rating: String = "0",
  val year: String = "",
  val genre: String = "",
  val categoryId: String = "",
  val containerExtension: String = "mp4",
  val num: Int = 0,
  val directUrl: String = ""
) {
  fun streamUrl(): String {
    if (directUrl.isNotEmpty()) return directUrl
    return when (type) {
      "live" -> "${Config.STREAMING_URL}/live/${Config.USERNAME}/${Config.PASSWORD}/${id}.ts"
      "movie" -> "${Config.STREAMING_URL}/movie/${Config.USERNAME}/${Config.PASSWORD}/${id}.${containerExtension}"
      "episode" -> "${Config.STREAMING_URL}/series/${Config.USERNAME}/${Config.PASSWORD}/${id}.${containerExtension}"
      else -> ""
    }
  }

  fun streamUrlM3u8(): String {
    if (directUrl.isNotEmpty()) return directUrl
    return when (type) {
      "live" -> "${Config.STREAMING_URL}/live/${Config.USERNAME}/${Config.PASSWORD}/${id}.m3u8"
      else -> ""
    }
  }
}
