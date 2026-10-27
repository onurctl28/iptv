package com.benim.iptv

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** Film (VOD) veya dizi tarama ekranı; mod EXTRA_MODE ile belirlenir. */
class BrowseActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_VOD = "vod"
        const val MODE_SERIES = "series"
        private const val ALL_ID = "__all__"
    }

    private lateinit var client: XtreamClient
    private lateinit var mode: String
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var mediaAdapter: MediaAdapter
    private lateinit var emptyView: TextView

    private val catNameById = HashMap<String, String>()
    private var vodAll: List<Vod> = emptyList()
    private var seriesAll: List<Series> = emptyList()
    private var currentVod: List<Vod> = emptyList()
    private var currentSeries: List<Series> = emptyList()

    private var currentCategoryId: String = ALL_ID
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Prefs.hasLogin(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_VOD
        setContentView(R.layout.activity_browse)
        client = XtreamClient(Prefs.host(this), Prefs.user(this), Prefs.pass(this))

        findViewById<TextView>(R.id.tv_title).text =
            getString(if (mode == MODE_SERIES) R.string.series else R.string.movies)

        val catRv = findViewById<RecyclerView>(R.id.rv_categories)
        val itemRv = findViewById<RecyclerView>(R.id.rv_items)
        val search = findViewById<EditText>(R.id.et_search)
        val progress = findViewById<ProgressBar>(R.id.progress)
        emptyView = findViewById(R.id.tv_empty)

        categoryAdapter = CategoryAdapter { cat ->
            currentCategoryId = cat.id
            applyFilter()
        }
        catRv.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        catRv.adapter = categoryAdapter

        mediaAdapter = MediaAdapter { pos -> onTileClick(pos) }
        itemRv.setHasFixedSize(true)
        itemRv.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.channel_columns))
        itemRv.adapter = mediaAdapter

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                applyFilter()
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        loadData(progress)
    }

    private fun loadData(progress: ProgressBar) {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val cats: List<Category>
            if (mode == MODE_SERIES) {
                val cCats = DataCache.seriesCats
                val cItems = DataCache.seriesItems
                if (cCats != null && cItems != null) {
                    cats = cCats; seriesAll = cItems
                } else {
                    val a = async { client.seriesCategories() }
                    val b = async { client.seriesList() }
                    cats = a.await(); seriesAll = b.await()
                    DataCache.seriesCats = cats; DataCache.seriesItems = seriesAll
                }
            } else {
                val cCats = DataCache.vodCats
                val cItems = DataCache.vodItems
                if (cCats != null && cItems != null) {
                    cats = cCats; vodAll = cItems
                } else {
                    val a = async { client.vodCategories() }
                    val b = async { client.vodStreams() }
                    cats = a.await(); vodAll = b.await()
                    DataCache.vodCats = cats; DataCache.vodItems = vodAll
                }
            }

            catNameById.clear()
            for (c in cats) catNameById[c.id] = c.name

            val list = ArrayList<Category>()
            list.add(Category(ALL_ID, getString(R.string.all_items)))
            list.addAll(cats)
            categoryAdapter.submit(list, currentCategoryId)

            progress.visibility = View.GONE
            applyFilter()
        }
    }

    private fun applyFilter() {
        val tiles: List<Tile>
        if (mode == MODE_SERIES) {
            var list = if (currentCategoryId == ALL_ID) seriesAll
            else seriesAll.filter { it.categoryId == currentCategoryId }
            if (query.isNotEmpty()) list = list.filter { it.name.contains(query, ignoreCase = true) }
            currentSeries = list
            tiles = list.map { Tile(it.name, it.cover) }
        } else {
            var list = if (currentCategoryId == ALL_ID) vodAll
            else vodAll.filter { it.categoryId == currentCategoryId }
            if (query.isNotEmpty()) list = list.filter { it.name.contains(query, ignoreCase = true) }
            currentVod = list
            tiles = list.map { Tile(it.name, it.icon) }
        }
        mediaAdapter.submit(tiles)
        categoryAdapter.setSelected(currentCategoryId)
        emptyView.visibility = if (tiles.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onTileClick(pos: Int) {
        if (mode == MODE_SERIES) {
            val s = currentSeries.getOrNull(pos) ?: return
            startActivity(
                Intent(this, SeriesDetailActivity::class.java)
                    .putExtra(SeriesDetailActivity.EXTRA_SERIES_ID, s.seriesId)
                    .putExtra(SeriesDetailActivity.EXTRA_SERIES_NAME, s.name)
            )
        } else {
            val items = currentVod.map { v ->
                PlayItem(
                    name = v.name,
                    url = client.vodUrl(v.streamId, v.ext),
                    logo = v.icon,
                    info = vodInfoLines(v, catNameById[v.categoryId]),
                    epgStreamId = null,
                    number = ""
                )
            }
            Playlist.items = items
            Playlist.index = pos.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            startActivity(Intent(this, PlayerActivity::class.java))
        }
    }
}
