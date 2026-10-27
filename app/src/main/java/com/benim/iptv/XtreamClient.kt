package com.benim.iptv

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AuthResult(val ok: Boolean, val message: String)

/**
 * Xtream Codes uyumlu IPTV sunucuları için istemci.
 */
class XtreamClient(host: String, private val user: String, private val pass: String) {

    companion object {
        // Birçok IPTV sunucusu tanımadığı uygulamayı engeller; yaygın kabul gören bir kimlik kullanıyoruz.
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    private val rawHost = host
    var base: String = candidates().first()
        private set

    private data class Resp(val code: Int, val body: String)

    /** Kullanıcının girdiği adresi temizler; şema yoksa http ve https adaylarını üretir. */
    private fun candidates(): List<String> {
        var s = rawHost.trim()
        while (s.endsWith("/")) s = s.dropLast(1)
        // Yanlışlıkla eklenen yolları ayıkla
        for (suffix in listOf("/player_api.php", "/panel_api.php", "/get.php", "/c")) {
            if (s.endsWith(suffix, true)) s = s.dropLast(suffix.length)
        }
        while (s.endsWith("/")) s = s.dropLast(1)
        val hasScheme = s.startsWith("http://", true) || s.startsWith("https://", true)
        return if (hasScheme) listOf(s) else listOf("http://$s", "https://$s")
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun apiUrl(b: String, action: String?): String {
        val u = "$b/player_api.php?username=${enc(user)}&password=${enc(pass)}"
        return if (action == null) u else "$u&action=$action"
    }

    /** Canlı yayın akış adresi. Çoğu sunucuda .ts en uyumlu seçenektir. */
    fun liveUrl(streamId: String, ext: String = "ts"): String =
        "$base/live/${enc(user)}/${enc(pass)}/$streamId.$ext"

    private fun fetch(urlStr: String): Resp {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "*/*")
        conn.instanceFollowRedirects = true
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return Resp(code, body)
        } finally {
            conn.disconnect()
        }
    }

    /** Giriş bilgilerini doğrular ve neyin yanlış gittiğini anlatan bir sonuç döner. */
    suspend fun login(): AuthResult = withContext(Dispatchers.IO) {
        var lastMsg = "Sunucuya ulaşılamadı."
        for (b in candidates()) {
            try {
                val resp = fetch(apiUrl(b, null))
                if (resp.body.isBlank()) {
                    lastMsg = "Sunucu boş yanıt verdi (HTTP ${resp.code}). Adres/port engellenmiş olabilir."
                    continue
                }
                val obj = try {
                    JSONObject(resp.body)
                } catch (e: Exception) {
                    lastMsg = "Sunucudan beklenmeyen yanıt geldi (HTTP ${resp.code}). Adres/port'u kontrol edin."
                    continue
                }
                val info = obj.optJSONObject("user_info")
                if (info == null) {
                    lastMsg = "Sunucu yanıtında hesap bilgisi yok. Adres/port'u kontrol edin."
                    continue
                }
                val auth = if (info.has("auth")) info.optString("auth") else ""
                val status = info.optString("status")
                if (auth == "1" || info.optInt("auth", 0) == 1) {
                    base = b
                    return@withContext AuthResult(true, "")
                }
                return@withContext AuthResult(
                    false,
                    "Sunucu girişi reddetti" + if (status.isNotEmpty()) " (durum: $status)." else "."
                )
            } catch (e: Exception) {
                lastMsg = "Sunucuya ulaşılamadı: ${e.message ?: "bağlantı hatası"}"
            }
        }
        AuthResult(false, lastMsg)
    }

    suspend fun categories(): List<Category> = withContext(Dispatchers.IO) {
        val out = ArrayList<Category>()
        try {
            val arr = JSONArray(fetch(apiUrl(base, "get_live_categories")).body)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("category_id")
                val name = o.optString("category_name")
                if (id.isNotEmpty()) out.add(Category(id, name))
            }
        } catch (e: Exception) {
        }
        out
    }

    suspend fun channels(): List<Channel> = withContext(Dispatchers.IO) {
        val out = ArrayList<Channel>()
        try {
            val arr = JSONArray(fetch(apiUrl(base, "get_live_streams")).body)
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
        }
        out
    }

    /** Kanalın "şimdi / sonra" program bilgisini (kısa EPG) getirir. */
    suspend fun shortEpg(streamId: String, limit: Int = 2): List<EpgItem> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<EpgItem>()
            try {
                val url = apiUrl(base, "get_short_epg") + "&stream_id=$streamId&limit=$limit"
                val obj = JSONObject(fetch(url).body)
                val arr = obj.optJSONArray("epg_listings") ?: return@withContext out
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val title = decodeMaybeB64(o.optString("title"))
                    if (title.isEmpty()) continue
                    out.add(EpgItem(title, hhmm(o.optString("start")), hhmm(o.optString("end"))))
                }
            } catch (e: Exception) {
            }
            out
        }

    private fun decodeMaybeB64(s: String): String {
        if (s.isEmpty()) return ""
        return try {
            val decoded = String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
            if (decoded.isNotBlank()) decoded else s
        } catch (e: Exception) {
            s
        }
    }

    private fun hhmm(s: String): String =
        if (s.length >= 16) s.substring(11, 16) else s
}
