package com.yassertv.player

import android.view.Surface
import com.yassertv.core.NativeCore

class NativeFFmpegPlayer {
  private var nativeHandle: Long = 0
  private var loaded = false

  companion object {
    init {
      try {
        System.loadLibrary("ffmpegJNI")
      } catch (e: UnsatisfiedLinkError) {
        android.util.Log.w("NativeFFmpegPlayer", "libffmpegJNI not available: ${e.message}")
      }
    }
  }

  fun init(url: String): Boolean {
    if (!NativeCore.ensureLoaded()) return false
    nativeHandle = nativeInit(url)
    loaded = nativeHandle != 0L
    return loaded
  }

  fun play() { if (loaded) nativePlay(nativeHandle) }
  fun pause() { if (loaded) nativePause(nativeHandle) }
  fun stop() { if (loaded) nativeStop(nativeHandle) }
  fun release() { if (loaded) { nativeRelease(nativeHandle); loaded = false; nativeHandle = 0 } }
  fun setSurface(surface: Surface?) { if (loaded) nativeSetSurface(nativeHandle, surface) }
  fun isPlaying(): Boolean = loaded && nativeIsPlaying(nativeHandle)
  fun getReconnectCount(): Int = if (loaded) nativeGetReconnectCount(nativeHandle) else 0
  fun resetReconnect() { if (loaded) nativeResetReconnect(nativeHandle) }

  private external fun nativeInit(url: String): Long
  private external fun nativePlay(handle: Long)
  private external fun nativePause(handle: Long)
  private external fun nativeStop(handle: Long)
  private external fun nativeRelease(handle: Long)
  private external fun nativeSetSurface(handle: Long, surface: Surface?)
  private external fun nativeIsPlaying(handle: Long): Boolean
  private external fun nativeGetReconnectCount(handle: Long): Int
  private external fun nativeResetReconnect(handle: Long)
}
