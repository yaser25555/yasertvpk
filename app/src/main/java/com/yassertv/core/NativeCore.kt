package com.yassertv.core

object NativeCore {
  private var loaded = false

  fun ensureLoaded(): Boolean {
    if (loaded) return true
    try {
      System.loadLibrary("yassertv_core")
      loaded = true
      return true
    } catch (e: UnsatisfiedLinkError) {
      android.util.Log.w("NativeCore", "Native library not available: ${e.message}")
      return false
    }
  }

  fun decryptString(encrypted: ByteArray, seed: Byte): String? {
    ensureLoaded()
    return try {
      if (loaded) nativeDecryptString(encrypted, seed) else null
    } catch (e: Exception) {
      android.util.Log.e("NativeCore", "decryptString error", e)
      null
    }
  }

  fun getReconnectDelay(attempt: Int): Int {
    ensureLoaded()
    return try {
      if (loaded) nativeGetReconnectDelay(attempt) else defaultDelay(attempt)
    } catch (_: Exception) { defaultDelay(attempt) }
  }

  fun resetBuffer() {
    ensureLoaded()
    try { if (loaded) nativeResetBuffer() } catch (_: Exception) {}
  }

  private fun defaultDelay(attempt: Int): Int {
    val delays = intArrayOf(1000, 2000, 3000, 5000, 8000, 12000)
    return if (attempt < delays.size) delays[attempt] else 15000
  }

  private external fun nativeDecryptString(encrypted: ByteArray, seed: Byte): String
  private external fun nativeGetReconnectDelay(attempt: Int): Int
  private external fun nativeResetBuffer()
}
