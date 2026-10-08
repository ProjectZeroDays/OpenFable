package com.projectzerodays.quantumcli.ops

import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

/**
 * NSE-style script profiles for the zenmap controls — banner-grab and
 * pattern-match probes over TCP. Every finding is VERIFIED BY OBSERVATION
 * (what the probe saw), never a CVE claim or a database lookup:
 * "anonymous FTP allowed" is reported only when the server returns 230,
 * "cleartext auth" only when a login prompt is actually captured, and so on.
 * No finding means no evidence — never "secure".
 *
 * Blocking socket IO with per-probe timeouts; callers run [runScripts] on
 * Dispatchers.IO. Nothing throws — failures yield no findings.
 */
object NseScripts {

    enum class Level { INFO, NOTE, WARN }

    data class Script(
        val id: String,
        val title: String,
        val desc: String,
        val ports: List<Int>,
    )

    data class Finding(
        val scriptId: String,
        val title: String,
        val port: Int,
        val level: Level,
        val detail: String,
    )

    val SCRIPTS: List<Script> = listOf(
        Script(
            "banner", "Banner grab",
            "Reads up to 2 KB from every open port and guesses the service.",
            emptyList(), // applies to all open ports
        ),
        Script(
            "http-headers", "HTTP headers",
            "HEAD request; reports Server / X-Powered-By (version disclosure is flagged).",
            listOf(80, 8000, 8080, 8888, 5000, 9000),
        ),
        Script(
            "http-methods", "HTTP methods",
            "OPTIONS request; reports the observed Allow header.",
            listOf(80, 8000, 8080, 8888, 5000, 9000),
        ),
        Script(
            "ssl-cert", "TLS certificate",
            "Handshake; reports subject/issuer/expiry, self-signed and expired states.",
            listOf(443, 8443),
        ),
        Script(
            "ssh-banner", "SSH version",
            "Reads the SSH banner; flags legacy SSH-1.x protocol only.",
            listOf(22, 2222),
        ),
        Script(
            "ftp-anon", "FTP anonymous login",
            "Attempts USER anonymous (+ probe password); reports ALLOWED only on a 230 reply.",
            listOf(21, 2121),
        ),
        Script(
            "smtp-greet", "SMTP greeting",
            "Banner plus EHLO extensions (benign enumeration, no mail is sent).",
            listOf(25, 587, 2525),
        ),
        Script(
            "telnet-banner", "Telnet banner",
            "Captures the prompt; a visible login prompt means cleartext auth.",
            listOf(23, 2323),
        ),
    )

    fun get(id: String): Script? = SCRIPTS.firstOrNull { it.id == id }

    /** True when [script] should run against [port].
     *
     * Three gates (any one suffices): the banner script always runs; a port
     * match runs the script; otherwise the already-grabbed banner is matched
     * as evidence (greeting-first protocols reveal themselves, so ephemeral
     * or non-standard ports still get the right probes). Silent ports stay
     * scoped — no speculative timeouts are burned.
     */
    fun appliesTo(script: Script, port: Int, bannerHint: String = ""): Boolean {
        if (script.id == "banner") return true
        if (port in script.ports) return true
        val guess = guessService(bannerHint)
        return when (script.id) {
            "ssh-banner" -> guess == "ssh"
            "ftp-anon" -> guess == "ftp" || guess == "ftp-or-smtp"
            "smtp-greet" -> guess == "smtp" || guess == "ftp-or-smtp"
            "telnet-banner" -> guess == "telnet"
            "http-headers", "http-methods" -> looksHttp(bannerHint)
            else -> false // ssl-cert and unknown ids stay port-scoped
        }
    }

    // ------------------------------------------------------------ runner

