package com.benim.iptv

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** TV tarafı: QR kod gösterir, telefondan gelen ayarları alır ve uygular. */
class SyncReceiveActivity : AppCompatActivity() {

    private var server: SyncServer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync_receive)

        val status = findViewById<TextView>(R.id.tv_status)
        val qrView = findViewById<ImageView>(R.id.iv_qr)

        val ip = SyncCrypto.localIp()
        if (ip == null) {
            status.text = getString(R.string.sync_no_network)
            return
        }

        val token = SyncCrypto.hex(SyncCrypto.randomBytes(8))
        val key = SyncCrypto.randomBytes(16)
        val srv = SyncServer(token, key) { json ->
            val ok = try { Prefs.importJson(this, json) } catch (e: Exception) { false }
            if (ok) runOnUiThread { onSynced() }
            ok
        }
        try {
            srv.start()
        } catch (e: Exception) {
            status.text = getString(R.string.sync_no_network)
            return
        }
        server = srv

        val payload = "iptvsync://$ip:${srv.port}?t=$token&k=${SyncCrypto.hex(key)}"
        qrView.setImageBitmap(makeQr(payload, 640))
        status.text = getString(R.string.sync_waiting)
    }

    private fun onSynced() {
        findViewById<TextView>(R.id.tv_status).text = getString(R.string.sync_done)
        DataCache.clear()
        server?.stop()
        handler.postDelayed({
            startActivity(
                Intent(this, HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }, 1500)
    }

    private fun makeQr(text: String, size: Int): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val px = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            px[y * size + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        server?.stop()
    }
}
