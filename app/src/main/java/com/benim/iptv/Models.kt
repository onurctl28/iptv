package com.benim.iptv

data class Category(
    val id: String,
    val name: String
)

data class Channel(
    val streamId: String,
    val name: String,
    val icon: String,
    val categoryId: String,
    val number: String = "",
    val epgId: String = "",
    val added: String = "",
    val archive: Boolean = false,
    val streamType: String = ""
)

/** Film (VOD) kaydı. */
data class Vod(
    val streamId: String,
    val name: String,
    val icon: String,
    val categoryId: String,
    val ext: String,
    val rating: String = "",
    val added: String = ""
)

/** Dizi kaydı. */
data class Series(
    val seriesId: String,
    val name: String,
    val cover: String,
    val categoryId: String,
    val plot: String = "",
    val genre: String = "",
    val rating: String = ""
)

/** Dizi bölümü. */
data class Episode(
    val id: String,
    val title: String,
    val season: String,
    val epNum: String,
    val ext: String
)

/** Canlı yayın rehberi (EPG) program kaydı. */
data class EpgItem(
    val title: String,
    val start: String,
    val end: String
)

/**
 * Oynatıcıya aktarılan, türden bağımsız oynatılabilir öğe.
 * url: hazır akış adresi. epgStreamId: yalnızca canlı yayında doludur.
 */
data class PlayItem(
    val name: String,
    val url: String,
    val logo: String,
    val info: ArrayList<String>,
    val epgStreamId: String? = null,
    val number: String = ""
)
