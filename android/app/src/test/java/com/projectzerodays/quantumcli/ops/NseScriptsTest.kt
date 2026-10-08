package com.projectzerodays.quantumcli.ops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * NSE-script tests: catalog integrity + pure helpers, plus live loopback
 * servers proving the probes observe real replies (no network beyond
 * 127.0.0.1).
 */
class NseScriptsTest {

    @Test
    fun catalogIsUniqueAndHonest() {
        val ids = NseScripts.SCRIPTS.map { it.id }
        assertEquals(ids.distinct(), ids)
        assertTrue(ids.size >= 8)
        NseScripts.SCRIPTS.forEach { s ->
            assertTrue(s.title.isNotBlank())
            assertTrue(s.desc.isNotBlank())
            s.ports.forEach { assertTrue("$s out of range", it in 1..65535) }
        }
        assertTrue(NseScripts.SCRIPTS.any { it.id == "banner" })
        assertTrue(NseScripts.SCRIPTS.any { it.id == "ftp-anon" })
    }

    @Test
    fun bannerAppliesEverywhereOthersArePortScoped() {
        val banner = NseScripts.get("banner")!!
        val http = NseScripts.get("http-headers")!!
        val ssh = NseScripts.get("ssh-banner")!!
        val ftp = NseScripts.get("ftp-anon")!!
        val smtp = NseScripts.get("smtp-greet")!!
        val telnet = NseScripts.get("telnet-banner")!!
        val ssl = NseScripts.get("ssl-cert")!!
        assertTrue(NseScripts.appliesTo(banner, 9999))
        assertFalse(NseScripts.appliesTo(http, 9999))
        assertTrue(NseScripts.appliesTo(http, 80))
        // evidence-driven: greeting banners pull the right probes on any port
        assertTrue(NseScripts.appliesTo(http, 9999, "HTTP/1.1 200 OK\r\nServer: x\r\n"))
        assertTrue(NseScripts.appliesTo(ssh, 9999, "SSH-2.0-OpenSSH_8.9\r\n"))
        assertTrue(NseScripts.appliesTo(ftp, 9999, "220 (vsFTPd 3.0.5)\r\n"))
        assertTrue(NseScripts.appliesTo(smtp, 9999, "220 mail ESMTP Postfix\r\n"))
        assertTrue(NseScripts.appliesTo(telnet, 9999, "\r\nlogin: "))
        // wrong-hint and silent ports stay scoped (no speculative timeouts)
        assertFalse(NseScripts.appliesTo(ssh, 9999, "HTTP/1.1"))
        assertFalse(NseScripts.appliesTo(ftp, 9999, ""))
        assertFalse(NseScripts.appliesTo(ssl, 9999, "SSH-2.0-x"))
        assertFalse(NseScripts.appliesTo(ssl, 9999, ""))
    }

    @Test
    fun serviceGuessesCoverCommonBanners() {
        assertEquals("ssh", NseScripts.guessService("SSH-2.0-OpenSSH_8.9\r\n"))
        assertEquals("ftp", NseScripts.guessService("220 (vsFTPd 3.0.5)\r\n"))
        assertEquals("smtp", NseScripts.guessService("220 mail ESMTP Postfix\r\n"))
        assertEquals("http", NseScripts.guessService("HTTP/1.1 200 OK\r\nServer: nginx\r\n"))
        assertEquals("telnet", NseScripts.guessService("\r\nlogin: "))
        assertEquals("unknown-tcp", NseScripts.guessService("??? garbled \u0000\u0001"))
    }

    @Test
    fun headerParsersExtractFirstValues() {
        val resp = "HTTP/1.1 200 OK\r\nServer: TestSrv/1.0\r\nX-Powered-By: PHP/8.1\r\n\r\n"
        assertEquals("TestSrv/1.0", NseScripts.parseServerHeader(resp))
        assertEquals(null, NseScripts.parseServerHeader("HTTP/1.1 200 OK\r\n\r\n"))
        val opts = "HTTP/1.1 200 OK\r\nAllow: GET, HEAD, OPTIONS\r\n\r\n"
        assertEquals("GET, HEAD, OPTIONS", NseScripts.parseAllowHeader(opts))
        assertEquals(null, NseScripts.parseAllowHeader("HTTP/1.1 200 OK\r\n\r\n"))
    }

