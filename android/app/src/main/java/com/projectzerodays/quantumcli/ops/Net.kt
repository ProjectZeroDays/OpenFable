package com.projectzerodays.quantumcli.ops

import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/** Low-level network primitives (tcp_probe / OPSEC.web / DoH parity). */
object Net {

    @Volatile
    private var sslFactory: SSLSocketFactory? = null

    fun localIp(): String {
        return try {
            val ifaces: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            for (ni in ifaces) {
                if (!ni.isUp || ni.isLoopback) continue
                val addrs: List<InetAddress> = Collections.list(ni.inetAddresses)
                for (a in addrs) {
                    if (a is java.net.Inet4Address && !a.isLoopbackAddress) {
                        return a.hostAddress ?: continue
                    }
                }
            }
            "127.0.0.1"
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }

    fun localScope(): String = localIp().substringBeforeLast(".") + ".0/24"

    fun tcpProbe(ip: String, port: Int, timeoutMs: Int = 500): Boolean {
        return try {
            Socket().use { s ->
                s.connect(java.net.InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Read a raw banner line from a TCP service (e.g. SSH, RTSP servers). */
    fun banner(ip: String, port: Int, timeoutMs: Int = 2000, send: ByteArray? = null): String? {
        return try {
            Socket().use { s ->
                s.connect(java.net.InetSocketAddress(ip, port), timeoutMs)
                s.soTimeout = timeoutMs
                send?.let { s.getOutputStream().apply { write(it); flush() } }
                val buf = ByteArray(512)
                val n = s.getInputStream().read(buf)
                if (n > 0) String(buf, 0, n, Charsets.ISO_8859_1) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun randHex(bytes: Int): String {
        val rnd = ByteArray(bytes)
        SecureRandom().nextBytes(rnd)
        return hex(rnd)
    }

    fun hex(data: ByteArray): String {
        val sb = StringBuilder(data.size * 2)
        for (b in data) sb.append("%02x".format(b))
        return sb.toString()
    }

    fun md5(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.ISO_8859_1))
        return hex(d)
    }

    fun validIp(ip: String): Boolean = try {
        val a = InetAddress.getByName(ip)
        a is java.net.Inet4Address
    } catch (e: Exception) {
        false
    }

    /** Parse CIDR (or bare IP) into host list; caps host count to protect the device. */
    fun cidrHosts(cidr: String, cap: Int = 1024): List<String> {
        val cleaned = cidr.trim()
        val parts = if (cleaned.contains("/")) cleaned.split("/") else listOf(cleaned, "32")
        if (parts.size != 2) return emptyList()
        val base = parts[0]
        val prefix = parts[1].toIntOrNull() ?: return emptyList()
        if (prefix !in 0..32) return emptyList()
        val octets = base.split(".")
        if (octets.size != 4) return emptyList()
        var addr = 0L
        for (o in octets) {
            val v = o.toIntOrNull() ?: return emptyList()
            if (v !in 0..255) return emptyList()
            addr = (addr shl 8) or v.toLong()
        }
        val size = 1L shl (32 - prefix)
        val first: Long
        val last: Long
        if (prefix >= 31) {
            first = addr
            last = addr + size - 1
        } else {
            first = addr + 1
            last = addr + size - 2
        }
        val out = ArrayList<String>()
        var cur = first
        while (cur <= last && out.size < cap) {
            out.add(
                "${(cur ushr 24) and 0xFF}.${(cur ushr 16) and 0xFF}." +
                    "${(cur ushr 8) and 0xFF}.${cur and 0xFF}"
            )
            cur++
        }
        return out
    }

    // ------------------------------------------------------------------ HTTP
    class HttpResult(
        val status: Int,
        val body: ByteArray,
        val headers: Map<String, String>,
        val error: String? = null,
    ) {
        val ok: Boolean get() = status in 200..399
        fun text(): String = String(body, Charsets.UTF_8)
        fun text(limit: Int): String = text().take(limit)
    }

    private fun trustAllFactory(): SSLSocketFactory {
        sslFactory?.let { return it }
        synchronized(this) {
            sslFactory?.let { return it }
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(tm), SecureRandom())
            val f = ctx.socketFactory
            sslFactory = f
            return f
        }
    }

    fun httpGet(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 3000,
    ): HttpResult = httpCall("GET", url, null, headers, timeoutMs)

    fun httpPost(
        url: String,
        body: ByteArray,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 3000,
    ): HttpResult = httpCall("POST", url, body, headers, timeoutMs)

    /** GET with Basic, upgrading to Digest when the server challenges. */
    fun httpGetAuth(url: String, user: String, pass: String, timeoutMs: Int = 3000): HttpResult {
        val basic = "Basic " + java.util.Base64.getEncoder()
            .encodeToString("$user:$pass".toByteArray(Charsets.ISO_8859_1))
        val first = httpGet(url, mapOf("Authorization" to basic), timeoutMs)
        if (first.status != 401) return first
        val challenge = first.headers["www-authenticate"] ?: return first
        if (!challenge.trim().lowercase().startsWith("digest")) return first
        val digest = digestAuthHeader(challenge, "GET", url, user, pass) ?: return first
        return httpGet(url, mapOf("Authorization" to digest), timeoutMs)
    }

    internal fun digestAuthHeader(
        challenge: String,
        method: String,
        url: String,
        user: String,
        pass: String,
    ): String? {
        val realm = regexGroup(challenge, "realm=\"?([^\"]*)\"?") ?: return null
        val nonce = regexGroup(challenge, "nonce=\"?([^\"]*)\"?") ?: return null
        val qop = regexGroup(challenge, "qop=\"?([^\"]*)\"?")
        val opaque = regexGroup(challenge, "opaque=\"?([^\"]*)\"?")
        val uri = runCatching { URL(url).let { it.path.ifEmpty { "/" } + it.query?.let { q -> "?$q" } ?: "" } }
            .getOrDefault("/")
        val ha1 = md5("$user:$realm:$pass")
        val ha2 = md5("$method:$uri")
        val cnonce = randHex(8)
        val nc = "00000001"
        val response = if (qop != null && qop.contains("auth")) {
            md5("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
        } else {
            md5("$ha1:$nonce:$ha2")
        }
        val sb = StringBuilder("Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\"")
        if (qop != null && qop.contains("auth")) {
            sb.append(", qop=auth, nc=$nc, cnonce=\"$cnonce\"")
        }
        sb.append(", response=\"$response\", algorithm=MD5")
        if (opaque != null) sb.append(", opaque=\"$opaque\"")
        return sb.toString()
    }

    private fun regexGroup(s: String, pattern: String): String? {
        val m = Regex(pattern).find(s) ?: return null
        return m.groupValues.getOrNull(1)
    }

    private fun httpCall(
        method: String,
        url: String,
        body: ByteArray?,
        headers: Map<String, String>,
        timeoutMs: Int,
    ): HttpResult {
        var conn: HttpURLConnection? = null
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            conn = c
            c.requestMethod = method
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = true
            if (c is HttpsURLConnection) {
                c.sslSocketFactory = trustAllFactory()
                c.setHostnameVerifier { _, _ -> true }
            }
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body != null) {
                c.doOutput = true
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val status = c.responseCode
            val stream = if (status in 200..399) c.inputStream else c.errorStream
            val respBody = stream?.use { it.readBytes() } ?: ByteArray(0)
            val respHeaders = HashMap<String, String>()
            for ((k, v) in c.headerFields) {
                if (k != null && v.isNotEmpty()) respHeaders[k.lowercase()] = v.joinToString(", ")
            }
            HttpResult(status, respBody, respHeaders)
        } catch (e: Exception) {
            HttpResult(0, ByteArray(0), emptyMap(), e.message ?: e.javaClass.simpleName)
        } finally {
            conn?.disconnect()
        }
    }

    /** Parallel map over hosts with a bounded worker pool (ThreadPoolExecutor parity). */
    fun <T> parMap(items: List<String>, workers: Int = 32, work: (String) -> T?): List<T> {
        if (items.isEmpty()) return emptyList()
        val pool = Executors.newFixedThreadPool(minOf(workers, items.size))
        try {
            val futures = items.map { pool.submit<T?> { work(it) } }
            return futures.mapNotNull { runCatching { it.get(60, TimeUnit.SECONDS) }.getOrNull() }
        } finally {
            pool.shutdownNow()
        }
    }
}
