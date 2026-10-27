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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var client: XtreamClient
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var emptyView: TextView

    private var allChannels: List<Channel> = emptyList()
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
            isFav = { id -> Prefs.isFav(this, id) }
        )
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
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val cats = client.categories()
            allChannels = client.channels()

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

    override fun onResume() {
        super.onResume()
        if (allChannels.isNotEmpty()) channelAdapter.notifyDataSetChanged()
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
        channelAdapter.submit(list)
        categoryAdapter.setSelected(currentCategoryId)
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openPlayer(ch: Channel) {
        val info = ArrayList<String>()
        info.add("Kanal: ${ch.name}")
        if (ch.number.isNotEmpty()) info.add("Sıra No: ${ch.number}")
        val catName = catNameById[ch.categoryId]
        if (!catName.isNullOrEmpty()) info.add("Kategori: $catName")
        info.add("Yayın No: ${ch.streamId}")
        if (ch.streamType.isNotEmpty()) info.add("Tür: ${ch.streamType}")
        info.add("Arşiv: " + if (ch.archive) "Var" else "Yok")
        if (ch.epgId.isNotEmpty()) info.add("EPG Kimliği: ${ch.epgId}")
        formatAdded(ch.added)?.let { info.add("Eklenme: $it") }

        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra(PlayerActivity.EXTRA_URL, client.liveUrl(ch.streamId))
        intent.putExtra(PlayerActivity.EXTRA_NAME, ch.name)
        intent.putExtra(PlayerActivity.EXTRA_LOGO, ch.icon)
        intent.putExtra(PlayerActivity.EXTRA_STREAM_ID, ch.streamId)
        intent.putStringArrayListExtra(PlayerActivity.EXTRA_INFO, info)
        startActivity(intent)
    }

    private fun formatAdded(added: String): String? {
        if (added.isEmpty()) return null
        return try {
            val millis = added.toLong() * 1000
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(millis))
        } catch (e: Exception) {
            null
        }
    }
}
