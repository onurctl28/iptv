package com.benim.iptv

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Altyazı görünüm ayarları; seçimler anında kaydedilir ve önizlemeye yansır. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var preview: TextView
    private lateinit var sizeChips: List<TextView>
    private lateinit var colorChips: List<TextView>
    private lateinit var bgChips: List<TextView>
    private lateinit var edgeChips: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        preview = findViewById(R.id.tv_preview)
        sizeChips = listOf(R.id.size0, R.id.size1, R.id.size2, R.id.size3).map { findViewById(it) }
        colorChips = listOf(R.id.color0, R.id.color1).map { findViewById(it) }
        bgChips = listOf(R.id.bg_on, R.id.bg_off).map { findViewById(it) }
        edgeChips = listOf(R.id.edge_on, R.id.edge_off).map { findViewById(it) }

        sizeChips.forEachIndexed { i, v -> v.setOnClickListener { Prefs.setSubSize(this, i); refresh() } }
        colorChips.forEachIndexed { i, v -> v.setOnClickListener { Prefs.setSubColor(this, i); refresh() } }
        bgChips.forEachIndexed { i, v -> v.setOnClickListener { Prefs.setSubBg(this, i == 0); refresh() } }
        edgeChips.forEachIndexed { i, v -> v.setOnClickListener { Prefs.setSubEdge(this, i == 0); refresh() } }

        // Telefon <-> TV eşitleme
        findViewById<View>(R.id.btn_sync_receive).setOnClickListener {
            startActivity(android.content.Intent(this, SyncReceiveActivity::class.java))
        }
        findViewById<View>(R.id.btn_sync_send).setOnClickListener {
            startActivity(android.content.Intent(this, SyncSendActivity::class.java))
        }

        // OpenSubtitles alanları
        val keyEt = findViewById<EditText>(R.id.et_os_key)
        val userEt = findViewById<EditText>(R.id.et_os_user)
        val passEt = findViewById<EditText>(R.id.et_os_pass)
        val langEt = findViewById<EditText>(R.id.et_os_lang)
        keyEt.setText(Prefs.osKey(this))
        userEt.setText(Prefs.osUser(this))
        passEt.setText(Prefs.osPass(this))
        langEt.setText(Prefs.osLang(this))
        findViewById<TextView>(R.id.btn_os_save).setOnClickListener {
            Prefs.saveOpenSubtitles(
                this,
                keyEt.text.toString().trim(),
                userEt.text.toString().trim(),
                passEt.text.toString().trim(),
                langEt.text.toString().trim()
            )
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }

        refresh()
    }

    private fun refresh() {
        val size = Prefs.subSize(this)
        val color = Prefs.subColor(this)
        val bg = Prefs.subBg(this)
        val edge = Prefs.subEdge(this)

        select(sizeChips, size)
        select(colorChips, color)
        select(bgChips, if (bg) 0 else 1)
        select(edgeChips, if (edge) 0 else 1)

        // Önizleme
        preview.textSize = when (size) {
            0 -> 16f; 1 -> 22f; 2 -> 28f; else -> 34f
        }
        preview.setTextColor(if (color == 1) Color.parseColor("#FFEB3B") else Color.WHITE)
        preview.setBackgroundColor(if (bg) Color.parseColor("#99000000") else Color.TRANSPARENT)
        if (edge) {
            preview.setShadowLayer(6f, 0f, 0f, Color.BLACK)
            preview.setTypeface(preview.typeface, Typeface.BOLD)
        } else {
            preview.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            preview.setTypeface(Typeface.DEFAULT)
        }
    }

    private fun select(chips: List<View>, index: Int) {
        chips.forEachIndexed { i, v -> v.isSelected = (i == index) }
    }
}
