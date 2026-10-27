package com.benim.iptv

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Canlı yayını Media3 / ExoPlayer ile oynatır.
 * Donanımsal kod çözme varsayılan olarak etkindir; tampon süreleri
 * zayıf bağlantılarda takılmayı azaltacak şekilde ayarlanmıştır ve
 * hata durumunda yayın birkaç kez otomatik yeniden denenir.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
        private const val MAX_RETRY = 5
    }

    private var player: ExoPlayer? = null
    private var url: String = ""
    private var retryCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        url = intent.getStringExtra(EXTRA_URL) ?: ""
        val name = intent.getStringExtra(EXTRA_NAME) ?: ""
        findViewById<TextView>(R.id.tv_title).text = name
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun initPlayer() {
        if (url.isEmpty()) return

        // Tampon: min 15 sn, hedef 50 sn, oynatmaya başlama 2.5 sn,
        // yeniden tamponlama sonrası 5 sn — canlı yayında akıcılık için.
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
                if (retryCount < MAX_RETRY) {
                    retryCount++
                    val info = findViewById<TextView>(R.id.tv_info)
                    info.visibility = View.VISIBLE
                    info.text = getString(R.string.reconnecting)
                    exo.prepare()
                    exo.play()
                } else {
                    val info = findViewById<TextView>(R.id.tv_info)
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
