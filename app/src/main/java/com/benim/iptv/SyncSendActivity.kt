package com.benim.iptv

import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Telefon tarafı: TV'deki QR kodu okutur ve tüm ayarları şifreli olarak TV'ye gönderir. */
class SyncSendActivity : AppCompatActivity() {

    private lateinit var status: TextView

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents
        if (text == null) status.text = getString(R.string.sync_scan_cancelled)
        else send(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync_send)
        status = findViewById(R.id.tv_status)
        findViewById<TextView>(R.id.btn_scan).setOnClickListener { startScan() }

        if (!Prefs.hasLogin(this)) {
            status.text = getString(R.string.sync_need_login)
            return
        }
        startScan()
    }

    private fun startScan() {
        if (!Prefs.hasLogin(this)) {
            status.text = getString(R.string.sync_need_login)
            return
        }
        val opts = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(getString(R.string.sync_scan_prompt))
            .setBeepEnabled(false)
        scanLauncher.launch(opts)
    }

    private fun send(qr: String) {
        val uri = Uri.parse(qr)
        val host = uri.host
        val port = uri.port
        val token = uri.getQueryParameter("t")
        val keyHex = uri.getQueryParameter("k")
        if (uri.scheme != "iptvsync" || host == null || port <= 0 || token == null || keyHex == null) {
            status.text = getString(R.string.sync_bad_qr)
            return
        }
        status.text = getString(R.string.sync_sending)
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val (iv, data) = SyncCrypto.encrypt(SyncCrypto.unhex(keyHex), Prefs.exportJson(this@SyncSendActivity))
                    val body = JSONObject()
                        .put("iv", SyncCrypto.b64(iv))
                        .put("data", SyncCrypto.b64(data))
                        .toString().toByteArray(Charsets.UTF_8)
                    val conn = URL("http://$host:$port/sync?t=$token").openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setFixedLengthStreamingMode(body.size)
                    try {
                        conn.outputStream.use { it.write(body) }
                        conn.responseCode == 200
                    } finally {
                        conn.disconnect()
                    }
                } catch (e: Exception) {
                    false
                }
            }
            status.text = getString(if (ok) R.string.sync_sent else R.string.sync_send_fail)
        }
    }
}
