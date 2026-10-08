package com.projectzerodays.quantumcli.education

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** Real ECDSA P-256 verification: valid sig passes, tamper fails. */
class ContentSigningTest {

    private fun freshKeys(): Pair<String, java.security.KeyPair> {
        val kp = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val pubB64 = Base64.getEncoder().encodeToString(kp.public.encoded)
        return pubB64 to kp
    }

    private fun sign(payload: ByteArray, kp: java.security.KeyPair): String {
        val s = Signature.getInstance("SHA256withECDSA")
        s.initSign(kp.private)
        s.update(payload)
        return Base64.getEncoder().encodeToString(s.sign())
    }

    private fun withPinned(pubB64: String, block: () -> Unit) {
        ContentSigning.init(pubB64)
        try {
            block()
        } finally {
            // Re-pin nothing; tests run in one JVM so the next init overwrites.
        }
    }

    @Test
    fun `valid signature verifies`() {
        val (pubB64, kp) = freshKeys()
        withPinned(pubB64) {
            val payload = """{"version":1,"files":{}}""".toByteArray()
            val sig = sign(payload, kp)
            assertTrue(ContentSigning.verify(payload, sig))
        }
    }

    @Test
    fun `tampered payload fails verification`() {
        val (pubB64, kp) = freshKeys()
        withPinned(pubB64) {
            val payload = """{"version":1,"files":{}}""".toByteArray()
            val sig = sign(payload, kp)
            val tampered = """{"version":2,"files":{}}""".toByteArray()
            assertFalse(ContentSigning.verify(tampered, sig))
        }
    }

    @Test
    fun `wrong key fails verification`() {
        val (pubB64, _) = freshKeys()
        val (_, otherKp) = freshKeys()
        withPinned(pubB64) {
            val payload = "manifest".toByteArray()
            assertFalse(ContentSigning.verify(payload, sign(payload, otherKp)))
        }
    }

    @Test
    fun `garbage signature fails closed`() {
        val (pubB64, _) = freshKeys()
        withPinned(pubB64) {
            assertFalse(ContentSigning.verify("payload".toByteArray(), "not-base64!!!"))
            assertFalse(ContentSigning.verify("payload".toByteArray(), ""))
        }
    }

    @Test
    fun `sha256 hex is stable and correct length`() {
        val hex = ContentSigning.sha256Hex("abc".toByteArray())
        assertTrue(hex.length == 64)
        assertTrue(hex.equals(hex.lowercase()))
    }
}
