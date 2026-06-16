package com.yassertv

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.yassertv.data.Config

@UnstableApi
class PlayerActivity : AppCompatActivity() {
  private var player: ExoPlayer? = null
  private lateinit var playerView: androidx.media3.ui.PlayerView
  private lateinit var titleText: TextView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_player)

    playerView = findViewById(R.id.playerView)
    titleText = findViewById(R.id.playerTitle)

    val name = intent.getStringExtra("name") ?: "قناة"
    val url = intent.getStringExtra("url") ?: return

    titleText.text = name

    // DataSource factory with custom headers (مثل VLC)
    val dataSourceFactory = DefaultHttpDataSource.Factory()
      .setUserAgent(Config.USER_AGENT)
      .setAllowCrossProtocolRedirects(true)
      .setConnectTimeoutMs(15000)
      .setReadTimeoutMs(30000)
      .setKeepPostFor302Redirects(false)

    // إضافة الـ Referer header تلقائياً
    val origin = try {
      val u = java.net.URL(url)
      "${u.protocol}://${u.host}:${u.port}/"
    } catch (_: Exception) { "" }

    dataSourceFactory.setDefaultRequestProperties(mapOf(
      "Referer" to origin,
      "Connection" to "keep-alive",
      "Accept-Encoding" to "identity"
    ))

    // Create player
    player = ExoPlayer.Builder(this)
      .build()
      .also { p ->
        playerView.player = p
        val mediaItem = MediaItem.Builder()
          .setUri(url)
          .build()
        // Try HLS first, fallback to progressive
        try {
          val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)
          p.setMediaSource(hlsSource)
        } catch (_: Exception) {
          val progSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)
          p.setMediaSource(progSource)
        }
        p.prepare()
        p.playWhenReady = true
      }
  }

  override fun onStop() {
    super.onStop()
    player?.pause()
  }

  override fun onDestroy() {
    super.onDestroy()
    player?.release()
    player = null
  }
}
