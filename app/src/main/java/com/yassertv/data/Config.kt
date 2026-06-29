package com.yassertv.data

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

data class ServerInfo(
  val name: String,
  val apiUrl: String,
  val streamingUrl: String,
  val username: String,
  val password: String,
  val hostHeader: String = "",
  val userAgent: String = "okhttp/5.0.0-alpha.2",
  val ipOverride: String = "",
  val icyMetaData: String = ""
)

object Config {
  private const val AES_KEY = "yassertvkey12345"

  var activeServer: ServerInfo = servers[0]

  val API_URL get() = activeServer.apiUrl
  val STREAMING_URL get() = activeServer.streamingUrl
  val USERNAME get() = activeServer.username
  val PASSWORD get() = activeServer.password
  val USER_AGENT get() = activeServer.userAgent
  val HOST_HEADER get() = activeServer.hostHeader.ifBlank { extractHost(activeServer.streamingUrl) }
  val IP_OVERRIDE get() = activeServer.ipOverride

  val servers = listOf(
    ServerInfo(
      name = "السيرفر الرئيسي",
      apiUrl = "http://192.142.31.13:8080",
      streamingUrl = "http://t77ert.top:8080",
      username = "28438439468494",
      password = "2eUmaHSdOgM2WcJ",
      hostHeader = "t77ert.top:8080"
    ),
    ServerInfo(
      name = "السيرفر الثاني",
      apiUrl = "http://sts.mydroon.com",
      streamingUrl = "http://sts.mydroon.com",
      username = "Abderrazakabbadi",
      password = "t8NsGxscTPM7whb",
      hostHeader = "sts.mydroon.com",
      ipOverride = "89.37.116.83"
    ),
    ServerInfo(
      name = "السيرفر الثالث",
      apiUrl = "http://kalaasmr.blog:8080",
      streamingUrl = "http://kalaasmr.blog:8080",
      username = "102030",
      password = "102030",
      hostHeader = "kalaasmr.blog:8080",
      ipOverride = "31.59.212.103",
      userAgent = "com.rayan.iqrebrand/43 (Linux; U; Android 15; ar_SA; moto g15; Build/VVTA35.51-153; Cronet/149.0.7827.48)",
      icyMetaData = "1"
    )
  )

  private fun extractHost(url: String): String = try {
    val u = java.net.URL(url)
    if (u.port > 0 && u.port != 80 && u.port != 443) "${u.host}:${u.port}" else u.host
  } catch (_: Exception) { url }

  fun aesEncrypt(plain: String): String = try {
    val key = SecretKeySpec(AES_KEY.toByteArray(Charsets.UTF_8), "AES")
    val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.DEFAULT)
  } catch (_: Exception) { "" }

  fun aesDecrypt(encrypted: String): String = try {
    val key = SecretKeySpec(AES_KEY.toByteArray(Charsets.UTF_8), "AES")
    val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, key)
    String(cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT)), Charsets.UTF_8)
  } catch (_: Exception) { "" }
}