    @Test
    fun legacySshDetectionIsVersionPrefixOnly() {
        assertTrue(NseScripts.isLegacySsh("SSH-1.5-1.2.27\r\n"))
        assertTrue(NseScripts.isLegacySsh("ssh-1.99-openssh"))
        assertFalse(NseScripts.isLegacySsh("SSH-2.0-OpenSSH_8.9\r\n"))
        assertFalse(NseScripts.isLegacySsh(""))
        assertFalse(NseScripts.isLegacySsh("not ssh at all"))
    }

    @Test
    fun certSummaryGradesObservedDates() {
        val now = 1_700_000_000_000L
        val day = 86_400_000L
        val ok = NseScripts.summarizeCert("example.com", "Some CA", false, now + 90 * day, now)
        assertEquals(NseScripts.Level.INFO, ok.level)
        assertTrue(ok.detail.contains("90 day(s)"))
        val soon = NseScripts.summarizeCert("example.com", "Some CA", false, now + 5 * day, now)
        assertEquals(NseScripts.Level.NOTE, soon.level)
        val self = NseScripts.summarizeCert("example.com", "example.com", true, now + 90 * day, now)
        assertEquals(NseScripts.Level.NOTE, self.level)
        assertTrue(self.detail.contains("self-signed"))
        val expired = NseScripts.summarizeCert("example.com", "Some CA", false, now - day, now)
        assertEquals(NseScripts.Level.WARN, expired.level)
        assertTrue(expired.detail.contains("EXPIRED"))
    }

    // ------------------------------------------------------- loopback probes

    /** Run [handler] per accepted connection on an ephemeral loopback port. */
    private fun fakeServer(handler: (Socket) -> Unit): Pair<ServerSocket, Int> {
        val server = ServerSocket()
        server.bind(InetSocketAddress("127.0.0.1", 0))
        val port = server.localPort
        Thread({
            try {
                while (!server.isClosed) {
                    val sock = try {
                        server.accept()
                    } catch (_: Exception) {
                        return@Thread
                    }
                    Thread({ handler(sock) }, "fake-svc").apply { isDaemon = true }.start()
                }
            } catch (_: Exception) {
            }
        }, "fake-accept").apply { isDaemon = true }.start()
        return server to port
    }

    @Test
    fun sshBannerProbeReadsVersionAndFlagsLegacy() {
        val (server, port) = fakeServer { sock ->
            sock.use { s ->
                s.getOutputStream().write("SSH-2.0-OpenSSH_8.9p1 TestOS\r\n".toByteArray())
                s.getOutputStream().flush()
                Thread.sleep(300)
            }
        }
        try {
            val findings = NseScripts.runScripts("127.0.0.1", listOf(port), setOf("ssh-banner", "banner"))
            assertTrue(findings.any { it.scriptId == "ssh-banner" && it.level == NseScripts.Level.INFO })
            assertTrue(findings.any { it.scriptId == "banner" && it.detail.contains("ssh") })
        } finally {
            server.close()
        }

        val (server2, port2) = fakeServer { sock ->
            sock.use { s ->
                s.getOutputStream().write("SSH-1.5-1.2.27\r\n".toByteArray())
                s.getOutputStream().flush()
                Thread.sleep(300)
            }
        }
        try {
            val findings = NseScripts.runScripts("127.0.0.1", listOf(port2), setOf("ssh-banner"))
            assertTrue(findings.any { it.level == NseScripts.Level.WARN })
        } finally {
            server2.close()
        }
    }

    @Test
    fun ftpAnonymousProbeDistinguishesAllowedFromDenied() {
        val (server, port) = fakeFtpServer(allowAnonymous = true)
        try {
            val findings = NseScripts.runScripts("127.0.0.1", listOf(port), setOf("ftp-anon"))
            val f = findings.firstOrNull { it.scriptId == "ftp-anon" }
            assertTrue(f != null && f.level == NseScripts.Level.WARN && f.detail.contains("ALLOWED"))
        } finally {
            server.close()
        }

        val (server2, port2) = fakeFtpServer(allowAnonymous = false)
        try {
            val findings = NseScripts.runScripts("127.0.0.1", listOf(port2), setOf("ftp-anon"))
            val f = findings.firstOrNull { it.scriptId == "ftp-anon" }
            assertTrue(f != null && (f.level == NseScripts.Level.NOTE || f.level == NseScripts.Level.INFO))
            assertTrue(f!!.detail.contains("denied") || f.detail.contains("no FTP"))
        } finally {
            server2.close()
        }
    }

