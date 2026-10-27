package com.benim.iptv

data class Category(
    val id: String,
    val name: String
)

data class Channel(
    val streamId: String,
    val name: String,
    val icon: String,
    val categoryId: String
)
