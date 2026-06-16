package com.yassertv.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
        .header("Accept", "*/*")
        .header("Connection", "keep-alive")
        .header("Accept-Encoding", "gzip")
        .header("Host", Config.HOST_HEADER)
      val referer = "${original.url.scheme}://${original.url.host}:${original.url.port}/"
      builder.header("Referer", referer)
      chain.proceed(builder.build())
    }
    .build()

  fun fetchLiveChannels(callback: (List<MediaItem>) -> Unit) {
    fetchJson("${Config.API_URL}/player_api.php?action=get_live_streams&username=${Config.USERNAME}&password=${Config.PASSWORD}") { json ->
      callback(parseLiveChannels(json))
    }
  }

  fun fetchMovies(callback: (List<MediaItem>) -> Unit) {
    fetchJson("${Config.API_URL}/player_api.php?action=get_vod_streams&username=${Config.USERNAME}&password=${Config.PASSWORD}") { json ->
      callback(parseMovies(json))
    }
  }

  fun fetchSeries(callback: (List<MediaItem>) -> Unit) {
    fetchJson("${Config.API_URL}/player_api.php?action=get_series&username=${Config.USERNAME}&password=${Config.PASSWORD}") { json ->
      callback(parseSeries(json))
    }
  }

  fun fetchWorldCupChannels(callback: (List<MediaItem>) -> Unit) {
    fetchLiveChannels { allChannels ->
      val max = allChannels.filter { it.name.contains("MAX", ignoreCase = true) }
      callback(max)
    }
  }

  private fun fetchJson(url: String, onResult: (String) -> Unit) {
    Thread {
      try {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        onResult(body)
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }.start()
  }

  private fun parseLiveChannels(json: String): List<MediaItem> {
    return try {
      val list = gson.fromJson(json, object : TypeToken<List<LiveChannel>>() {}.type) as List<LiveChannel>
      list.map { MediaItem(
        id = it.streamId.toString(),
        type = "live",
        name = it.name,
        image = it.streamIcon,
        categoryId = it.categoryId.toString()
      ) }
    } catch (e: Exception) { emptyList() }
  }

  private fun parseMovies(json: String): List<MediaItem> {
    return try {
      val list = gson.fromJson(json, object : TypeToken<List<Movie>>() {}.type) as List<Movie>
      list.map { MediaItem(
        id = it.streamId.toString(),
        type = "movie",
        name = it.name,
        image = it.streamIcon,
        rating = it.rating,
        categoryId = it.categoryId.toString()
      ) }
    } catch (e: Exception) { emptyList() }
  }

  private fun parseSeries(json: String): List<MediaItem> {
    return try {
      val list = gson.fromJson(json, object : TypeToken<List<Series>>() {}.type) as List<Series>
      list.filter { it.seriesId != null }
        .map { MediaItem(
          id = it.seriesId.toString(),
          type = "series",
          name = it.name,
          image = it.cover,
          genre = it.genre,
          rating = it.rating,
          categoryId = it.categoryId.toString()
        ) }
    } catch (e: Exception) { emptyList() }
  }
}
