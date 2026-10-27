package com.benim.iptv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Açılış seçim ekranı: Canlı TV / Filmler / Diziler. */
class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Prefs.hasLogin(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_home)

        findViewById<View>(R.id.card_live).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<View>(R.id.card_movies).setOnClickListener {
            startActivity(Intent(this, BrowseActivity::class.java)
                .putExtra(BrowseActivity.EXTRA_MODE, BrowseActivity.MODE_VOD))
        }
        findViewById<View>(R.id.card_series).setOnClickListener {
            startActivity(Intent(this, BrowseActivity::class.java)
                .putExtra(BrowseActivity.EXTRA_MODE, BrowseActivity.MODE_SERIES))
        }
        findViewById<TextView>(R.id.btn_logout).setOnClickListener {
            Prefs.clear(this)
            DataCache.clear()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
