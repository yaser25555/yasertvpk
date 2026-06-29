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
      val srv = Config.activeServer
      val builder = original.newBuilder()
        .header("User-Agent", srv.userAgent)
        .header("Accept", "*/*")
        .header("Connection", "keep-alive")
        .header("Accept-Encoding", "gzip")
      val host = srv.hostHeader.ifBlank { extractHost(srv.apiUrl) }
      if (host.isNotBlank()) builder.header("Host", host)
      val referer = "${original.url.scheme}://${original.url.host}:${original.url.port}/"
      builder.header("Referer", referer)
      chain.proceed(builder.build())
    }
    .build()

  private fun extractHost(url: String): String = try {
    val u = java.net.URL(url)
    if (u.port > 0 && u.port != 80 && u.port != 443) "${u.host}:${u.port}" else u.host
  } catch (_: Exception) { "" }

  fun fetchLiveChannels(callback: (List<MediaItem>) -> Unit, onError: ((String) -> Unit)? = null) {
    fetchJson("${Config.API_URL}/player_api.php?action=get_live_streams&username=${Config.USERNAME}&password=${Config.PASSWORD}",
      { json -> callback(parseLiveChannels(json)) }, onError)
  }

  fun fetchMovies(callback: (List<MediaItem>) -> Unit, onError: ((String) -> Unit)? = null) {
    fetchJson("${Config.API_URL}/player_api.php?action=get_vod_streams&username=${Config.USERNAME}&password=${Config.PASSWORD}",
      { json -> callback(parseMovies(json)) }, onError)
  }

  fun fetchSeries(callback: (List<MediaItem>) -> Unit, onError: ((String) -> Unit)? = null) {
    fetchJson("${Config.API_URL}/player_api.php?action=get_series&username=${Config.USERNAME}&password=${Config.PASSWORD}",
      { json -> callback(parseSeries(json)) }, onError)
  }

  private fun fetchJson(url: String, onResult: (String) -> Unit, onError: ((String) -> Unit)? = null) {
    Thread {
      try {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) { onError?.invoke("خطأ في السيرفر: ${response.code}"); return@Thread }
        val body = response.body?.string() ?: ""
        if (body.isEmpty()) { onError?.invoke("استجابة فارغة"); return@Thread }
        onResult(body)
      } catch (e: Exception) { onError?.invoke(e.message ?: "خطأ في الاتصال") }
    }.start()
  }

  private fun parseLiveChannels(json: String): List<MediaItem> = try {
    val list = gson.fromJson(json, object : TypeToken<List<LiveChannel>>() {}.type) as List<LiveChannel>
    list.map { MediaItem(it.streamId.toString(), "live", it.name, it.streamIcon, categoryId = it.categoryId.toString()) }
  } catch (_: Exception) { emptyList() }

  private fun parseMovies(json: String): List<MediaItem> = try {
    val list = gson.fromJson(json, object : TypeToken<List<Movie>>() {}.type) as List<Movie>
    list.map { MediaItem(it.streamId.toString(), "movie", it.name, it.streamIcon, it.rating, categoryId = it.categoryId.toString()) }
  } catch (_: Exception) { emptyList() }

  private fun parseSeries(json: String): List<MediaItem> = try {
    val list = gson.fromJson(json, object : TypeToken<List<Series>>() {}.type) as List<Series>
    list.filter { it.seriesId != null }.map { MediaItem(it.seriesId.toString(), "series", it.name, it.cover, it.rating, genre = it.genre, categoryId = it.categoryId.toString()) }
  } catch (_: Exception) { emptyList() }
}
