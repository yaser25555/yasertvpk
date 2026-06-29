package com.yassertv

import android.app.Application
import android.util.Log
import com.yassertv.data.RemoteConfig

class App : Application() {
  companion object {
    const val REMOTE_CONFIG_URL = "https://raw.githubusercontent.com/used4/online/refs/heads/main/Alameer-altqny.json"
  }

  override fun onCreate() {
    super.onCreate()

    RemoteConfig.load(REMOTE_CONFIG_URL) { cfg ->
      Log.i("App", "Remote config loaded: apiUrl=${cfg.apiUrl}")
    }

    val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        val fos = openFileOutput("crash_log.txt", MODE_PRIVATE)
        fos.write("Thread: ${thread.name}\n".toByteArray())
        fos.write(Log.getStackTraceString(throwable).toByteArray())
        fos.close()
      } catch (_: Exception) {}
      prevHandler?.uncaughtException(thread, throwable) ?: java.lang.System.exit(2)
    }
  }
}
