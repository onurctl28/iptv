package com.benim.iptv

import android.content.Context

/** Giriş bilgilerini ve favori kanalları cihazda saklar. */
object Prefs {
    private const val FILE = "iptv_prefs"
    private const val K_HOST = "host"
    private const val K_USER = "user"
    private const val K_PASS = "pass"
    private const val K_FAV = "favorites"
    private const val K_LAST = "last_stream_id"
    private const val K_SUB_SIZE = "sub_size"
    private const val K_SUB_COLOR = "sub_color"
    private const val K_SUB_BG = "sub_bg"
    private const val K_SUB_EDGE = "sub_edge"
    private const val K_OS_KEY = "os_key"
    private const val K_OS_USER = "os_user"
    private const val K_OS_PASS = "os_pass"
    private const val K_OS_LANG = "os_lang"

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

    fun saveLastChannel(ctx: Context, streamId: String) {
        sp(ctx).edit().putString(K_LAST, streamId).apply()
    }

    fun lastChannel(ctx: Context): String = sp(ctx).getString(K_LAST, "") ?: ""

    // Altyazı görünümü (0..3 boyut, 0=beyaz/1=sarı renk, arka plan kutusu, dış çizgi)
    fun subSize(ctx: Context): Int = sp(ctx).getInt(K_SUB_SIZE, 1)
    fun subColor(ctx: Context): Int = sp(ctx).getInt(K_SUB_COLOR, 0)
    fun subBg(ctx: Context): Boolean = sp(ctx).getBoolean(K_SUB_BG, true)
    fun subEdge(ctx: Context): Boolean = sp(ctx).getBoolean(K_SUB_EDGE, true)
    fun setSubSize(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_SUB_SIZE, v).apply()
    fun setSubColor(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_SUB_COLOR, v).apply()
    fun setSubBg(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_SUB_BG, v).apply()
    fun setSubEdge(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_SUB_EDGE, v).apply()

    // OpenSubtitles (altyazı indirme) ayarları
    fun osKey(ctx: Context): String = sp(ctx).getString(K_OS_KEY, "") ?: ""
    fun osUser(ctx: Context): String = sp(ctx).getString(K_OS_USER, "") ?: ""
    fun osPass(ctx: Context): String = sp(ctx).getString(K_OS_PASS, "") ?: ""
    fun osLang(ctx: Context): String = sp(ctx).getString(K_OS_LANG, "tr") ?: "tr"
    fun saveOpenSubtitles(ctx: Context, key: String, user: String, pass: String, lang: String) {
        sp(ctx).edit()
            .putString(K_OS_KEY, key)
            .putString(K_OS_USER, user)
            .putString(K_OS_PASS, pass)
            .putString(K_OS_LANG, lang.ifEmpty { "tr" })
            .apply()
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
