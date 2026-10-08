package com.projectzerodays.quantumcli.core.db

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DbKeyHierarchyTest {

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private fun fromHex(s: String) = ByteArray(s.length / 2) {
        s.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }

    // ------------------------------------------------------- HKDF RFC 5869

    @Test
    fun hkdfMatchesRfc5869TestCase1() {
        val ikm = fromHex("0b".repeat(22))
        val salt = fromHex("000102030405060708090a0b0c")
        val info = fromHex("f0f1f2f3f4f5f6f7f8f9")
        val prk = DbKeyHierarchy.hkdfExtract(salt, ikm)
        assertEquals(
            "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5",
            hex(prk),
        )
        val okm = DbKeyHierarchy.hkdfExpand(prk, info, 42)
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                "34007208d5b887185865",
            hex(okm),
        )
        // full HKDF agrees with extract+expand
        assertArrayEquals(okm, DbKeyHierarchy.hkdfSha256(ikm, salt, info, 42))
    }

    @Test
    fun hkdfMatchesRfc5869TestCase3ZeroSaltInfo() {
        val ikm = fromHex("0b".repeat(22))
        val okm = DbKeyHierarchy.hkdfSha256(ikm, ByteArray(0), ByteArray(0), 42)
        assertEquals(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d" +
                "201395faa4b61a96c8",
            hex(okm),
        )
    }

    // ----------------------------------------------------- key hierarchy

    @Test
    fun dbKeysAreDeterministicAndIndependent() {
        val master = ByteArray(32) { it.toByte() }
        val intel1 = DbKeyHierarchy.deriveDbKey(master, "quantum_intel")
        val intel2 = DbKeyHierarchy.deriveDbKey(master, "quantum_intel")
        val secrets = DbKeyHierarchy.deriveDbKey(master, "quantum_secrets")
        val app = DbKeyHierarchy.deriveDbKey(master, "quantum_app")
        assertEquals(32, intel1.size)
        assertArrayEquals(intel1, intel2)
        assertNotEquals(hex(intel1), hex(secrets))
        assertNotEquals(hex(intel1), hex(app))
        assertNotEquals(hex(secrets), hex(app))
    }

    @Test
    fun differentMastersGiveDifferentDbKeys() {
        val a = DbKeyHierarchy.deriveDbKey(ByteArray(32) { 1 }, "quantum_intel")
        val b = DbKeyHierarchy.deriveDbKey(ByteArray(32) { 2 }, "quantum_intel")
        assertNotEquals(hex(a), hex(b))
    }

    // ------------------------------------------------------- BLOB envelope

    @Test
    fun sealedBlobRoundTrips() {
        val key = DbKeyHierarchy.deriveDbKey(ByteArray(32) { 7 }, "quantum_secrets")
        val plaintext = "wifi password: hunter2 ✓".toByteArray()
        val sealed = DbKeyHierarchy.seal(key, plaintext)
        assertTrue(sealed.size > 12)
        assertArrayEquals(plaintext, DbKeyHierarchy.openSealed(key, sealed))
    }

    @Test
    fun tamperedBlobOrWrongKeyOrAadFailsClosed() {
        val key = DbKeyHierarchy.deriveDbKey(ByteArray(32) { 7 }, "quantum_secrets")
        val sealed = DbKeyHierarchy.seal(key, "secret".toByteArray(), aad = "row:42".toByteArray())

        // flip one ciphertext byte → GCM tag mismatch
        val tampered = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x01).toByte() }
        assertNull(DbKeyHierarchy.openSealed(key, tampered, aad = "row:42".toByteArray()))

        // wrong key
        val wrongKey = DbKeyHierarchy.deriveDbKey(ByteArray(32) { 8 }, "quantum_secrets")
        assertNull(DbKeyHierarchy.openSealed(wrongKey, sealed, aad = "row:42".toByteArray()))

        // wrong AAD
        assertNull(DbKeyHierarchy.openSealed(key, sealed, aad = "row:43".toByteArray()))

        // truncated blob
        assertNull(DbKeyHierarchy.openSealed(key, sealed.copyOf(10), aad = "row:42".toByteArray()))
    }

    // ----------------------------------------------------------- per-row HMAC

    @Test
    fun hmacVerifiesAndDetectsTamper() {
        val macKey = DbKeyHierarchy.deriveDbKey(ByteArray(32) { 9 }, "quantum_intel")
        val row = """{"imei":"356938035643809","ip":"10.0.0.5"}""".toByteArray()
        val tag = DbKeyHierarchy.hmacSha256(macKey, row)
        assertEquals(32, tag.size)
        assertTrue(DbKeyHierarchy.hmacVerify(macKey, row, tag))

        val tamperedRow = """{"imei":"356938035643809","ip":"10.0.0.6"}""".toByteArray()
        assertFalse(DbKeyHierarchy.hmacVerify(macKey, tamperedRow, tag))
        assertFalse(DbKeyHierarchy.hmacVerify(macKey, row, tag.copyOf().also { it[0] = 0 }))
    }

    @Test
    fun constantTimeEqualsHandlesSizeMismatch() {
        assertTrue(DbKeyHierarchy.constantTimeEquals(ByteArray(3), ByteArray(3)))
        assertFalse(DbKeyHierarchy.constantTimeEquals(ByteArray(3), ByteArray(4)))
        assertFalse(
            DbKeyHierarchy.constantTimeEquals(
                byteArrayOf(1, 2, 3),
                byteArrayOf(1, 2, 4),
            ),
        )
    }
}
