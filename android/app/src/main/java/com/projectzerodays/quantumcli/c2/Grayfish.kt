package com.projectzerodays.quantumcli.c2

import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * GRAYFISH parity — sealed loot at rest (AES-256-GCM).
 * The key file at logs/.lootkey is created once; deleting it breaks every
 * sealed file. Treat as immutable (same contract as the Python GrayFish).
 */
object Grayfish {

    private const val AAD = "grayfish"

    fun key(logDir: File): ByteArray {
        val keyFile = File(logDir, ".lootkey")
        if (!keyFile.isFile) {
            val rnd = ByteArray(32)
            SecureRandom().nextBytes(rnd)
            val hex = buildString { rnd.forEach { append("%02x".format(it)) } }
            keyFile.writeText(hex)
        }
        val hex = keyFile.readText().trim()
        return MessageDigest.getInstance("SHA-256")
            .digest(hex.toByteArray(Charsets.UTF_8))
    }

    fun sealBytes(logDir: File, data: ByteArray): ByteArray {
        val nonce = ByteArray(12)
        SecureRandom().nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key(logDir), "AES"),
            GCMParameterSpec(128, nonce)
        )
        cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
        val ct = cipher.doFinal(data)
        return nonce + ct
    }

    fun unsealBytes(logDir: File, blob: ByteArray): ByteArray? {
        if (blob.size < 12 + 16) return null
        val nonce = blob.copyOfRange(0, 12)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key(logDir), "AES"),
                GCMParameterSpec(128, nonce)
            )
            cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
            cipher.doFinal(blob.copyOfRange(12, blob.size))
        } catch (e: Exception) {
            null
        }
    }

    /** Encrypt file -> <path>.gfy (plaintext removed); returns the sealed path. */
    fun seal(file: File): File {
        if (file.name.endsWith(".gfy")) return file
        val logDir = file.parentFile?.parentFile ?: C2State.logDir
        val sealed = File(file.parentFile, file.name + ".gfy")
        sealed.writeBytes(sealBytes(logDir, file.readBytes()))
        file.delete()
        return sealed
    }

    /** Decrypt a .gfy file in place; returns the plaintext path. */
    fun unseal(sealed: File): File? {
        if (!sealed.name.endsWith(".gfy")) return sealed
        val logDir = sealed.parentFile?.parentFile ?: C2State.logDir
        val plain = unsealBytes(logDir, sealed.readBytes()) ?: return null
        val out = File(sealed.parentFile, sealed.name.removeSuffix(".gfy"))
        out.writeBytes(plain)
        sealed.delete()
        return out
    }

    /** Decrypt a .gfy file to memory (no plaintext written to disk). */
    fun unsealToBytes(sealed: File): ByteArray? {
        val logDir = sealed.parentFile?.parentFile ?: C2State.logDir
        return unsealBytes(logDir, sealed.readBytes())
    }
}
