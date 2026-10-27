package com.benim.iptv

import android.content.Context

/** Giriş bilgilerini ve favori kanalları cihazda saklar. */
object Prefs {
    private const val FILE = "iptv_prefs"
    private const val K_HOST = "host"
    private const val K_USER = "user"
    private const val K_PASS = "pass"
    private const val K_FAV = "favorites"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun saveLogin(ctx: Context, host: String, user: String, pass: String) {
        sp(ctx).edit()
            .putString(K_HOST, host)
            .putString(K_USER, user)
            .putString(K_PASS, pass)
            .apply()
    }

    fun host(ctx: Context): String = sp(ctx).getString(K_HOST, "") ?: ""
    fun user(ctx: Context): String = sp(ctx).getString(K_USER, "") ?: ""
    fun pass(ctx: Context): String = sp(ctx).getString(K_PASS, "") ?: ""

    fun hasLogin(ctx: Context): Boolean = host(ctx).isNotEmpty() && user(ctx).isNotEmpty()

    fun clear(ctx: Context) {
        // Favorileri koruyarak sadece oturumu kapat
        val favs = favorites(ctx)
        sp(ctx).edit().clear().putStringSet(K_FAV, favs).apply()
    }

    fun favorites(ctx: Context): MutableSet<String> =
        HashSet(sp(ctx).getStringSet(K_FAV, emptySet()) ?: emptySet())

    fun isFav(ctx: Context, id: String): Boolean = favorites(ctx).contains(id)

    fun toggleFav(ctx: Context, id: String): Boolean {
        val set = favorites(ctx)
        val now: Boolean
        if (set.contains(id)) {
            set.remove(id); now = false
        } else {
            set.add(id); now = true
        }
        sp(ctx).edit().putStringSet(K_FAV, set).apply()
        return now
    }
}
