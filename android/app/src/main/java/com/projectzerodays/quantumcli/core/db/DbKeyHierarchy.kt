package com.projectzerodays.quantumcli.core.db

import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Key hierarchy for the three-database encryption core (Sprint 0.5 B1) —
 * the dependency-free part that is fully implemented and tested now:
 *
 *  - RFC 5869 HKDF-SHA256 (known-answer tested against the RFC vectors),
 *  - per-database key derivation from one master secret
 *    (`deriveDbKey(master, "quantum_intel")` etc. — different info labels
 *    mean the three DB keys are cryptographically independent),
 *  - AES-256-GCM BLOB envelope with optional AAD (tamper → open returns null),
 *  - per-row HMAC-SHA256 tags with constant-time verification.
 *
 * [MasterKeyProvider] supplies the 32-byte master secret;
 * [AndroidKeyStoreMasterKeyProvider] backs it with an AndroidKeyStore
 * HMAC-SHA256 key (StrongBox where available) whose fixed-input MAC output
 * is the exportable derivation root SQLCipher needs as a passphrase.
 *
 * Scope note (honest): wiring these keys into actual Room+SQLCipher
 * databases additionally needs the Room/KSP/SQLCipher Gradle deps — that
 * dependency-heavy PR is deliberately left as the next step rather than
 * half-shipped (see docs/TODO.md).
 */
object DbKeyHierarchy {

    private val HKDF_SALT = "quantum-cli-db-v1".toByteArray(Charsets.UTF_8)
    private const val DB_INFO_PREFIX = "quantum-db-"
    private const val DB_INFO_SUFFIX = "-v1"
    private const val KEY_LEN = 32

    // ------------------------------------------------------------ HKDF

    /** RFC 5869 HKDF-Extract(salt, ikm) → PRK (SHA-256). Empty salt = 32 zero bytes (§2.2). */
    fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val effective = if (salt.isEmpty()) ByteArray(32) else salt
        return Mac.getInstance("HmacSHA256")
            .run { init(SecretKeySpec(effective, "HmacSHA256")); doFinal(ikm) }
    }

    /** RFC 5869 HKDF-Expand(PRK, info, length). */
    fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length > 0) { "length must be positive" }
        val out = ByteArrayOutputStream()
        var t = ByteArray(0)
        var i = 1
        while (out.size() < length) {
            t = Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(prk, "HmacSHA256"))
                doFinal(t + info + byteArrayOf(i.toByte()))
            }
            out.write(t)
            i++
        }
        return out.toByteArray().copyOf(length)
    }

    /** RFC 5869 HKDF (extract + expand) with SHA-256. */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = KEY_LEN): ByteArray =
        hkdfExpand(hkdfExtract(salt, ikm), info, length)

    // ------------------------------------------------------ key hierarchy

    interface MasterKeyProvider {
        /** ≥32-byte master secret; must be deterministic per install. */
        fun masterKey(): ByteArray
    }

    /** In-memory provider for tests / unencrypted dev builds. */
    class StaticMasterKeyProvider(private val key: ByteArray) : MasterKeyProvider {
        override fun masterKey(): ByteArray = key.copyOf()
    }

    /** Derive the per-database key (HKDF with a per-DB info label). */
    fun deriveDbKey(master: ByteArray, dbName: String): ByteArray =
        hkdfSha256(
            ikm = master,
            salt = HKDF_SALT,
            info = (DB_INFO_PREFIX + dbName + DB_INFO_SUFFIX).toByteArray(Charsets.UTF_8),
            length = KEY_LEN,
        )

    // ------------------------------------------------------- BLOB envelope

    private const val GCM_TAG_BITS = 128
    private const val NONCE_LEN = 12

    /** Seal with AES-256-GCM; output = 12-byte nonce || ciphertext+tag. */
    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val nonce = ByteArray(NONCE_LEN).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        aad?.let { cipher.updateAAD(it) }
        return nonce + cipher.doFinal(plaintext)
    }

    /** Open a sealed blob; null on any failure (tamper, wrong key, bad AAD). */
    fun openSealed(key: ByteArray, sealed: ByteArray, aad: ByteArray? = null): ByteArray? {
        return try {
            if (sealed.size <= NONCE_LEN) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, sealed, 0, NONCE_LEN),
            )
            aad?.let { cipher.updateAAD(it) }
            cipher.doFinal(sealed, NONCE_LEN, sealed.size - NONCE_LEN)
        } catch (_: Exception) {
            null
        }
    }

    // ----------------------------------------------------------- per-row HMAC

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256")); doFinal(data)
        }

    fun hmacVerify(key: ByteArray, data: ByteArray, tag: ByteArray): Boolean =
        constantTimeEquals(hmacSha256(key, data), tag)

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}

/**
 * Master-key provider backed by an AndroidKeyStore HMAC-SHA256 key: the
 * MAC over a fixed domain string is deterministic per install and exportable
 * (SQLCipher/Passphrase need raw bytes — Keystore AES keys can't be
 * exported). StrongBox is attempted first and falls back to TEE/software
 * Keystore on devices without it.
 */
class AndroidKeyStoreMasterKeyProvider(
    private val alias: String = "qc_master_key",
    private val domain: String = "sqlcipher-db-key",
) : DbKeyHierarchy.MasterKeyProvider {

    override fun masterKey(): ByteArray {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(alias)) generate(ks)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(ks.getKey(alias, null) as SecretKey)
        return mac.doFinal(domain.toByteArray(Charsets.UTF_8))
    }

    private fun generate(ks: KeyStore) {
        val spec = android.security.keystore.KeyGenParameterSpec
            .Builder(alias, android.security.keystore.KeyProperties.PURPOSE_SIGN)
            .setKeySize(256)
            .build()
        val generator = KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            "AndroidKeyStore",
        )
        // StrongBox first (Android 9+ devices that ship it), plain Keystore otherwise.
        try {
            generator.init(
                android.security.keystore.KeyGenParameterSpec
                    .Builder(alias, android.security.keystore.KeyProperties.PURPOSE_SIGN)
                    .setKeySize(256)
                    .setIsStrongBoxBacked(true)
                    .build(),
            )
            generator.generateKey()
        } catch (_: Exception) {
            try {
                ks.deleteEntry(alias)
            } catch (_: Exception) {
            }
            generator.init(spec)
            generator.generateKey()
        }
    }
}
