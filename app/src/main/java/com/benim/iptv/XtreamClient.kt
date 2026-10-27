package com.benim.iptv

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Xtream Codes uyumlu IPTV sunucuları için istemci.
 * player_api.php üzerinden kategori, kanal ve yayın rehberi (EPG) bilgisini çeker,
 * ve canlı yayın (live) akış adreslerini üretir.
 */
class XtreamClient(host: String, private val user: String, private val pass: String) {

    val base: String = normalize(host)

    private fun normalize(h: String): String {
        var s = h.trim()
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) {
            s = "http://$s"
        }
        while (s.endsWith("/")) s = s.dropLast(1)
        return s
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun apiUrl(action: String?): String {
        val b = "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}"
        return if (action == null) b else "$b&action=$action"
    }

    /** Canlı yayın akış adresi. Çoğu sunucuda .ts en uyumlu seçenektir. */
    fun liveUrl(streamId: String, ext: String = "ts"): String =
        "$base/live/${enc(user)}/${enc(pass)}/$streamId.$ext"

    private fun fetch(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "IPTVOynatici/1.0")
        conn.instanceFollowRedirects = true
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return stream?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            conn.disconnect()
        }
    }

    /** Giriş bilgilerini doğrular. */
    suspend fun authenticate(): Boolean = withContext(Dispatchers.IO) {
        try {
            val obj = JSONObject(fetch(apiUrl(null)))
            val info = obj.optJSONObject("user_info")
            info != null && (info.optString("auth") == "1" || info.optInt("auth") == 1)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun categories(): List<Category> = withContext(Dispatchers.IO) {
        val out = ArrayList<Category>()
        try {
            val arr = JSONArray(fetch(apiUrl("get_live_categories")))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("category_id")
                val name = o.optString("category_name")
                if (id.isNotEmpty()) out.add(Category(id, name))
            }
        } catch (e: Exception) {
            // boş liste döner
        }
        out
    }

    suspend fun channels(): List<Channel> = withContext(Dispatchers.IO) {
        val out = ArrayList<Channel>()
        try {
            val arr = JSONArray(fetch(apiUrl("get_live_streams")))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("stream_id")
                if (id.isEmpty() || id == "null") continue
                out.add(
                    Channel(
                        streamId = id,
                        name = o.optString("name"),
                        icon = o.optString("stream_icon"),
                        categoryId = o.optString("category_id"),
                        number = o.optString("num"),
                        epgId = o.optString("epg_channel_id"),
                        added = o.optString("added"),
                        archive = (o.optInt("tv_archive", 0) == 1),
                        streamType = o.optString("stream_type")
                    )
                )
            }
        } catch (e: Exception) {
            // boş liste döner
        }
        out
    }

    /** Kanalın "şimdi / sonra" program bilgisini (kısa EPG) getirir. */
    suspend fun shortEpg(streamId: String, limit: Int = 2): List<EpgItem> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<EpgItem>()
            try {
                val txt = fetch(apiUrl("get_short_epg") + "&stream_id=$streamId&limit=$limit")
                val obj = JSONObject(txt)
                val arr = obj.optJSONArray("epg_listings") ?: return@withContext out
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val title = decodeMaybeB64(o.optString("title"))
                    if (title.isEmpty()) continue
                    out.add(EpgItem(title, hhmm(o.optString("start")), hhmm(o.optString("end"))))
                }
            } catch (e: Exception) {
                // boş liste döner
            }
            out
        }

    /** Çoğu sunucu başlıkları base64 ile gönderir; değilse ham metni kullanır. */
    private fun decodeMaybeB64(s: String): String {
        if (s.isEmpty()) return ""
        return try {
            val bytes = Base64.decode(s, Base64.DEFAULT)
            val decoded = String(bytes, Charsets.UTF_8)
            if (decoded.isNotBlank()) decoded else s
        } catch (e: Exception) {
            s
        }
    }

    /** "2024-01-02 20:30:00" -> "20:30" */
    private fun hhmm(s: String): String =
        if (s.length >= 16) s.substring(11, 16) else s
}
