package com.yassertv.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.yassertv.data.Config
import java.io.File

@UnstableApi
object StreamPlayerFactory {
  private var cache: SimpleCache? = null

  private fun getCache(context: Context): SimpleCache {
    if (cache == null) {
      val cacheDir = File(context.cacheDir, "exo_cache")
      val evictor = LeastRecentlyUsedCacheEvictor(200 * 1024 * 1024)
      cache = SimpleCache(cacheDir, evictor)
    }
    return cache!!
  }

  fun create(context: Context, forLive: Boolean, preferredBufferMs: Int): ExoPlayer {
    val requestHeaders = mapOf(
      "Accept" to "*/*",
      "Connection" to "keep-alive"
    )

    val httpFactory = DefaultHttpDataSource.Factory()
      .setUserAgent(Config.USER_AGENT)
      .setDefaultRequestProperties(requestHeaders)
      .setConnectTimeoutMs(15_000)
      .setReadTimeoutMs(if (forLive) 120_000 else 60_000)
      .setAllowCrossProtocolRedirects(true)

    val cacheDataSourceFactory = CacheDataSource.Factory()
      .setCache(getCache(context))
      .setUpstreamDataSourceFactory(httpFactory)
      .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    val minBufferMs = preferredBufferMs.coerceIn(
      if (forLive) 30_000 else 10_000,
      if (forLive) 180_000 else 60_000
    )
    val maxBufferMs = if (forLive) {
      (minBufferMs * 3).coerceIn(120_000, 360_000)
    } else {
      (minBufferMs * 3).coerceIn(60_000, 240_000)
    }

    val loadControl = if (forLive) {
      DefaultLoadControl.Builder()
        .setBufferDurationsMs(minBufferMs, maxBufferMs, 3_000, 15_000)
        .setPrioritizeTimeOverSizeThresholds(true)
        .setTargetBufferBytes(50 * 1024 * 1024)
        .build()
    } else {
      DefaultLoadControl.Builder()
        .setBufferDurationsMs(minBufferMs, maxBufferMs, 2_000, 10_000)
        .setTargetBufferBytes(30 * 1024 * 1024)
        .build()
    }

    val trackSelector = DefaultTrackSelector(context)
    trackSelector.setParameters(
      trackSelector.buildUponParameters()
        .setMaxVideoBitrate(20_000_000)
        .setMaxVideoSize(1920, 1080)
    )

    val mediaSourceFactory = DefaultMediaSourceFactory(context)
      .setDataSourceFactory(cacheDataSourceFactory)

    return ExoPlayer.Builder(context)
      .setMediaSourceFactory(mediaSourceFactory)
      .setLoadControl(loadControl)
      .setTrackSelector(trackSelector)
      .build()
  }

  fun releaseCache() {
    try { cache?.release() } catch (_: Exception) {}
    cache = null
  }
}
