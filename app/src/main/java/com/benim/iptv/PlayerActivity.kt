package com.benim.iptv

import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import coil.load
import kotlinx.coroutines.launch
import java.io.File

/**
 * Türden bağımsız oynatıcı (canlı kanal / film / dizi bölümü).
 * - Açılışta öğenin tüm bilgilerini (logo, ad, bilgiler, canlıda EPG) 5 sn gösterir.
 * - Kumanda: AŞAĞI = sonraki, YUKARI = önceki, rakam tuşları = numarayla geçiş, SES +/- = ses.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val MAX_RETRY = 5
        private const val PANEL_MS = 5000L
        private const val NUM_COMMIT_MS = 2500L
    }

    private lateinit var client: XtreamClient
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null

    private var items: List<PlayItem> = emptyList()
    private var index: Int = 0
    private var retryCount = 0
    private var extSubFile: File? = null
    private var extSubIndex: Int = -1

    private val handler = Handler(Looper.getMainLooper())
    private val hidePanel = Runnable { findViewById<View>(R.id.info_panel).visibility = View.GONE }
    private val numBuffer = StringBuilder()
    private val commitNum = Runnable { commitNumber() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        volumeControlStream = AudioManager.STREAM_MUSIC

        client = XtreamClient(Prefs.host(this), Prefs.user(this), Prefs.pass(this))
        items = Playlist.items
        index = Playlist.index.coerceIn(0, (items.size - 1).coerceAtLeast(0))

        if (items.isEmpty()) {
            finish()
            return
        }

        findViewById<View>(R.id.btn_sub).setOnClickListener { downloadSubtitleForCurrent() }

        bindUi(items[index])
        showPanel()
        loadEpg(items[index])
    }

    private fun current(): PlayItem? = items.getOrNull(index)

    private fun bindUi(item: PlayItem) {
        findViewById<TextView>(R.id.tv_name).text = item.name

        val logoView = findViewById<ImageView>(R.id.iv_logo)
        if (item.logo.isNotEmpty()) {
            logoView.load(item.logo) {
                placeholder(R.drawable.ic_tv)
                error(R.drawable.ic_tv)
            }
        } else {
            logoView.setImageResource(R.drawable.ic_tv)
        }

        findViewById<TextView>(R.id.tv_meta).text = item.info.joinToString("\n")
    }

    private fun showPanel() {
        findViewById<View>(R.id.info_panel).visibility = View.VISIBLE
        handler.removeCallbacks(hidePanel)
        handler.postDelayed(hidePanel, PANEL_MS)
    }

    /** Canlı yayında "Şimdi / Sonra" program bilgisini EPG'den çekip ekler. */
    private fun loadEpg(item: PlayItem) {
        val sid = item.epgStreamId
        if (sid.isNullOrEmpty()) return
        val baseMeta = item.info.joinToString("\n")
        lifecycleScope.launch {
            val epg = client.shortEpg(sid, 2)
            if (current()?.epgStreamId != sid || epg.isEmpty()) return@launch
            val sb = StringBuilder(baseMeta)
            epg.getOrNull(0)?.let { sb.append("\nŞimdi: ${it.start}-${it.end}  ${it.title}") }
            epg.getOrNull(1)?.let { sb.append("\nSonra: ${it.start}-${it.end}  ${it.title}") }
            findViewById<TextView>(R.id.tv_meta).text = sb.toString()
        }
    }

    /** delta=+1 sonraki, -1 önceki (liste baş/sonunda döner). */
    private fun zap(delta: Int) {
        if (items.isEmpty()) return
        index = ((index + delta) % items.size + items.size) % items.size
        goTo(index)
    }

    private fun goTo(newIndex: Int) {
        index = newIndex
        val item = items[index]
        retryCount = 0
        bindUi(item)
        showPanel()
        loadEpg(item)
        playCurrent()
    }

    private fun playCurrent() {
        val exo = player ?: return
        exo.setMediaItem(buildMediaItem())
        exo.playWhenReady = true
        exo.prepare()
    }

    /** O anki öğe için, varsa indirilen harici altyazıyı da iliştirerek MediaItem üretir. */
    private fun buildMediaItem(): MediaItem {
        val item = items[index]
        val builder = MediaItem.Builder().setUri(item.url)
        val sub = extSubFile
        if (sub != null && extSubIndex == index) {
            val cfg = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(sub))
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage(Prefs.osLang(this))
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            builder.setSubtitleConfigurations(listOf(cfg))
        }
        return builder.build()
    }

    /** O anki içerik için OpenSubtitles'tan altyazı arayıp indirir ve yükler. */
    private fun downloadSubtitleForCurrent() {
        val info = findViewById<TextView>(R.id.tv_info)
        val key = Prefs.osKey(this)
        val user = Prefs.osUser(this)
        val pass = Prefs.osPass(this)
        val lang = Prefs.osLang(this)
        if (key.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            info.visibility = View.VISIBLE
            info.text = getString(R.string.sub_need_key)
            handler.postDelayed({ info.visibility = View.GONE }, 3000)
            return
        }
        val item = current() ?: return
        val query = cleanQuery(item.name)
        val targetIndex = index
        info.visibility = View.VISIBLE
        info.text = getString(R.string.sub_searching)
        lifecycleScope.launch {
            val token = OpenSubtitles.login(key, user, pass)
            if (token == null) {
                info.text = getString(R.string.sub_login_fail); return@launch
            }
            val found = OpenSubtitles.search(key, query, lang)
            val link = found?.let { OpenSubtitles.downloadLink(key, token, it.fileId) }
            if (link == null) {
                info.text = getString(R.string.sub_none)
                handler.postDelayed({ info.visibility = View.GONE }, 2500)
                return@launch
            }
            val dest = File(cacheDir, "sub_${System.currentTimeMillis()}.srt")
            val ok = OpenSubtitles.fetchToFile(link, dest)
            if (!ok || index != targetIndex) {
                if (!ok) {
                    info.text = getString(R.string.sub_none)
                    handler.postDelayed({ info.visibility = View.GONE }, 2500)
                }
                return@launch
            }
            extSubFile = dest
            extSubIndex = targetIndex
            val pos = player?.currentPosition ?: 0L
            player?.setMediaItem(buildMediaItem())
            player?.prepare()
            if (pos > 0) player?.seekTo(pos)
            player?.play()
            info.text = getString(R.string.sub_loaded)
            handler.postDelayed({ info.visibility = View.GONE }, 2000)
        }
    }

    /** Dizi için "Ad — S1 B2 · Başlık" biçimindeki addan dizi adını ayıklar. */
    private fun cleanQuery(name: String): String {
        val dash = name.indexOf(" — ")
        return if (dash > 0) name.substring(0, dash) else name
    }

    private fun onDigit(d: Int) {
        if (numBuffer.length >= 4) numBuffer.clear()
        numBuffer.append(d)
        val tv = findViewById<TextView>(R.id.tv_numinput)
        tv.text = numBuffer.toString()
        tv.visibility = View.VISIBLE
        handler.removeCallbacks(commitNum)
        handler.postDelayed(commitNum, NUM_COMMIT_MS)
    }

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
            goTo(pos)
        } else {
            tv.text = getString(R.string.channel_not_found)
            handler.postDelayed({ tv.visibility = View.GONE }, 1500)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val code = event.keyCode
            if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                onDigit(code - KeyEvent.KEYCODE_0)
                return true
            }
            // Kumanda çubuğu açıkken yön tuşları çubuğu yönetsin (ses/altyazı seçimi için)
            val controllerOpen = playerView?.isControllerFullyVisible == true
            when (code) {
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (controllerOpen) return super.dispatchKeyEvent(event)
                    zap(1); return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    if (controllerOpen) return super.dispatchKeyEvent(event)
                    zap(-1); return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> { adjustVolume(AudioManager.ADJUST_RAISE); return true }
                KeyEvent.KEYCODE_VOLUME_DOWN -> { adjustVolume(AudioManager.ADJUST_LOWER); return true }
                KeyEvent.KEYCODE_CAPTIONS, KeyEvent.KEYCODE_MENU -> {
                    downloadSubtitleForCurrent(); return true
                }
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
        val item = current() ?: return

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 50000, 2500, 5000)
            .build()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(XtreamClient.UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
        // Hem http akışı hem de yerel (file://) altyazı dosyasını okuyabilmek için
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)

        val exo = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()

        val view = findViewById<PlayerView>(R.id.player_view)
        view.player = exo
        view.keepScreenOn = true
        view.setShowSubtitleButton(true)
        applySubtitleStyle(view)
        playerView = view

        exo.setMediaItem(buildMediaItem())
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

    /** Ayarlardaki altyazı görünümünü oynatıcıya uygula. */
    private fun applySubtitleStyle(view: PlayerView) {
        val sv = view.subtitleView ?: return
        val fg = if (Prefs.subColor(this) == 1) Color.parseColor("#FFEB3B") else Color.WHITE
        val boxBg = if (Prefs.subBg(this)) Color.parseColor("#99000000") else Color.TRANSPARENT
        val edgeType =
            if (Prefs.subEdge(this)) CaptionStyleCompat.EDGE_TYPE_OUTLINE
            else CaptionStyleCompat.EDGE_TYPE_NONE
        sv.setApplyEmbeddedStyles(false)
        sv.setStyle(CaptionStyleCompat(fg, boxBg, Color.TRANSPARENT, edgeType, Color.BLACK, null))
        val frac = when (Prefs.subSize(this)) {
            0 -> 0.05f; 1 -> 0.08f; 2 -> 0.11f; else -> 0.14f
        }
        sv.setFractionalTextSize(frac)
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }
}
