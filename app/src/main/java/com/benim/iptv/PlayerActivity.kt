package com.benim.iptv

import android.media.AudioManager
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
 * Canlı yayın oynatıcı.
 * - Açılışta kanalın sunucudan gelen tüm bilgilerini (logo, ad, kategori, EPG) 5 sn gösterir.
 * - Kumanda: AŞAĞI = sonraki kanal, YUKARI = önceki kanal, SES +/- = ses ayarı.
 * - Donanımsal kod çözme, akıcılık için ayarlı tampon, hatada otomatik yeniden deneme.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val MAX_RETRY = 5
        private const val PANEL_MS = 5000L
        private const val NUM_COMMIT_MS = 2500L
    }

    private lateinit var client: XtreamClient
    private var player: ExoPlayer? = null

    private var items: List<Channel> = emptyList()
    private var index: Int = 0
    private var retryCount = 0

    private val handler = Handler(Looper.getMainLooper())
    private val hidePanel = Runnable { findViewById<View>(R.id.info_panel).visibility = View.GONE }

    private val numBuffer = StringBuilder()
    private val commitNum = Runnable { commitNumber() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        // Ses tuşları medya sesini ayarlasın
        volumeControlStream = AudioManager.STREAM_MUSIC

        client = XtreamClient(Prefs.host(this), Prefs.user(this), Prefs.pass(this))
        items = Playlist.items
        index = Playlist.index.coerceIn(0, (items.size - 1).coerceAtLeast(0))

        if (items.isEmpty()) {
            finish()
            return
        }

        bindChannelUi(items[index])
        showPanel()
        loadEpg(items[index])
    }

    private fun currentChannel(): Channel? = items.getOrNull(index)

    /** Panelde kanal adı, logo ve tüm bilgileri göster. */
    private fun bindChannelUi(ch: Channel) {
        findViewById<TextView>(R.id.tv_name).text = ch.name

        val logoView = findViewById<ImageView>(R.id.iv_logo)
        if (ch.icon.isNotEmpty()) {
            logoView.load(ch.icon) {
                placeholder(R.drawable.ic_tv)
                error(R.drawable.ic_tv)
            }
        } else {
            logoView.setImageResource(R.drawable.ic_tv)
        }

        val catName = Playlist.catNameById[ch.categoryId]
        findViewById<TextView>(R.id.tv_meta).text =
            channelInfoLines(ch, catName).joinToString("\n")

        // Açılışta devam edebilmek için son izlenen kanalı kaydet
        Prefs.saveLastChannel(this, ch.streamId)
    }

    /** Kumandadan yazılan rakamı biriktir ve göster. */
    private fun onDigit(d: Int) {
        if (numBuffer.length >= 4) numBuffer.clear()
        numBuffer.append(d)
        val tv = findViewById<TextView>(R.id.tv_numinput)
        tv.text = numBuffer.toString()
        tv.visibility = View.VISIBLE
        handler.removeCallbacks(commitNum)
        handler.postDelayed(commitNum, NUM_COMMIT_MS)
    }

    /** Yazılan "Sıra No"ya sahip kanala geç. */
    private fun commitNumber() {
        val target = numBuffer.toString()
        numBuffer.clear()
        val tv = findViewById<TextView>(R.id.tv_numinput)
        if (target.isEmpty()) {
            tv.visibility = View.GONE
            return
        }
        val pos = items.indexOfFirst { it.number == target }
        if (pos >= 0) {
            tv.visibility = View.GONE
            index = pos
            val ch = items[index]
            retryCount = 0
            bindChannelUi(ch)
            showPanel()
            loadEpg(ch)
            playCurrent()
        } else {
            tv.text = getString(R.string.channel_not_found)
            handler.postDelayed({ tv.visibility = View.GONE }, 1500)
        }
    }

    /** Paneli göster ve 5 sn sonra gizle. */
    private fun showPanel() {
        findViewById<View>(R.id.info_panel).visibility = View.VISIBLE
        handler.removeCallbacks(hidePanel)
        handler.postDelayed(hidePanel, PANEL_MS)
    }

    /** "Şimdi / Sonra" program bilgisini EPG'den çekip panele ekler (yarış korumalı). */
    private fun loadEpg(ch: Channel) {
        if (ch.streamId.isEmpty()) return
        val baseMeta = findViewById<TextView>(R.id.tv_meta).text.toString()
        lifecycleScope.launch {
            val epg = client.shortEpg(ch.streamId, 2)
            // Kullanıcı bu arada başka kanala geçtiyse ekleme
            if (currentChannel()?.streamId != ch.streamId || epg.isEmpty()) return@launch
            val sb = StringBuilder(baseMeta)
            epg.getOrNull(0)?.let { sb.append("\nŞimdi: ${it.start}-${it.end}  ${it.title}") }
            epg.getOrNull(1)?.let { sb.append("\nSonra: ${it.start}-${it.end}  ${it.title}") }
            findViewById<TextView>(R.id.tv_meta).text = sb.toString()
        }
    }

    /** delta=+1 sonraki, -1 önceki kanal (liste baş/sonunda döner). */
    private fun zap(delta: Int) {
        if (items.isEmpty()) return
        index = ((index + delta) % items.size + items.size) % items.size
        val ch = items[index]
        retryCount = 0
        bindChannelUi(ch)
        showPanel()
        loadEpg(ch)
        playCurrent()
    }

    private fun playCurrent() {
        val ch = currentChannel() ?: return
        val exo = player ?: return
        exo.setMediaItem(MediaItem.fromUri(client.liveUrl(ch.streamId)))
        exo.playWhenReady = true
        exo.prepare()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val code = event.keyCode
            if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                onDigit(code - KeyEvent.KEYCODE_0)
                return true
            }
            when (code) {
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(1); return true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { zap(-1); return true }
                KeyEvent.KEYCODE_VOLUME_UP -> { adjustVolume(AudioManager.ADJUST_RAISE); return true }
                KeyEvent.KEYCODE_VOLUME_DOWN -> { adjustVolume(AudioManager.ADJUST_LOWER); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO -> showPanel()
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun adjustVolume(direction: Int) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(hidePanel)
        handler.removeCallbacks(commitNum)
        releasePlayer()
    }

    private fun initPlayer() {
        val ch = currentChannel() ?: return

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 50000, 2500, 5000)
            .build()

        val exo = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()

        val view = findViewById<PlayerView>(R.id.player_view)
        view.player = exo
        view.keepScreenOn = true

        exo.setMediaItem(MediaItem.fromUri(client.liveUrl(ch.streamId)))
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
