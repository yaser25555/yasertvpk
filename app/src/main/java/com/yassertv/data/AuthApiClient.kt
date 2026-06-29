package com.yassertv.data

import android.content.Context
import android.content.res.Configuration
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

object AuthApiClient {
  private val gson = Gson()
  private val JSON = "application/json; charset=utf-8".toMediaType()
  private val client = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  fun login(
    context: Context,
    username: String,
    password: String,
    onSuccess: (LoginResponse) -> Unit,
    onError: (String) -> Unit
  ) {
    Thread {
      try {
        if (Config.SUPABASE_URL.isBlank() || Config.SUPABASE_KEY.isBlank()) {
          onError("AUTH_URL_NOT_CONFIGURED")
          return@Thread
        }

        val encodedUsername = URLEncoder.encode(username, "UTF-8")
        val encodedPassword = URLEncoder.encode(password, "UTF-8")

        val selectUrl = "${Config.SUPABASE_URL}/rest/v1/app_users" +
          "?select=username,expires_at" +
          "&username=eq.$encodedUsername" +
          "&password=eq.$encodedPassword" +
          "&active=eq.true" +
          "&limit=1"

        val selectRequest = Request.Builder()
          .url(selectUrl)
          .get()
          .header("apikey", Config.SUPABASE_KEY)
          .header("Authorization", "Bearer ${Config.SUPABASE_KEY}")
          .header("Accept", "application/json")
          .build()

        val selectResponse = client.newCall(selectRequest).execute()
        val body = selectResponse.body?.string().orEmpty()

        if (!selectResponse.isSuccessful || body.isBlank() || body == "[]") {
          onError("LOGIN_FAILED")
          return@Thread
        }

        val users = gson.fromJson(body, Array<SupabaseUser>::class.java) ?: emptyArray()
        val user = users.firstOrNull()
        if (user != null) {
          val expireDate = user.expiresAt.orEmpty()
          if (expireDate.isNotBlank() && expireDate.substring(0, 10.coerceAtMost(expireDate.length)) < todayString()) {
            onError("ACCOUNT_EXPIRED")
            return@Thread
          }

          val deviceToken = UUID.randomUUID().toString()
          tryPatchDeviceKey(username, deviceToken, deviceType(context))

          onSuccess(
            LoginResponse(
              success = true,
              macRegistered = true,
              message = "Login successful",
              expireDate = expireDate,
              deviceKey = deviceToken
            )
          )
        } else {
          onError("LOGIN_FAILED")
        }
      } catch (e: Exception) {
        onError(e.message ?: "CONNECTION_ERROR")
      }
    }.start()
  }

  fun sendHeartbeat(context: Context) {
    Thread {
      try {
        if (Config.SUPABASE_URL.isBlank() || Config.SUPABASE_KEY.isBlank()) return@Thread

        val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        val token = prefs.getString("device_key", "").orEmpty()
        if (token.isBlank()) return@Thread

        patchPresence(token, deviceType(context))
      } catch (_: Exception) {}
    }.start()
  }

  private fun tryPatchDeviceKey(username: String, deviceToken: String, type: String) {
    try {
      val encodedUsername = URLEncoder.encode(username, "UTF-8")
      val updateUrl = "${Config.SUPABASE_URL}/rest/v1/app_users?username=eq.$encodedUsername"
      val updateBody = gson.toJson(
        mapOf(
          "device_key" to deviceToken,
          "last_seen" to nowIsoUtc(),
          "device_type" to type
        )
      )
      val updateRequest = Request.Builder()
        .url(updateUrl)
        .patch(updateBody.toRequestBody(JSON))
        .header("apikey", Config.SUPABASE_KEY)
        .header("Authorization", "Bearer ${Config.SUPABASE_KEY}")
        .header("Prefer", "return=minimal")
        .build()
      client.newCall(updateRequest).execute()
    } catch (_: Exception) {}
  }

  private fun patchPresence(deviceToken: String, type: String) {
    val encodedToken = URLEncoder.encode(deviceToken, "UTF-8")
    val updateUrl = "${Config.SUPABASE_URL}/rest/v1/app_users?device_key=eq.$encodedToken"
    val updateBody = gson.toJson(
      mapOf(
        "last_seen" to nowIsoUtc(),
        "device_type" to type
      )
    )
    val updateRequest = Request.Builder()
      .url(updateUrl)
      .patch(updateBody.toRequestBody(JSON))
      .header("apikey", Config.SUPABASE_KEY)
      .header("Authorization", "Bearer ${Config.SUPABASE_KEY}")
      .header("Prefer", "return=minimal")
      .build()
    client.newCall(updateRequest).execute()
  }

  private fun deviceType(context: Context): String {
    val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    return if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) "tv" else "phone"
  }

  private fun nowIsoUtc(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date())
  }

  fun verifySession(
    savedToken: String,
    onValid: (String) -> Unit,
    onInvalid: (String) -> Unit
  ) {
    Thread {
      try {
        if (Config.SUPABASE_URL.isBlank() || Config.SUPABASE_KEY.isBlank()) {
          onInvalid("AUTH_URL_NOT_CONFIGURED")
          return@Thread
        }

        val url = "${Config.SUPABASE_URL}/rest/v1/app_users" +
          "?select=expires_at" +
          "&device_key=eq.$savedToken" +
          "&active=eq.true" +
          "&limit=1"

        val request = Request.Builder()
          .url(url)
          .get()
          .header("apikey", Config.SUPABASE_KEY)
          .header("Authorization", "Bearer ${Config.SUPABASE_KEY}")
          .header("Accept", "application/json")
          .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful || body.isBlank() || body == "[]") {
          onInvalid("SESSION_INVALID")
          return@Thread
        }

        val users = gson.fromJson(body, Array<SupabaseUser>::class.java) ?: emptyArray()
        val user = users.firstOrNull()
        if (user != null) {
          val expireDate = user.expiresAt.orEmpty()
          if (expireDate.isNotBlank() && expireDate.substring(0, 10.coerceAtMost(expireDate.length)) < todayString()) {
            onInvalid("ACCOUNT_EXPIRED")
          } else {
            onValid(expireDate)
          }
        } else {
          onInvalid("SESSION_INVALID")
        }
      } catch (_: Exception) {
        onInvalid("CONNECTION_ERROR")
      }
    }.start()
  }

  private fun todayString(): String {
    val cal = java.util.Calendar.getInstance()
    val y = cal.get(java.util.Calendar.YEAR)
    val m = cal.get(java.util.Calendar.MONTH) + 1
    val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
    return java.lang.String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m, d)
  }
}

data class LoginResponse(
  val success: Boolean = false,
  @com.google.gson.annotations.SerializedName("mac_registered")
  val macRegistered: Boolean = false,
  val message: String = "",
  @com.google.gson.annotations.SerializedName("expire_date")
  val expireDate: String = "",
  @com.google.gson.annotations.SerializedName("device_key")
  val deviceKey: String = ""
)

data class SupabaseUser(
  val username: String = "",
  @com.google.gson.annotations.SerializedName("expires_at")
  val expiresAt: String? = "",
  @com.google.gson.annotations.SerializedName("device_key")
  val deviceKey: String? = ""
)
