package com.benim.iptv

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Oynatıcıya aktarılan oynatma listesi (canlı kanal, film veya dizi bölümleri). */
object Playlist {
    var items: List<PlayItem> = emptyList()
    var index: Int = 0
}

/**
 * Bir kez çekilen verileri bellekte tutar; aynı oturumda tekrar girişlerde
 * ağdan yeniden çekmeden anında açılması için.
 */
object DataCache {
    var liveCats: List<Category>? = null
    var liveChannels: List<Channel>? = null
    var vodCats: List<Category>? = null
    var vodItems: List<Vod>? = null
    var seriesCats: List<Category>? = null
    var seriesItems: List<Series>? = null

    fun clear() {
        liveCats = null; liveChannels = null
        vodCats = null; vodItems = null
        seriesCats = null; seriesItems = null
    }
}

fun channelInfoLines(ch: Channel, catName: String?): ArrayList<String> {
    val info = ArrayList<String>()
    info.add("Kanal: ${ch.name}")
    if (ch.number.isNotEmpty()) info.add("Sıra No: ${ch.number}")
    if (!catName.isNullOrEmpty()) info.add("Kategori: $catName")
    info.add("Yayın No: ${ch.streamId}")
    if (ch.streamType.isNotEmpty()) info.add("Tür: ${ch.streamType}")
    info.add("Arşiv: " + if (ch.archive) "Var" else "Yok")
    if (ch.epgId.isNotEmpty()) info.add("EPG Kimliği: ${ch.epgId}")
    formatDate(ch.added)?.let { info.add("Eklenme: $it") }
    return info
}

fun vodInfoLines(v: Vod, catName: String?): ArrayList<String> {
    val info = ArrayList<String>()
    info.add("Film: ${v.name}")
    if (!catName.isNullOrEmpty()) info.add("Kategori: $catName")
    if (v.rating.isNotEmpty() && v.rating != "0") info.add("Puan: ${v.rating}")
    if (v.ext.isNotEmpty()) info.add("Biçim: ${v.ext}")
    formatDate(v.added)?.let { info.add("Eklenme: $it") }
    return info
}

fun episodeInfoLines(ep: Episode, seriesName: String): ArrayList<String> {
    val info = ArrayList<String>()
    info.add("Dizi: $seriesName")
    if (ep.season.isNotEmpty()) info.add("Sezon: ${ep.season}")
    if (ep.epNum.isNotEmpty()) info.add("Bölüm: ${ep.epNum}")
    if (ep.title.isNotEmpty()) info.add("Başlık: ${ep.title}")
    if (ep.ext.isNotEmpty()) info.add("Biçim: ${ep.ext}")
    return info
}

private fun formatDate(unixSeconds: String): String? {
    if (unixSeconds.isEmpty()) return null
    return try {
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(unixSeconds.toLong() * 1000))
    } catch (e: Exception) {
        null
    }
}