    /**
     * Run [scriptIds] against every port in [openPorts] on [ip].
     * Findings sorted by (port, script). Never throws.
     */
    fun runScripts(
        ip: String,
        openPorts: List<Int>,
        scriptIds: Set<String>,
        timeoutMs: Int = 4000,
    ): List<Finding> {
        if (ip.isBlank() || openPorts.isEmpty() || scriptIds.isEmpty()) return emptyList()
        val scripts = SCRIPTS.filter { it.id in scriptIds }
        if (scripts.isEmpty()) return emptyList()
        val out = ArrayList<Finding>()
        for (port in openPorts.sorted().distinct()) {
            val banner = try {
                tcpRead(ip, port, timeoutMs, null)
            } catch (_: Exception) {
                null
            }
            for (script in scripts) {
                if (!appliesTo(script, port, banner ?: "")) continue
                try {
                    out += runOne(script.id, ip, port, banner, timeoutMs)
                } catch (_: Exception) {
                    // one script's failure never aborts the rest
                }
            }
        }
        return out.sortedWith(compareBy({ it.port }, { it.scriptId }))
    }

    private fun runOne(
        id: String,
        ip: String,
        port: Int,
        banner: String?,
        timeoutMs: Int,
    ): List<Finding> = when (id) {
        "banner" -> {
            if (banner.isNullOrBlank()) {
                listOf(Finding(id, "Banner grab", port, Level.INFO, "no banner received"))
            } else {
                val first = banner.lineSequence().firstOrNull()?.take(160) ?: ""
                listOf(
                    Finding(
                        id, "Banner grab", port, Level.INFO,
                        "service guess: ${guessService(banner)} — ${first.ifBlank { "(unprintable bytes)" }}",
                    ),
                )
            }
        }
        "http-headers" -> {
            val resp = tcpRead(ip, port, timeoutMs, "HEAD / HTTP/1.0\r\nHost: $ip\r\n\r\n".toByteArray())
            if (resp == null) {
                listOf(Finding(id, "HTTP headers", port, Level.INFO, "no HTTP response"))
            } else {
                val server = parseServerHeader(resp)
                val level = if (server != null && server.any { it.isDigit() }) Level.NOTE else Level.INFO
                listOf(
                    Finding(
                        id, "HTTP headers", port, level,
                        if (server != null) "Server: $server (version disclosed)" else "no Server header",
                    ),
                )
            }
        }
        "http-methods" -> {
            val resp = tcpRead(ip, port, timeoutMs, "OPTIONS * HTTP/1.0\r\nHost: $ip\r\n\r\n".toByteArray())
            if (resp == null) {
                listOf(Finding(id, "HTTP methods", port, Level.INFO, "no HTTP response"))
            } else {
                val allow = parseAllowHeader(resp)
                listOf(
                    Finding(
                        id, "HTTP methods", port, Level.INFO,
                        if (allow != null) "Allow: $allow" else "no Allow header advertised",
                    ),
                )
            }
        }
        "ssl-cert" -> {
            val info = fetchCertInfo(ip, port, timeoutMs)
            if (info == null) {
                listOf(Finding(id, "TLS certificate", port, Level.INFO, "handshake failed"))
            } else {
                listOf(Finding(id, "TLS certificate", port, info.level, info.detail))
            }
        }
        "ssh-banner" -> {
            val b = banner ?: tcpRead(ip, port, timeoutMs, null)
            if (b.isNullOrBlank()) {
                listOf(Finding(id, "SSH version", port, Level.INFO, "no SSH banner"))
            } else if (isLegacySsh(b)) {
                listOf(Finding(id, "SSH version", port, Level.WARN, "legacy SSH-1.x protocol: ${b.take(60)}"))
            } else {
                listOf(Finding(id, "SSH version", port, Level.INFO, b.lineSequence().first().take(80)))
            }
        }
        "ftp-anon" -> {
            val convo = ftpTryAnonymous(ip, port, timeoutMs)
            if (convo == null) {
                listOf(
                    Finding(id, "FTP anonymous login", port, Level.INFO, "no FTP greeting"),
                )
            } else if (convo.allowed) {
                listOf(
                    Finding(
                        id, "FTP anonymous login", port, Level.WARN,
                        "anonymous login ALLOWED${if (convo.withPassword) " (even with a probe password)" else ""}",
                    ),
                )
            } else {
                listOf(Finding(id, "FTP anonymous login", port, Level.NOTE, "anonymous login denied (${convo.lastCode})"))
            }
        }
        "smtp-greet" -> {
            val greet = banner ?: tcpRead(ip, port, timeoutMs, null)
            if (greet.isNullOrBlank()) {
                listOf(Finding(id, "SMTP greeting", port, Level.INFO, "no SMTP banner"))
            } else {
                val ext = smtpExtensions(ip, port, timeoutMs)
                val first = greet.lineSequence().firstOrNull()?.take(120) ?: ""
                listOf(
                    Finding(
                        id, "SMTP greeting", port, Level.INFO,
                        first + if (ext != null) " | EHLO: $ext" else " | EHLO refused/failed",
                    ),
                )
            }
        }
        "telnet-banner" -> {
            val b = banner ?: tcpRead(ip, port, timeoutMs, null)
            if (b.isNullOrBlank()) {
                listOf(Finding(id, "Telnet banner", port, Level.INFO, "no banner"))
            } else if (b.contains("login:", ignoreCase = true) || b.contains("username:", ignoreCase = true)) {
                listOf(Finding(id, "Telnet banner", port, Level.WARN, "login prompt captured — auth is cleartext"))
            } else {
                listOf(
                    Finding(id, "Telnet banner", port, Level.INFO, b.lineSequence().firstOrNull()?.take(120) ?: ""),
                )
            }
        }
        else -> emptyList()
    }

