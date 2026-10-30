package com.benim.iptv

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * TV'de, QR ekranı açıkken çalışan küçük yerel HTTP alıcısı.
 * Yalnızca QR'daki token ile gelen ve QR'daki anahtarla şifrelenmiş isteği kabul eder.
 */
class SyncServer(
    private val token: String,
    private val key: ByteArray,
    private val onPayload: (String) -> Boolean
) {
    private var server: ServerSocket? = null
    @Volatile private var running = false

    val port: Int get() = server?.localPort ?: -1

    fun start() {
        val s = ServerSocket(0)
        server = s
        running = true
        val t = Thread {
            while (running) {
                try {
                    handle(s.accept())
                } catch (e: Exception) {
                    if (!running) break
                }
            }
        }
        t.isDaemon = true
        t.start()
    }

    fun stop() {
        running = false
        try { server?.close() } catch (e: Exception) { }
    }

    private fun handle(sock: Socket) {
        sock.use { c ->
            c.soTimeout = 10000
            val input = BufferedInputStream(c.getInputStream())
            val requestLine = readLine(input) ?: return
            var contentLength = 0
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0 && line.substring(0, idx).trim().equals("Content-Length", true)) {
                    contentLength = line.substring(idx + 1).trim().toIntOrNull() ?: 0
                }
            }
            if (contentLength > 1_000_000) contentLength = 0
            val body = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = input.read(body, read, contentLength - read)
                if (n < 0) break
                read += n
            }
            val ok = requestLine.startsWith("POST ") &&
                requestLine.contains("t=$token") &&
                process(String(body, 0, read, Charsets.UTF_8))
            val resp = (if (ok) "{\"ok\":true}" else "{\"ok\":false}").toByteArray()
            val status = if (ok) "200 OK" else "403 Forbidden"
            val out = c.getOutputStream()
            out.write(
                ("HTTP/1.1 $status\r\nContent-Type: application/json\r\n" +
                    "Content-Length: ${resp.size}\r\nConnection: close\r\n\r\n").toByteArray()
            )
            out.write(resp)
            out.flush()
        }
    }

    private fun process(body: String): Boolean = try {
        val o = JSONObject(body)
        val json = SyncCrypto.decrypt(key, SyncCrypto.b64d(o.getString("iv")), SyncCrypto.b64d(o.getString("data")))
        onPayload(json)
    } catch (e: Exception) {
        false
    }

    private fun readLine(i: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = i.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
        }
    }
}
