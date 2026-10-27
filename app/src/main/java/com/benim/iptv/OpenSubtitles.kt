package com.benim.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * OpenSubtitles.com REST API istemcisi.
 * Arama yalnızca API anahtarı ister; indirme için ayrıca giriş (token) gerekir.
 * Anahtar ve hesap bilgileri kullanıcıya aittir (Ayarlar'dan girilir).
 */
object OpenSubtitles {

    private const val BASE = "https://api.opensubtitles.com/api/v1"
    private const val UA = "IPTVOynatici v1.7"

    data class Found(val fileId: String, val fileName: String)

    private fun open(urlStr: String, method: String, key: String, token: String?): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("Api-Key", key)
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "application/json")
        if (!token.isNullOrEmpty()) conn.setRequestProperty("Authorization", "Bearer $token")
        return conn
    }

    private fun postJson(urlStr: String, key: String, token: String?, body: JSONObject): String {
        val conn = open(urlStr, "POST", key, token)
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return stream?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            conn.disconnect()
        }
    }

    private fun get(urlStr: String, key: String): String {
        val conn = open(urlStr, "GET", key, null)
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return stream?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            conn.disconnect()
        }
    }

    /** Giriş yapıp indirme için gereken token'ı döner. */
    suspend fun login(key: String, user: String, pass: String): String? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("username", user).put("password", pass)
            val obj = JSONObject(postJson("$BASE/login", key, null, body))
            obj.optString("token").ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    /** Ada göre altyazı arar; ilk uygun dosyayı döner. */
    suspend fun search(key: String, query: String, lang: String): Found? = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE/subtitles?query=$q&languages=$lang"
            val obj = JSONObject(get(url, key))
            val data = obj.optJSONArray("data") ?: return@withContext null
            for (i in 0 until data.length()) {
                val attr = data.getJSONObject(i).optJSONObject("attributes") ?: continue
                val files = attr.optJSONArray("files") ?: continue
                if (files.length() == 0) continue
                val f = files.getJSONObject(0)
                val fileId = f.optString("file_id")
                if (fileId.isNotEmpty() && fileId != "null") {
                    return@withContext Found(fileId, f.optString("file_name"))
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** file_id için geçici indirme bağlantısını döner. */
    suspend fun downloadLink(key: String, token: String, fileId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().put("file_id", fileId.toIntOrNull() ?: fileId)
                val obj = JSONObject(postJson("$BASE/download", key, token, body))
                obj.optString("link").ifEmpty { null }
            } catch (e: Exception) {
                null
            }
        }

    /** Bağlantıdaki .srt dosyasını yerel dosyaya indirir. */
    suspend fun fetchToFile(link: String, dest: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = URL(link).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            try {
                if (conn.responseCode !in 200..299) return@withContext false
                conn.inputStream.use { input -> dest.outputStream.use { out -> input.copyTo(out) } }
            } finally {
                conn.disconnect()
            }
            dest.length() > 0
        } catch (e: Exception) {
            false
        }
    }
}
