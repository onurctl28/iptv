package com.benim.iptv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.load
import kotlinx.coroutines.launch

/**
 * Canlı yayını Media3 / ExoPlayer ile oynatır.
 * Açılışta kanalın sunucudan gelen tüm bilgilerini (logo, ad, kategori, EPG
 * "şimdi/sonra" vb.) 5 saniye gösterir. Donanımsal kod çözme etkindir;
 * tampon süreleri akıcılık için ayarlı, hata durumunda otomatik yeniden denenir.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
        const val EXTRA_LOGO = "logo"
        const val EXTRA_STREAM_ID = "stream_id"
        const val EXTRA_INFO = "info"
        private const val MAX_RETRY = 5
        private const val PANEL_MS = 5000L
    }

    private var player: ExoPlayer? = null
    private var url: String = ""
    private var streamId: String = ""
    private var baseMeta: String = ""
    private var retryCount = 0

    private val handler = Handler(Looper.getMainLooper())
    private val hidePanel = Runnable { findViewById<View>(R.id.info_panel).visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        url = intent.getStringExtra(EXTRA_URL) ?: ""
        streamId = intent.getStringExtra(EXTRA_STREAM_ID) ?: ""
        val name = intent.getStringExtra(EXTRA_NAME) ?: ""
        val logo = intent.getStringExtra(EXTRA_LOGO) ?: ""
        val info = intent.getStringArrayListExtra(EXTRA_INFO) ?: arrayListOf()

        findViewById<TextView>(R.id.tv_name).text = name

        val logoView = findViewById<ImageView>(R.id.iv_logo)
        if (logo.isNotEmpty()) {
            logoView.load(logo) {
                placeholder(R.drawable.ic_tv)
                error(R.drawable.ic_tv)
            }
        } else {
            logoView.setImageResource(R.drawable.ic_tv)
        }

        baseMeta = info.joinToString("\n")
        findViewById<TextView>(R.id.tv_meta).text = baseMeta

        showPanel()
        loadEpg()
    }

    /** Paneli göster ve 5 saniye sonra gizlemeyi planla. */
    private fun showPanel() {
        findViewById<View>(R.id.info_panel).visibility = View.VISIBLE
        handler.removeCallbacks(hidePanel)
        handler.postDelayed(hidePanel, PANEL_MS)
    }

    /** "Şimdi / Sonra" program bilgisini EPG'den çekip panele ekler. */
    private fun loadEpg() {
        if (streamId.isEmpty()) return
        val client = XtreamClient(Prefs.host(this), Prefs.user(this), Prefs.pass(this))
        lifecycleScope.launch {
            val epg = client.shortEpg(streamId, 2)
            if (epg.isEmpty()) return@launch
            val sb = StringBuilder(baseMeta)
            epg.getOrNull(0)?.let { sb.append("\nŞimdi: ${it.start}-${it.end}  ${it.title}") }
            epg.getOrNull(1)?.let { sb.append("\nSonra: ${it.start}-${it.end}  ${it.title}") }
            findViewById<TextView>(R.id.tv_meta).text = sb.toString()
        }
    }

    /** Kumandada bir tuşa basılınca bilgi panelini tekrar 5 sn göster. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_INFO -> showPanel()
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(hidePanel)
        releasePlayer()
    }

    private fun initPlayer() {
        if (url.isEmpty()) return

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 50000, 2500, 5000)
            .build()

        val exo = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()

        val view = findViewById<PlayerView>(R.id.player_view)
        view.player = exo
        view.keepScreenOn = true

        exo.setMediaItem(MediaItem.fromUri(url))
        exo.playWhenReady = true
        exo.prepare()

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val info = findViewById<TextView>(R.id.tv_info)
                if (retryCount < MAX_RETRY) {
                    retryCount++
                    info.visibility = View.VISIBLE
                    info.text = getString(R.string.reconnecting)
                    exo.prepare()
                    exo.play()
                } else {
                    info.visibility = View.VISIBLE
                    info.text = getString(R.string.play_error)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val info = findViewById<TextView>(R.id.tv_info)
                when (playbackState) {
                    Player.STATE_READY -> {
                        retryCount = 0
                        info.visibility = View.GONE
                    }
                    Player.STATE_BUFFERING -> {
                        info.visibility = View.VISIBLE
                        info.text = getString(R.string.buffering)
                    }
                    else -> { /* yok */ }
                }
            }
        })

        player = exo
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }
}