    private fun fakeFtpServer(allowAnonymous: Boolean): Pair<ServerSocket, Int> {
        return fakeServer { sock ->
            sock.use { s ->
                val rd = BufferedReader(InputStreamReader(s.getInputStream()))
                val wr = OutputStreamWriter(s.getOutputStream())
                wr.write("220 FakeFTP ready\r\n")
                wr.flush()
                val user = try {
                    rd.readLine()
                } catch (_: Exception) {
                    null
                } ?: return@use
                if (!user.startsWith("USER")) return@use
                if (allowAnonymous) {
                    wr.write("230 Login successful.\r\n")
                    wr.flush()
                } else {
                    wr.write("530 Login incorrect.\r\n")
                    wr.flush()
                }
                Thread.sleep(200)
            }
        }
    }

    @Test
    fun httpHeaderProbeReadsServerBanner() {
        // Harness note: the fake greets immediately (real HTTP servers wait
        // for the request). This exercises dispatch-by-evidence plus the real
        // HEAD-over-socket probe and header parse; ordering is irrelevant to
        // what is verified.
        val (server, port) = fakeServer { sock ->
            sock.use { s ->
                val out = s.getOutputStream()
                out.write("HTTP/1.1 200 OK\r\nServer: FakeSrv/9.9\r\n\r\n".toByteArray())
                out.flush()
                val rd = BufferedReader(InputStreamReader(s.getInputStream()))
                try {
                    while (true) {
                        val line = rd.readLine() ?: break
                        if (line.isBlank()) break
                    }
                } catch (_: Exception) {
                }
                val body = "HTTP/1.1 200 OK\r\nServer: FakeSrv/9.9\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                out.write(body.toByteArray())
                out.flush()
            }
        }
        try {
            val findings = NseScripts.runScripts("127.0.0.1", listOf(port), setOf("http-headers", "banner"))
            val h = findings.firstOrNull { it.scriptId == "http-headers" }
            assertTrue(h != null && h.detail.contains("FakeSrv/9.9"))
            assertTrue(h!!.level == NseScripts.Level.NOTE) // version disclosed
        } finally {
            server.close()
        }
    }

    @Test
    fun smtpGreetProbeListsExtensions() {
        val (server, port) = fakeServer { sock ->
            sock.use { s ->
                val rd = BufferedReader(InputStreamReader(s.getInputStream()))
                val wr = OutputStreamWriter(s.getOutputStream())
                wr.write("220 fake ESMTP TestMTA\r\n")
                wr.flush()
                val line = try {
                    rd.readLine()
                } catch (_: Exception) {
                    null
                } ?: return@use
                if (line.startsWith("EHLO", ignoreCase = true)) {
                    wr.write("250-Hello\r\n250 AUTH PLAIN\r\n")
                    wr.flush()
                }
                Thread.sleep(200)
            }
        }
        try {
            val findings = NseScripts.runScripts("127.0.0.1", listOf(port), setOf("smtp-greet"))
            val f = findings.firstOrNull { it.scriptId == "smtp-greet" }
            assertTrue(f != null && f.detail.contains("AUTH PLAIN"))
        } finally {
            server.close()
        }
    }

    @Test
    fun telnetPromptIsFlaggedCleartext() {
        val (server, port) = fakeServer { sock ->
            sock.use { s ->
                s.getOutputStream().write("\r\nFakeOS login: ".toByteArray())
                s.getOutputStream().flush()
                Thread.sleep(300)
            }
        }
        try {
            val findings = NseScripts.runScripts("127.0.0.1", listOf(port), setOf("telnet-banner"))
            val f = findings.firstOrNull { it.scriptId == "telnet-banner" }
            assertTrue(f != null && f.level == NseScripts.Level.WARN)
        } finally {
            server.close()
        }
    }

    @Test
    fun closedPortYieldsNoFindings() {
        // ephemeral port with nothing listening (bound then released)
        val tmp = ServerSocket()
        tmp.bind(InetSocketAddress("127.0.0.1", 0))
        val port = tmp.localPort
        tmp.close()
        Thread.sleep(100)
        val findings = NseScripts.runScripts(
            "127.0.0.1", listOf(port),
            setOf("banner", "ssh-banner", "http-headers", "ftp-anon", "telnet-banner"),
        )
        assertTrue(findings.all { it.detail.contains("no ") || it.detail.contains("failed") })
    }
}
