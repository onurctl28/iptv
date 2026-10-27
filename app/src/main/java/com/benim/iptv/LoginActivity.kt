package com.benim.iptv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Daha önce giriş yapıldıysa doğrudan seçim ekranına geç
        if (Prefs.hasLogin(this)) {
            goHome()
            return
        }

        setContentView(R.layout.activity_login)

        val hostEt = findViewById<EditText>(R.id.et_host)
        val userEt = findViewById<EditText>(R.id.et_user)
        val passEt = findViewById<EditText>(R.id.et_pass)
        val btn = findViewById<Button>(R.id.btn_login)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val error = findViewById<TextView>(R.id.tv_error)

        btn.setOnClickListener {
            val host = hostEt.text.toString().trim()
            val user = userEt.text.toString().trim()
            val pass = passEt.text.toString().trim()

            if (host.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                error.text = getString(R.string.fill_all)
                error.visibility = View.VISIBLE
                return@setOnClickListener
            }

            error.visibility = View.GONE
            progress.visibility = View.VISIBLE
            btn.isEnabled = false

            lifecycleScope.launch {
                val client = XtreamClient(host, user, pass)
                val result = client.login()
                progress.visibility = View.GONE
                btn.isEnabled = true
                if (result.ok) {
                    Prefs.saveLogin(this@LoginActivity, client.base, user, pass)
                    DataCache.clear()
                    goHome()
                } else {
                    error.text = result.message.ifEmpty { getString(R.string.login_failed) }
                    error.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
