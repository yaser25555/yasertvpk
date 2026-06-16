package com.yassertv

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.yassertv.data.Config

class PlayerActivity : AppCompatActivity() {
  private var player: ExoPlayer? = null
  private var retryCount = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_player)

    val playerView = findViewById<PlayerView>(R.id.playerView)
    val url = intent.getStringExtra("url") ?: return

    // DataSource factory مثل VLC للتعامل مع 302 redirect و Referer
    val origin = try {
      val u = java.net.URL(url)
      "${u.protocol}://${u.host}:${u.port}/"
    } catch (_: Exception) { "" }

    val dataSourceFactory = DefaultHttpDataSource.Factory()
      .setUserAgent(Config.USER_AGENT)
      .setAllowCrossProtocolRedirects(true)
      .setConnectTimeoutMs(20000)
      .setReadTimeoutMs(60000)
      .setDefaultRequestProperties(mapOf(
        "Referer" to origin,
        "Connection" to "keep-alive",
        "Accept-Encoding" to "identity"
      ))

    player = ExoPlayer.Builder(this)
      .build()
      .also { p ->
        playerView.player = p
        val mediaItem = MediaItem.Builder().setUri(url).build()
        val isHls = url.contains(".m3u8", ignoreCase = true)
        val source = if (isHls) {
          HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        } else {
          ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }
        p.setMediaSource(source)
        p.prepare()
        p.playWhenReady = true
        p.addListener(object : Player.Listener {
          override fun onPlayerError(error: PlaybackException) {
            if (retryCount < 3) {
              retryCount++
              p.stop()
              p.clearMediaItems()
              p.setMediaSource(source)
              p.prepare()
              p.playWhenReady = true
            }
          }
        })
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
