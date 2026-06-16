package com.yassertv.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ApiClient {
  private val gson = Gson()
  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .addInterceptor { chain ->
      val original = chain.request()
      val builder = original.newBuilder()
        .header("User-Agent", Config.USER_AGENT)
        .header("Accept", "*/*")
        .header("Connection", "keep-alive")
        .header("Accept-Encoding", "identity")
      // Referer = نفس أصل الرابط (يشتغل مع 302)
      val referer = "${original.url.scheme}://${original.url.host}:${original.url.port}/"
      builder.header("Referer", referer)
      chain.proceed(builder.build())
    }
    .build()

  fun fetchLiveChannels(callback: (List<MediaItem>) -> Unit) {
    fetchJson("${Config.STREAMING_URL}/player_api.php?username=${Config.USERNAME}&password=${Config.PASSWORD}&action=live&category_id=") { json ->
      callback(parseLiveChannels(json))
    }
  }

  fun fetchLiveCategories(callback: (List<Pair<String, String>>) -> Unit) {
    fetchJson("${Config.STREAMING_URL}/player_api.php?username=${Config.USERNAME}&password=${Config.PASSWORD}&action=live_categories") { json ->
      val list = parseCategories(json)
      callback(list)
    }
  }

  fun fetchMovies(callback: (List<MediaItem>) -> Unit) {
    fetchJson("${Config.STREAMING_URL}/player_api.php?username=${Config.USERNAME}&password=${Config.PASSWORD}&action=vod") { json ->
      callback(parseMovies(json))
    }
  }

  fun fetchSeries(callback: (List<MediaItem>) -> Unit) {
    fetchJson("${Config.STREAMING_URL}/player_api.php?username=${Config.USERNAME}&password=${Config.PASSWORD}&action=series") { json ->
      callback(parseSeries(json))
    }
  }

  fun fetchSeriesEpisodes(seriesId: String, callback: (List<MediaItem>) -> Unit) {
    fetchJson("${Config.STREAMING_URL}/player_api.php?username=${Config.USERNAME}&password=${Config.PASSWORD}&action=get_series_info&series_id=$seriesId") { json ->
      callback(parseEpisodes(json))
    }
  }

  // World Cup = beIN MAX channels
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
      list.filter { it.num != null }
        .map { MediaItem(
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
      list.filter { it.num != null }
        .map { MediaItem(
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

  private fun parseEpisodes(json: String): List<MediaItem> {
    return try {
      val obj = gson.fromJson(json, Map::class.java)
      val episodes = obj?.get("episodes") as? Map<*, *> ?: return emptyList()
      episodes.flatMap { (_, list) ->
        (list as? List<*>)?.mapNotNull { ep ->
          val map = ep as? Map<*, *> ?: return@mapNotNull null
          val id = map["id"]?.toString() ?: map["episode_id"]?.toString() ?: return@mapNotNull null
          val title = map["title"]?.toString() ?: ""
          MediaItem(id = id, type = "episode", name = title)
        } ?: emptyList()
      }
    } catch (e: Exception) { emptyList() }
  }

  private fun parseCategories(json: String): List<Pair<String, String>> {
    return try {
      val list = gson.fromJson(json, List::class.java) as List<Map<String, Any>>
      list.mapNotNull { m ->
        val id = m["category_id"]?.toString() ?: return@mapNotNull null
        val name = m["category_name"]?.toString() ?: return@mapNotNull null
        Pair(id, name)
      }
    } catch (e: Exception) { emptyList() }
  }
}
