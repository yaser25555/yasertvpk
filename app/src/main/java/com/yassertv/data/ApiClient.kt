package com.yassertv.data

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ApiClient {
  private val gson = Gson()
  private val client = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .addInterceptor { chain ->
      val original = chain.request()
      val builder = original.newBuilder()
        .header("User-Agent", Config.USER_AGENT)
      chain.proceed(builder.build())
    }
    .build()

  fun fetchLiveCategories(callback: (List<LiveCategory>) -> Unit, onError: ((String) -> Unit)? = null) {
    fetchJson("${Config.API_URL}/player_api.php?action=get_live_categories&username=${Config.USERNAME}&password=${Config.PASSWORD}",
      onResult = { json -> callback(parseLiveCategories(json)) },
      onError = onError
    )
  }

  fun fetchVodCategories(callback: (List<LiveCategory>) -> Unit, onError: ((String) -> Unit)? = null) {
    fetchJson("${Config.API_URL}/player_api.php?action=get_vod_categories&username=${Config.USERNAME}&password=${Config.PASSWORD}",
      onResult = { json -> callback(parseLiveCategories(json)) },
      onError = onError
    )
  }

  fun fetchSeriesCategories(callback: (List<LiveCategory>) -> Unit, onError: ((String) -> Unit)? = null) {
    fetchJson("${Config.API_URL}/player_api.php?action=get_series_categories&username=${Config.USERNAME}&password=${Config.PASSWORD}",
      onResult = { json -> callback(parseLiveCategories(json)) },
      onError = onError
    )
  }

  fun fetchLiveChannels(callback: (List<MediaItem>) -> Unit, onError: ((String) -> Unit)? = null) {
    fetchJson("${Config.API_URL}/player_api.php?action=get_live_streams&username=${Config.USERNAME}&password=${Config.PASSWORD}",
      onResult = { json -> callback(parseLiveChannels(json)) },
      onError = onError
    )
  }

  fun fetchMovies(callback: (List<MediaItem>) -> Unit, onError: ((String) -> Unit)? = null) {
    fetchJson("${Config.API_URL}/player_api.php?action=get_vod_streams&username=${Config.USERNAME}&password=${Config.PASSWORD}",
      onResult = { json -> callback(parseMovies(json)) },
      onError = onError
    )
  }

  fun fetchSeries(callback: (List<MediaItem>) -> Unit, onError: ((String) -> Unit)? = null) {
    fetchJson("${Config.API_URL}/player_api.php?action=get_series&username=${Config.USERNAME}&password=${Config.PASSWORD}",
      onResult = { json -> callback(parseSeries(json)) },
      onError = onError
    )
  }

  fun fetchSeriesEpisodes(seriesId: String, callback: (List<MediaItem>, String) -> Unit, onError: ((String) -> Unit)? = null) {
    fetchJson("${Config.API_URL}/player_api.php?action=get_series_info&username=${Config.USERNAME}&password=${Config.PASSWORD}&series_id=$seriesId",
      onResult = { json -> callback(parseEpisodes(json), seriesId) },
      onError = onError
    )
  }

  fun fetchWorldCupChannels(callback: (List<MediaItem>) -> Unit, onError: ((String) -> Unit)? = null) {
    fetchLiveChannels({ allChannels ->
      val max = allChannels.filter { it.name.contains("MAX", ignoreCase = true) }
      callback(max)
    }, onError)
  }

  private fun fetchJson(url: String, onResult: (String) -> Unit, onError: ((String) -> Unit)? = null) {
    Thread {
      try {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
          onError?.invoke("خطأ في السيرفر: ${response.code}")
          return@Thread
        }
        val body = response.body?.string() ?: ""
        if (body.isEmpty()) {
          onError?.invoke("استجابة فارغة من السيرفر")
          return@Thread
        }
        onResult(body)
      } catch (e: Exception) {
        e.printStackTrace()
        onError?.invoke(e.message ?: "خطأ غير معروف")
      }
    }.start()
  }

  private fun parseLiveCategories(json: String): List<LiveCategory> {
    val arr = gson.fromJson(json, Array<LiveCategory>::class.java) ?: emptyArray()
    return arr.filter { it.categoryName.isNotBlank() }.toList()
  }

  private fun parseLiveChannels(json: String): List<MediaItem> {
    val arr = gson.fromJson(json, Array<LiveChannel>::class.java) ?: emptyArray()
    return arr.mapNotNull { item ->
      if (item.streamId == null) return@mapNotNull null
      MediaItem(
        id = item.streamId.toString(),
        type = "live",
        name = item.name ?: "",
        image = item.streamIcon ?: "",
        categoryId = item.categoryId?.toString() ?: "",
        num = item.num ?: 0
      )
    }
  }

  private fun parseMovies(json: String): List<MediaItem> {
    val arr = gson.fromJson(json, Array<Movie>::class.java) ?: emptyArray()
    return arr.mapNotNull { item ->
      if (item.streamId == null) return@mapNotNull null
      MediaItem(
        id = item.streamId.toString(),
        type = "movie",
        name = item.name ?: "",
        image = item.streamIcon ?: "",
        rating = item.rating ?: "0",
        categoryId = item.categoryId?.toString() ?: "",
        containerExtension = item.containerExtension ?: "mp4"
      )
    }
  }

  private fun parseEpisodes(json: String): List<MediaItem> {
    val response = gson.fromJson(json, SeriesInfoResponse::class.java) ?: return emptyList()
    val allEpisodes = response.episodes ?: return emptyList()
    val result = mutableListOf<MediaItem>()
    for ((seasonNum, eps) in allEpisodes) {
      eps.forEach { ep ->
        val epTitle = ep.title?.takeIf { it.isNotBlank() } ?: "حلقة ${ep.episodeNum}"
        result.add(MediaItem(
          id = ep.id ?: "",
          type = "episode",
          name = "S${seasonNum}E${ep.episodeNum} $epTitle",
          image = ep.info?.movieImage ?: "",
          containerExtension = ep.containerExtension ?: "mp4"
        ))
      }
    }
    return result
  }

  private fun parseSeries(json: String): List<MediaItem> {
    val arr = gson.fromJson(json, Array<Series>::class.java) ?: emptyArray()
    return arr.mapNotNull { item ->
      if (item.seriesId == null) return@mapNotNull null
      MediaItem(
        id = item.seriesId.toString(),
        type = "series",
        name = item.name ?: "",
        image = item.cover ?: "",
        genre = item.genre ?: "",
        rating = item.rating ?: "0",
        categoryId = item.categoryId?.toString() ?: ""
      )
    }
  }
}