    // ------------------------------------------------------------ probes

    /** Connect, optionally send, read up to [maxBytes]; null on any failure. */
    fun tcpRead(ip: String, port: Int, timeoutMs: Int, send: ByteArray?, maxBytes: Int = 2048): String? {
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), timeoutMs)
                s.soTimeout = timeoutMs
                if (send != null) {
                    s.getOutputStream().write(send)
                    s.getOutputStream().flush()
                }
                val buf = ByteArray(maxBytes)
                val out = StringBuilder()
                try {
                    while (out.length < maxBytes) {
                        val n = s.getInputStream().read(buf, 0, minOf(buf.size, maxBytes - out.length))
                        if (n <= 0) break
                        out.append(String(buf, 0, n, Charsets.UTF_8))
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // partial read is fine
                }
                val text = out.toString()
                return text.ifBlank { null }
            }
        } catch (_: Exception) {
            return null
        }
    }

    private data class FtpAnon(val allowed: Boolean, val withPassword: Boolean, val lastCode: String)

    /** USER anonymous [+ probe PASS]; allowed only on an observed 230. */
    private fun ftpTryAnonymous(ip: String, port: Int, timeoutMs: Int): FtpAnon? {
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), timeoutMs)
                s.soTimeout = timeoutMs
                val rd = s.getInputStream().bufferedReader(Charsets.UTF_8)
                val wr = s.getOutputStream().bufferedWriter(Charsets.UTF_8)
                val greet = try {
                    rd.readLine()
                } catch (_: Exception) {
                    null
                } ?: return null
                if (!greet.startsWith("220")) return FtpAnon(false, false, greet.take(3))
                fun cmd(line: String): String {
                    wr.write(line + "\r\n")
                    wr.flush()
                    return try {
                        rd.readLine() ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                }
                val userResp = cmd("USER anonymous")
                if (userResp.startsWith("230")) return FtpAnon(true, false, "230")
                if (!userResp.startsWith("331")) return FtpAnon(false, false, userResp.take(3).ifBlank { "???" })
                val passResp = cmd("PASS anon@qcli.invalid")
                if (passResp.startsWith("230")) return FtpAnon(true, true, "230")
                return FtpAnon(false, true, passResp.take(3).ifBlank { "???" })
            }
        } catch (_: Exception) {
            return null
        }
    }

    /** EHLO extension list, space-joined, or null. */
    fun smtpExtensions(ip: String, port: Int, timeoutMs: Int): String? {
        val resp = tcpRead(ip, port, timeoutMs, "EHLO qcli\r\n".toByteArray(), 2048) ?: return null
        val exts = resp.lineSequence()
            .mapNotNull { line ->
                val m = Regex("^250[ -](.+)$", RegexOption.IGNORE_CASE).find(line.trim())
                m?.groupValues?.getOrNull(1)?.trim()?.take(64)
            }
            .filter { it.isNotBlank() && !it.equals("hello", ignoreCase = true) }
            .distinct()
            .toList()
        return exts.joinToString(" ").ifBlank { null }
    }

    data class CertInfo(val level: Level, val detail: String)

    /** TLS handshake; subject/issuer/expiry observed from the live chain. */
    fun fetchCertInfo(ip: String, port: Int, timeoutMs: Int): CertInfo? {
        return try {
            val factory = SSLSocketFactory.getDefault()
            (factory.createSocket() as javax.net.ssl.SSLSocket).use { sock ->
                sock.connect(InetSocketAddress(ip, port), timeoutMs)
                sock.soTimeout = timeoutMs
                sock.startHandshake()
                val cert = sock.session.peerCertificates.firstOrNull() as? java.security.cert.X509Certificate
                    ?: return null
                val dn = { p: java.security.Principal -> p.name.substringAfter("CN=").substringBefore(",").take(64) }
                summarizeCert(
                    subject = dn(cert.subjectDN),
                    issuer = dn(cert.issuerDN),
                    selfSigned = cert.subjectDN.name == cert.issuerDN.name,
                    notAfterMs = cert.notAfter.time,
                    nowMs = System.currentTimeMillis(),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------ pure helpers

    /** Service guess from banner keywords (best-effort label, not a fingerprint). */
    fun guessService(banner: String): String {
        val b = banner.lowercase()
        return when {
            b.startsWith("ssh-") -> "ssh"
            b.startsWith("220") && ("ftp" in b || "vsftpd" in b || "filezilla" in b) -> "ftp"
            b.startsWith("220") && ("smtp" in b || "esmtp" in b || "postfix" in b || "exim" in b) -> "smtp"
            b.startsWith("220") -> "ftp-or-smtp"
            b.startsWith("http/") || "server:" in b -> "http"
            "rtsp" in b -> "rtsp"
            "mysql" in b || "mariadb" in b -> "mysql"
            "redis" in b || "+pong" in b -> "redis"
            "telnet" in b || "login:" in b -> "telnet"
            "adb" in b || "cnxn" in b -> "adb?"
            "smb" in b || "samba" in b -> "smb?"
            else -> "unknown-tcp"
        }
    }

    fun looksHttp(banner: String): Boolean {
        val b = banner.trimStart()
        return b.startsWith("HTTP/", ignoreCase = true) ||
            "server:" in b.lowercase() ||
            "<html" in b.lowercase()
    }

    /** First Server:/X-Powered-By: header value, or null. */
    fun parseServerHeader(resp: String): String? {
        for (line in resp.lineSequence()) {
            val t = line.trim()
            if (t.startsWith("Server:", ignoreCase = true) || t.startsWith("X-Powered-By:", ignoreCase = true)) {
                return t.substringAfter(":").trim().take(120).ifBlank { null }
            }
        }
        return null
    }

    /** Allow header value, or null. */
    fun parseAllowHeader(resp: String): String? {
        for (line in resp.lineSequence()) {
            val t = line.trim()
            if (t.startsWith("Allow:", ignoreCase = true)) {
                return t.substringAfter(":").trim().take(160).ifBlank { null }
            }
        }
        return null
    }

    /** SSH-1.x banners only (protocol 1 is obsolete by design — no CVE needed). */
    fun isLegacySsh(banner: String): Boolean {
        val first = banner.lineSequence().firstOrNull()?.trim() ?: return false
        return first.startsWith("SSH-1.", ignoreCase = true)
    }

    /** Human summary of observed cert facts; pure date math (unit-tested). */
    fun summarizeCert(
        subject: String,
        issuer: String,
        selfSigned: Boolean,
        notAfterMs: Long,
        nowMs: Long,
    ): CertInfo {
        val daysLeft = (notAfterMs - nowMs) / 86_400_000L
        val base = "subject=$subject issuer=$issuer expires in $daysLeft day(s)"
        return when {
            daysLeft < 0 -> CertInfo(Level.WARN, "$base — EXPIRED")
            selfSigned -> CertInfo(Level.NOTE, "$base — self-signed")
            daysLeft < 30 -> CertInfo(Level.NOTE, "$base — expiring soon")
            else -> CertInfo(Level.INFO, base)
        }
    }
}
