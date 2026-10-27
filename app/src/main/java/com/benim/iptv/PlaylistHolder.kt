package com.benim.iptv

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Oynatıcıya geçerken kanal listesini taşımak için paylaşılan tutucu.
 * (Intent ile taşımak çok kanalda boyut sınırına takılabilir.)
 */
object Playlist {
    var items: List<Channel> = emptyList()
    var index: Int = 0
    var catNameById: Map<String, String> = emptyMap()
}

/** Bir kanalın sunucudan gelen bilgilerini "Etiket: değer" satırları olarak üretir. */
fun channelInfoLines(ch: Channel, catName: String?): ArrayList<String> {
    val info = ArrayList<String>()
    info.add("Kanal: ${ch.name}")
    if (ch.number.isNotEmpty()) info.add("Sıra No: ${ch.number}")
    if (!catName.isNullOrEmpty()) info.add("Kategori: $catName")
    info.add("Yayın No: ${ch.streamId}")
    if (ch.streamType.isNotEmpty()) info.add("Tür: ${ch.streamType}")
    info.add("Arşiv: " + if (ch.archive) "Var" else "Yok")
    if (ch.epgId.isNotEmpty()) info.add("EPG Kimliği: ${ch.epgId}")
    formatAdded(ch.added)?.let { info.add("Eklenme: $it") }
    return info
}

private fun formatAdded(added: String): String? {
    if (added.isEmpty()) return null
    return try {
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(added.toLong() * 1000))
    } catch (e: Exception) {
        null
    }
}
