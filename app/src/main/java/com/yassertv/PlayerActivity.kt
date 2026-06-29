package com.yassertv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.yassertv.data.Config
import java.io.File

class PlayerActivity : AppCompatActivity() {
  private var player: ExoPlayer? = null
  private var retryCount = 0
  private val maxRetries = 10
  private val handler = Handler(Looper.getMainLooper())
  private var cache: SimpleCache? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_player)

    val playerView = findViewById<PlayerView>(R.id.playerView)
    val url = intent.getStringExtra("url") ?: return
    val name = intent.getStringExtra("name") ?: ""
    findViewById<TextView>(R.id.tvChannelName)?.text = name

    val origin = try { val u = java.net.URL(url); "${u.protocol}://${u.host}:${u.port}/" } catch (_: Exception) { "" }

    val httpFactory = DefaultHttpDataSource.Factory()
      .setUserAgent(Config.USER_AGENT)
      .setAllowCrossProtocolRedirects(true)
      .setConnectTimeoutMs(20_000)
      .setReadTimeoutMs(120_000)
      .setDefaultRequestProperties(mapOf("Referer" to origin, "Connection" to "keep-alive", "Accept-Encoding" to "identity"))

    cache = SimpleCache(File(cacheDir, "media_cache"), null, null, null, 200L * 1024 * 1024)

    val dsFactory = CacheDataSource.Factory()
      .setCache(cache!!)
      .setUpstreamDataSourceFactory(httpFactory)
      .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    player = ExoPlayer.Builder(this)
      .setLoadControl(DefaultLoadControl.Builder()
        .setBufferDurationsMs(90_000, 300_000, 30_000, 60_000)
        .setPrioritizeTimeOverSizeThresholds(true).build())
      .build()
      .also { p ->
        playerView.player = p
        val mediaItem = MediaItem.Builder().setUri(url).build()
        val isHls = url.contains(".m3u8", ignoreCase = true)
        val source = if (isHls) {
          HlsMediaSource.Factory(dsFactory).setAllowChunklessPreparation(true).createMediaSource(mediaItem)
        } else {
          ProgressiveMediaSource.Factory(dsFactory).createMediaSource(mediaItem)
        }
        p.setMediaSource(source)
        p.prepare()
        p.playWhenReady = true
        p.addListener(object : Player.Listener {
          override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_BUFFERING) {
              handler.removeCallbacksAndMessages(null)
              handler.postDelayed({ if (p.playbackState == Player.STATE_BUFFERING) retryPlayback(p, source) }, 30_000L)
            }
            if (state == Player.STATE_READY) retryCount = 0
            if (state == Player.STATE_ENDED) handler.postDelayed({ retryPlayback(p, source) }, 2_000L)
          }
          override fun onPlayerError(error: PlaybackException) { retryPlayback(p, source) }
        })
      }
  }

  private fun retryPlayback(p: ExoPlayer, source: com.google.android.media3.exoplayer.source.MediaSource) {
    if (retryCount >= maxRetries) { handler.post { Toast.makeText(this, "تعذر الاتصال بعد $maxRetries محاولات", Toast.LENGTH_LONG).show() }; return }
    retryCount++
    handler.postDelayed({
      try { p.stop(); p.clearMediaItems(); p.setMediaSource(source, 0); p.prepare(); p.playWhenReady = true }
      catch (_: Exception) {}
    }, (2_000L * retryCount).coerceAtMost(30_000L))
  }

  override fun onStop() { super.onStop(); player?.pause() }

  override fun onDestroy() {
    super.onDestroy(); handler.removeCallbacksAndMessages(null); player?.release(); player = null; cache?.release(); cache = null
  }
}
