package com.yassertv.data

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class RemoteConfigData(
  val apiUrl: String = "",
  val streamingUrl: String = "",
  val username: String = "",
  val password: String = "",
  val userAgent: String = "",
  val hostHeader: String = "",
  val genralApiUrl: String = "",
  val supabaseUrl: String = "",
  val supabaseKey: String = "",
  val bufferMs: Int = 0,
  val liveBufferMs: Int = 0,
  val hlsPreferred: Boolean = false,
  val reconnectLimit: Int = 0
)

object RemoteConfig {
  private val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()
  private val gson = Gson()

  var current: RemoteConfigData = RemoteConfigData()
    private set

  fun load(configUrl: String, onResult: (RemoteConfigData) -> Unit) {
    Thread {
      try {
        val request = Request.Builder().url(configUrl).build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
          val body = response.body?.string()
          if (!body.isNullOrBlank()) {
            val parsed = gson.fromJson(body, RemoteConfigData::class.java)
            if (parsed != null) {
              current = parsed
              Config.applyRemote(parsed)
            }
          }
        }
      } catch (_: Exception) {}
      onResult(current)
    }.start()
  }
}
