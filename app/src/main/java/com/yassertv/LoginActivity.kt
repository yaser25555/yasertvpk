package com.yassertv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.yassertv.data.Config
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {
  private lateinit var serverSpinner: Spinner
  private lateinit var usernameInput: EditText
  private lateinit var passwordInput: EditText
  private lateinit var loginButton: Button
  private lateinit var loginProgress: ProgressBar
  private lateinit var loginMessage: TextView
  private val prefs by lazy { getSharedPreferences("yassertv", MODE_PRIVATE) }
  private val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (prefs.getBoolean("logged_in", false)) {
      val idx = prefs.getInt("server_index", 0).coerceIn(0, Config.servers.lastIndex)
      Config.activeServer = Config.servers[idx]
      startActivity(Intent(this, MainActivity::class.java))
      finish()
      return
    }

    setContentView(R.layout.activity_login)

    serverSpinner = findViewById(R.id.serverSpinner)
    usernameInput = findViewById(R.id.usernameInput)
    passwordInput = findViewById(R.id.passwordInput)
    loginButton = findViewById(R.id.loginButton)
    loginProgress = findViewById(R.id.loginProgress)
    loginMessage = findViewById(R.id.loginMessage)

    val names = Config.servers.mapIndexed { i, s -> "${i + 1}. ${s.name}" }
    serverSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)

    serverSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
        val srv = Config.servers[pos]
        Config.activeServer = srv
        usernameInput.setText(srv.username)
        passwordInput.setText(srv.password)
        loginMessage.text = "🌐 ${srv.apiUrl}"
      }
      override fun onNothingSelected(p: AdapterView<*>?) {}
    }

    loginButton.setOnClickListener { attemptLogin() }
  }

  private fun attemptLogin() {
    val idx = serverSpinner.selectedItemPosition
    val srv = Config.servers[idx]
    val user = usernameInput.text.toString().trim()
    val pass = passwordInput.text.toString().trim()
    if (user.isBlank() || pass.isBlank()) { loginMessage.text = "أدخل اسم المستخدم وكلمة المرور"; return }

    setLoading(true)
    loginMessage.text = "جاري التحقق..."

    Thread {
      try {
        val url = "${srv.apiUrl}/player_api.php?action=get_live_streams&username=$user&password=$pass&limit=1"
        val req = Request.Builder().url(url).header("User-Agent", srv.userAgent).build()
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) { runOnUiThread { setLoading(false); loginMessage.text = "خطأ في السيرفر: ${res.code}" }; return@Thread }
        val body = res.body?.string() ?: ""
        if (!body.startsWith("[")) { runOnUiThread { setLoading(false); loginMessage.text = "بيانات الدخول غير صحيحة" }; return@Thread }

        Config.activeServer = srv.copy(username = user, password = pass)
        prefs.edit().putBoolean("logged_in", true).putInt("server_index", idx).apply()
        runOnUiThread { startActivity(Intent(this, MainActivity::class.java)); finish() }
      } catch (e: Exception) { runOnUiThread { setLoading(false); loginMessage.text = "فشل الاتصال: ${e.message}" } }
    }.start()
  }

  private fun setLoading(v: Boolean) {
    loginProgress.visibility = if (v) View.VISIBLE else View.GONE
    loginButton.isEnabled = !v
  }
}
