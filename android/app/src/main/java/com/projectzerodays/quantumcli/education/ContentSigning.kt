package com.projectzerodays.quantumcli.education

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * ECDSA P-256 / SHA256withECDSA verification for content bundles.
 *
 * The verifying public key is pinned in the APK (`content/content_pub.b64`).
 * Hot-fix packs are fail-closed: an unverifiable manifest is rejected, never
 * applied (see [ContentRegistry.checkHotFix]). Pure JVM — unit-testable.
 */
object ContentSigning {

    private var pinnedKey: PublicKey? = null

    fun init(pemBase64: String) {
        val der = Base64.getDecoder().decode(pemBase64.trim())
        pinnedKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(der))
    }

    fun isReady(): Boolean = pinnedKey != null

    /** True iff [signatureB64] (DER-encoded ECDSA sig) verifies over [payload]. */
    fun verify(payload: ByteArray, signatureB64: String): Boolean {
        val key = pinnedKey ?: return false
        return runCatching {
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(key)
            sig.update(payload)
            sig.verify(Base64.getDecoder().decode(signatureB64.trim()))
        }.getOrDefault(false)
    }

    fun sha256Hex(data: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it) }
}
