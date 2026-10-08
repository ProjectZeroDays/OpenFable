package com.projectzerodays.quantumcli.c2

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * QcliApi.handle contract (JVM, no Android Context): JSON in / JSON out,
 * unknown commands and bad params return {"error": ...} — never a crash.
 * Parity with the Python C2's QCLI_API dispatcher.
 */
class QcliApiTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var api: QcliApi

    @Before
    fun setUp() {
        C2State.init(tmp.root)
        api = QcliApi()
    }

    @Test
    fun `status returns ok with version`() {
        val res = api.handle("status")
        assertTrue(res.optBoolean("ok"))
        assertEquals(C2State.VERSION, res.optString("version"))
    }

    @Test
    fun `empty command is status`() {
        val res = api.handle("")
        assertTrue(res.optBoolean("ok"))
    }

    @Test
    fun `unknown command returns error json never throws`() {
        val res = api.handle("definitely-not-a-command")
        assertTrue(res.optString("error").contains("unknown cmd"))
    }

    @Test
    fun `commands needing an ip reject invalid ips with error json`() {
        // 999.x is an invalid IPv4 literal: no DNS, fails fast
        val res = api.handle("zerocheck", JSONObject().put("ip", "999.1.1.1"))
        assertTrue(res.optString("error").contains("valid 'ip' param required"))
    }

    @Test
    fun `commands needing an ip reject a missing ip`() {
        val res = api.handle("zerocheck", JSONObject())
        assertTrue(res.optString("error").contains("valid 'ip' param required"))
    }

    @Test
    fun `fire requires a cve param`() {
        val res = api.handle(
            "fire",
            JSONObject().put("ip", "127.0.0.1").put("params", JSONObject()),
        )
        assertTrue(res.optString("error").contains("'cve' param required"))
    }

    @Test
    fun `fire with unknown cve returns error json never throws`() {
        val res = api.handle(
            "fire",
            JSONObject().put("ip", "127.0.0.1").put("cve", "CVE-0000-0000"),
        )
        // Weapons.fire on an unknown cve must be a clean error, not a crash
        assertTrue(res.has("error") || res.has("status"))
    }

    @Test
    fun `decide returns an action from the offered acts`() {
        val state = JSONObject().put("cams", JSONArray()).put("fired", JSONArray())
        val res = api.handle(
            "decide",
            JSONObject().put("state", state).put("acts", JSONArray(listOf("cam_war", "rest"))),
        )
        assertTrue(res.optString("action").isNotBlank())
    }

    @Test
    fun `plan returns a cve string`() {
        val recon = JSONObject()
            .put("ms17_010", true)
            .put("ports", JSONArray(listOf(445, 135)))
        val res = api.handle("plan", JSONObject().put("recon", recon))
        assertTrue(res.optString("cve").startsWith("CVE-"))
    }

    @Test
    fun `kill without wipe returns killed list`() {
        val res = api.handle("kill", JSONObject().put("wipe", false))
        assertTrue(res.has("killed"))
    }

    @Test
    fun `ghostdns requires a domain param`() {
        val res = api.handle("ghostdns", JSONObject())
        assertTrue(res.optString("error").contains("'domain' param required"))
    }

    @Test
    fun `persistx requires a host param`() {
        val res = api.handle("persistx", JSONObject())
        assertTrue(res.optString("error").contains("'host' param required"))
    }

    @Test
    fun `dohc2 rejects unknown action`() {
        val res = api.handle("dohc2", JSONObject().put("action", "sideways"))
        assertTrue(res.optString("error").contains("'action' must be"))
    }
}
