package com.benim.iptv

import android.util.Base64
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Telefon -> TV aktarımı için tek kullanımlık AES-GCM şifreleme ve yardımcılar. */
object SyncCrypto {
    private val rnd = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rnd.nextBytes(it) }

    fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    fun unhex(s: String): ByteArray =
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    fun b64d(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    fun encrypt(key: ByteArray, plain: String): Pair<ByteArray, ByteArray> {
        val iv = randomBytes(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return iv to c.doFinal(plain.toByteArray(Charsets.UTF_8))
    }

    fun decrypt(key: ByteArray, iv: ByteArray, data: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return String(c.doFinal(data), Charsets.UTF_8)
    }

    /** Cihazın yerel ağdaki IPv4 adresi (Wi-Fi veya kablolu). */
    fun localIp(): String? = try {
        val addrs = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
        (addrs.firstOrNull { it.isSiteLocalAddress } ?: addrs.firstOrNull())?.hostAddress
    } catch (e: Exception) {
        null
    }
}
