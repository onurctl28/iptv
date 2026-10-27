package com.benim.iptv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

/** Bir dizinin bölüm listesi; bölüme basınca oynatıcıya geçer. */
class SeriesDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SERIES_ID = "series_id"
        const val EXTRA_SERIES_NAME = "series_name"
    }

    private lateinit var client: XtreamClient
    private lateinit var adapter: EpisodeAdapter
    private lateinit var emptyView: TextView
    private var seriesName: String = ""
    private var episodes: List<Episode> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Prefs.hasLogin(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_series_detail)
        client = XtreamClient(Prefs.host(this), Prefs.user(this), Prefs.pass(this))

        val seriesId = intent.getStringExtra(EXTRA_SERIES_ID) ?: ""
        seriesName = intent.getStringExtra(EXTRA_SERIES_NAME) ?: ""
        findViewById<TextView>(R.id.tv_title).text = seriesName

        val rv = findViewById<RecyclerView>(R.id.rv_episodes)
        val progress = findViewById<ProgressBar>(R.id.progress)
        emptyView = findViewById(R.id.tv_empty)

        adapter = EpisodeAdapter { pos -> openEpisode(pos) }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        if (seriesId.isEmpty()) {
            progress.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            return
        }

        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            episodes = client.seriesEpisodes(seriesId)
            adapter.submit(episodes)
            progress.visibility = View.GONE
            emptyView.visibility = if (episodes.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openEpisode(pos: Int) {
        if (episodes.isEmpty()) return
        val items = episodes.map { e ->
            val label = buildString {
                if (e.season.isNotEmpty()) append("S${e.season} ")
                if (e.epNum.isNotEmpty()) append("B${e.epNum}")
                if (e.title.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(e.title)
                }
            }
            PlayItem(
                name = if (label.isNotEmpty()) "$seriesName — $label" else seriesName,
                url = client.episodeUrl(e.id, e.ext),
                logo = "",
                info = episodeInfoLines(e, seriesName),
                epgStreamId = null,
                number = ""
            )
        }
        Playlist.items = items
        Playlist.index = pos.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        startActivity(Intent(this, PlayerActivity::class.java))
    }
}
