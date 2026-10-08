package com.projectzerodays.quantumcli.ops

import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Honeypot engine tests — real loopback sockets (ephemeral ports), no
 * external traffic. Exercises the full accept → banner → capture → hit path.
 */
class HoneypotTest {

    private val started = mutableListOf<String>()

    @After
    fun tearDown() {
        started.forEach { Honeypot.stop(it) }
        started.clear()
        Honeypot.clearHits()
    }

    private fun startEphemeral(id: String, svc: Honeypot.Service): Int {
        val res = Honeypot.start(svc.copy(port = 0))
        assertTrue("start failed: $res", res.startsWith("started"))
        started.add(id)
        val port = Honeypot.boundPort(id)
        assertNotNull("no bound port for $id", port)
        return port!!
    }

    private fun waitForHits(n: Int, timeoutMs: Long = 5_000): List<Honeypot.Hit> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (Honeypot.hits.value.size >= n) return Honeypot.hits.value
            Thread.sleep(50)
        }
        return Honeypot.hits.value
    }

    private fun waitForDetail(timeoutMs: Long = 5_000): Honeypot.Hit? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Honeypot.hits.value.lastOrNull()?.let { if (it.detail.isNotEmpty()) return it }
            Thread.sleep(50)
        }
        return Honeypot.hits.value.lastOrNull()
    }

    @Test
    fun presetsAreUnprivilegedAndUnique() {
        val ids = Honeypot.PRESETS.map { it.id }
        assertEquals(ids.distinct(), ids)
        Honeypot.PRESETS.forEach { svc ->
            assertTrue("${svc.id} port must be >=1024 (no-root)", svc.port >= 1024)
            assertTrue(svc.name.isNotBlank())
            assertFalse("${svc.id} must not be marked root-only", svc.rootOnly)
        }
        // the presets the dashboard promises are all present
        for (id in listOf("http-admin", "ssh", "ftp", "telnet", "mqtt", "redis", "mysql")) {
            assertTrue("missing preset $id", ids.contains(id))
        }
    }

    @Test
    fun startBannerCaptureStopOverLoopback() {
        val svc = Honeypot.Service(
            id = "t-ftp", name = "FTP test", banner = "220 ready\r\n", port = 0,
            replies = listOf("331 need password\r\n"),
        )
        val port = startEphemeral("t-ftp", svc)

        Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", port), 3_000)
            s.soTimeout = 3_000
            val banner = s.getInputStream().bufferedReader().readLine()
            assertEquals("220 ready", banner)

            s.getOutputStream().write("USER root\r\n".toByteArray())
            s.getOutputStream().flush()
            val reply = s.getInputStream().bufferedReader().readLine()
            assertEquals("331 need password", reply)
        }

        val hits = waitForHits(1)
        assertEquals(1, hits.size)
        assertEquals("t-ftp", hits[0].serviceId)
        assertEquals("127.0.0.1", hits[0].remoteIp)

        val withDetail = waitForDetail()
        assertNotNull(withDetail)
        assertTrue(
            "captured detail should contain the probe, got: ${withDetail!!.detail}",
            withDetail.detail.contains("USER root"),
        )

        val stopRes = Honeypot.stop("t-ftp")
        assertEquals("stopped", stopRes)
        assertFalse(Honeypot.runningIds.value.contains("t-ftp"))
    }

    @Test
    fun hitRecordedEvenWithoutPayload() {
        val svc = Honeypot.Service(
            id = "t-silent", name = "silent", banner = "", port = 0,
        )
        val port = startEphemeral("t-silent", svc)
        Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", port), 3_000)
            // send nothing — silent scanner
        }
        val hits = waitForHits(1)
        assertEquals(1, hits.size)
        assertEquals("", hits[0].detail)
        assertTrue(hits[0].remotePort > 0)
    }

    @Test
    fun doubleStartIsRefused() {
        val svc = Honeypot.Service(
            id = "t-dup", name = "dup", banner = "", port = 0,
        )
        startEphemeral("t-dup", svc)
        val again = Honeypot.start(svc)
        assertTrue(again.contains("already running"))
    }

    @Test
    fun privilegedPortRejectedHonestly() {
        val res = Honeypot.start(
            Honeypot.Service(id = "t-priv", name = "ssh22", banner = "x", port = 22),
        )
        assertTrue(res.contains("root required"))
        assertFalse(Honeypot.runningIds.value.contains("t-priv"))
    }

    @Test
    fun busyPortReportsBindFailure() {
        val svc = Honeypot.Service(
            id = "t-busy", name = "busy", banner = "", port = 0,
        )
        val port = startEphemeral("t-busy", svc)
        // second service forced onto the same port -> honest bind error
        val res = Honeypot.start(svc.copy(id = "t-busy-2").let { it.copy(port = port) })
        assertTrue("got: $res", res.contains("bind failed"))
        started.add("t-busy-2") // no-op if never started; safe to stop
    }

    @Test
    fun exportJsonIsValidAndEscaped() {
        Honeypot.recordForTest(
            Honeypot.Hit(
                ts = 123L, serviceId = "http-admin",
                remoteIp = "10.0.0.9", remotePort = 44444,
                detail = "path \"/admin\" quote\\slash\nnewline\ttab",
            ),
        )
        Honeypot.recordForTest(
            Honeypot.Hit(124L, "ssh", "10.0.0.8", 5555, ""),
        )
        val json = Honeypot.exportJson()
        val arr = JSONArray(json)
        assertEquals(2, arr.length())
        assertEquals("http-admin", arr.getJSONObject(0).getString("service"))
        assertEquals("10.0.0.9", arr.getJSONObject(0).getString("ip"))
        assertEquals(44444, arr.getJSONObject(0).getInt("port"))
        assertEquals(
            "path \"/admin\" quote\\slash\nnewline\ttab",
            arr.getJSONObject(0).getString("detail"),
        )
        assertEquals("", arr.getJSONObject(1).getString("detail"))
        assertEquals(124L, arr.getJSONObject(1).getLong("ts"))
    }

    @Test
    fun clearHitsEmptiesLog() {
        Honeypot.recordForTest(Honeypot.Hit(1L, "ssh", "1.2.3.4", 1, "x"))
        assertEquals(1, Honeypot.hits.value.size)
        Honeypot.clearHits()
        assertEquals(0, Honeypot.hits.value.size)
    }

    @Test
    fun stopUnknownServiceIsHonest() {
        assertEquals("not running", Honeypot.stop("never-existed"))
    }
}
