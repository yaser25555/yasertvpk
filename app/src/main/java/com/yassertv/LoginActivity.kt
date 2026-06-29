package com.yassertv

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yassertv.data.AuthApiClient

class LoginActivity : AppCompatActivity() {
  private val prefs by lazy { getSharedPreferences("auth", MODE_PRIVATE) }

  private lateinit var usernameInput: EditText
  private lateinit var passwordInput: EditText
  private lateinit var loginButton: Button
  private lateinit var loginProgress: ProgressBar
  private lateinit var loginMessage: TextView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_login)

    usernameInput = findViewById(R.id.usernameInput)
    passwordInput = findViewById(R.id.passwordInput)
    loginButton = findViewById(R.id.loginButton)
    loginProgress = findViewById(R.id.loginProgress)
    loginMessage = findViewById(R.id.loginMessage)

    findViewById<ImageButton>(R.id.btnWhatsApp).setOnClickListener {
      try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/966558570889"))) } catch (_: Exception) {}
    }
    findViewById<ImageButton>(R.id.btnTelegram).setOnClickListener {
      try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/iptvyaser"))) } catch (_: Exception) {}
    }

    if (prefs.getBoolean("logged_in", false)) {
      val token = prefs.getString("device_key", "")
      if (token.isNullOrBlank()) {
        prefs.edit().clear().apply()
      } else {
        setLoading(true)
        AuthApiClient.verifySession(
          savedToken = token,
          onValid = { _ -> runOnUiThread { openMain() } },
          onInvalid = { reason -> runOnUiThread {
            prefs.edit().clear().apply()
            setLoading(false)
            if (reason == "ACCOUNT_EXPIRED") showMessage("انتهت صلاحية الحساب")
          } }
        )
        return
      }
    }

    loginButton.setOnClickListener { attemptLogin() }
    passwordInput.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        attemptLogin()
        true
      } else {
        false
      }
    }

    setupLoginUi()
  }

  private fun setupLoginUi() {
    if (isTvDevice()) {
      usernameInput.showSoftInputOnFocus = false
      passwordInput.showSoftInputOnFocus = false

      val openKeyboardOnSelect = { view: EditText ->
        view.setOnKeyListener { v, keyCode, event ->
          if (event.action == KeyEvent.ACTION_DOWN &&
            (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
          ) {
            v.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            true
          } else {
            false
          }
        }
      }
      openKeyboardOnSelect(usernameInput)
      openKeyboardOnSelect(passwordInput)

      usernameInput.requestFocus()
    } else {
      usernameInput.requestFocus()
    }
  }

  private fun isTvDevice(): Boolean {
    return (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
      Configuration.UI_MODE_TYPE_TELEVISION
  }

  private fun attemptLogin() {
    val username = usernameInput.text?.toString()?.trim().orEmpty()
    val password = passwordInput.text?.toString()?.trim().orEmpty()

    if (username.isBlank() || password.isBlank()) {
      showMessage(getString(R.string.login_missing_fields))
      return
    }

    setLoading(true)
    loginMessage.text = ""

    AuthApiClient.login(
      context = this,
      username = username,
      password = password,
      onSuccess = { response ->
        runOnUiThread {
          prefs.edit()
            .putBoolean("logged_in", true)
            .putString("username", username)
            .putString("device_key", response.deviceKey)
            .putString("expire_date", response.expireDate)
            .apply()
          setLoading(false)
          onLoginSuccess(response.expireDate)
        }
      },
      onError = { error ->
        runOnUiThread {
          setLoading(false)
          val message = when {
            error == "AUTH_URL_NOT_CONFIGURED" -> getString(R.string.login_server_not_configured)
            error == "LOGIN_FAILED" || error.contains("Invalid", ignoreCase = true) -> getString(R.string.login_failed)
            error.startsWith("ACCOUNT_EXPIRED") -> "انتهت صلاحية الحساب"
            else -> getString(R.string.login_connection_error)
          }
          showMessage(message)
        }
      }
    )
  }

  private fun onLoginSuccess(expireDate: String) {
    if (isTvDevice()) {
      Toast.makeText(this, "مرحباً — تاريخ الانتهاء: $expireDate", Toast.LENGTH_LONG).show()
      openMain()
      return
    }
    showWelcomeDialog(expireDate)
  }

  private fun showWelcomeDialog(expireDate: String) {
    val view = layoutInflater.inflate(R.layout.dialog_welcome, null)
    view.findViewById<TextView>(R.id.welcomeExpire).text = "تاريخ الانتهاء: $expireDate"
    AlertDialog.Builder(this)
      .setView(view)
      .setCancelable(false)
      .setPositiveButton("دخول") { _, _ -> openMain() }
      .show()
  }

  private fun setLoading(loading: Boolean) {
    loginProgress.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
    loginButton.isEnabled = !loading
    usernameInput.isEnabled = !loading
    passwordInput.isEnabled = !loading
  }

  private fun showMessage(message: String) {
    loginMessage.text = message
  }

  private fun openMain() {
    startActivity(Intent(this, MainActivity::class.java))
    finish()
  }
}
