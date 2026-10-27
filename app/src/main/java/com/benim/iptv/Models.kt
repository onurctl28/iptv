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

/** Canlı yayın rehberi (EPG) program kaydı. */
data class EpgItem(
    val title: String,
    val start: String,
    val end: String
)
