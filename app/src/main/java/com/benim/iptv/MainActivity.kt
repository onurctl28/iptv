package com.benim.iptv

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** Canlı TV: kategoriler + kanal listesi + arama + favori. */
class MainActivity : AppCompatActivity() {

    private lateinit var client: XtreamClient
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var emptyView: TextView

    private var allChannels: List<Channel> = emptyList()
    private var currentList: List<Channel> = emptyList()
    private val catNameById = HashMap<String, String>()
    private var currentCategoryId: String = FAV_ID
    private var query: String = ""

    companion object {
        const val FAV_ID = "__fav__"
        const val ALL_ID = "__all__"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Prefs.hasLogin(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        client = XtreamClient(Prefs.host(this), Prefs.user(this), Prefs.pass(this))

        val catRv = findViewById<RecyclerView>(R.id.rv_categories)
        val chRv = findViewById<RecyclerView>(R.id.rv_channels)
        val search = findViewById<EditText>(R.id.et_search)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val logout = findViewById<ImageView>(R.id.btn_logout)
        emptyView = findViewById(R.id.tv_empty)

        categoryAdapter = CategoryAdapter { cat ->
            currentCategoryId = cat.id
            applyFilter()
        }
        catRv.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        catRv.adapter = categoryAdapter

        channelAdapter = ChannelAdapter(
            onClick = { ch -> openPlayer(ch) },
            onFav = { ch ->
                Prefs.toggleFav(this, ch.streamId)
                if (currentCategoryId == FAV_ID) applyFilter()
            },
            isFav = { id -> Prefs.isFav(this, id) },
            onLongPress = { ch -> showFavDialog(ch) }
        )
        chRv.setHasFixedSize(true)
        chRv.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.channel_columns))
        chRv.adapter = channelAdapter

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                applyFilter()
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        logout.setOnClickListener {
            Prefs.clear(this)
            DataCache.clear()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        loadData(progress)
    }

    override fun onResume() {
        super.onResume()
        if (allChannels.isNotEmpty()) channelAdapter.notifyDataSetChanged()
    }

    private fun loadData(progress: ProgressBar) {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            // Önbellekte varsa ağdan yeniden çekme (hızlı açılış)
            val cats: List<Category>
            val chans: List<Channel>
            val cachedCats = DataCache.liveCats
            val cachedChans = DataCache.liveChannels
            if (cachedCats != null && cachedChans != null) {
                cats = cachedCats
                chans = cachedChans
            } else {
                // Kategori ve kanalları paralel çek
                val catsDef = async { client.categories() }
                val chansDef = async { client.channels() }
                cats = catsDef.await()
                chans = chansDef.await()
                DataCache.liveCats = cats
                DataCache.liveChannels = chans
            }

            allChannels = chans
            catNameById.clear()
            for (c in cats) catNameById[c.id] = c.name

            val list = ArrayList<Category>()
            list.add(Category(FAV_ID, getString(R.string.favorites)))
            list.add(Category(ALL_ID, getString(R.string.all_channels)))
            list.addAll(cats)
            categoryAdapter.submit(list, currentCategoryId)

            progress.visibility = View.GONE
            applyFilter()
        }
    }

    private fun applyFilter() {
        var list = when (currentCategoryId) {
            FAV_ID -> allChannels.filter { Prefs.isFav(this, it.streamId) }
            ALL_ID -> allChannels
            else -> allChannels.filter { it.categoryId == currentCategoryId }
        }
        if (query.isNotEmpty()) {
            list = list.filter { it.name.contains(query, ignoreCase = true) }
        }
        currentList = list
        channelAdapter.submit(list)
        categoryAdapter.setSelected(currentCategoryId)
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    /** OK'a basılı tutunca açılan favori menüsü. */
    private fun showFavDialog(ch: Channel) {
        val isFav = Prefs.isFav(this, ch.streamId)
        val action = getString(if (isFav) R.string.fav_remove else R.string.fav_add)
        AlertDialog.Builder(this)
            .setTitle(ch.name)
            .setItems(arrayOf(action, getString(R.string.watch))) { _, which ->
                if (which == 0) {
                    val nowFav = Prefs.toggleFav(this, ch.streamId)
                    Toast.makeText(
                        this,
                        if (nowFav) R.string.fav_added else R.string.fav_removed,
                        Toast.LENGTH_SHORT
                    ).show()
                    if (currentCategoryId == FAV_ID) applyFilter() else channelAdapter.notifyDataSetChanged()
                } else {
                    openPlayer(ch)
                }
            }
            .show()
    }

    private fun openPlayer(ch: Channel) {
        val items = currentList.map { c ->
            PlayItem(
                name = c.name,
                url = client.liveUrl(c.streamId),
                logo = c.icon,
                info = channelInfoLines(c, catNameById[c.categoryId]),
                epgStreamId = c.streamId,
                number = c.number
            )
        }
        Playlist.items = items
        Playlist.index = currentList.indexOf(ch).coerceAtLeast(0)
        startActivity(Intent(this, PlayerActivity::class.java))
    }
}
