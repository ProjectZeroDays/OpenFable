package com.projectzerodays.quantumcli.ops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure-helper tests — no device, no sockets. */
class AdbControlTest {

    @Test
    fun parseTargetAppliesDefaultPort() {
        assertEquals("192.168.1.5" to 5555, AdbControl.parseTarget("192.168.1.5"))
        assertEquals("host.local" to 5555, AdbControl.parseTarget("  host.local  "))
    }

    @Test
    fun parseTargetHonorsExplicitPort() {
        assertEquals("1.2.3.4" to 5554, AdbControl.parseTarget("1.2.3.4:5554"))
        assertEquals("1.2.3.4" to 42, AdbControl.parseTarget("1.2.3.4:42"))
    }

    @Test
    fun parseTargetRejectsGarbage() {
        assertNull(AdbControl.parseTarget(""))
        assertNull(AdbControl.parseTarget("   "))
        assertNull(AdbControl.parseTarget("has space:5555"))
        assertNull(AdbControl.parseTarget("1.2.3.4:notaport"))
        assertNull(AdbControl.parseTarget("1.2.3.4:0"))
        assertNull(AdbControl.parseTarget("1.2.3.4:70000"))
        assertNull(AdbControl.parseTarget(":5555"))
        // IPv6 targets rejected explicitly (multi-colon)
        assertNull(AdbControl.parseTarget("[::1]:5555"))
    }

    @Test
    fun parsePackagesStripsPrefixSortsAndDedupes() {
        val out = """
            package:com.zeta.app
            package:com.alpha.app
            package:com.zeta.app

            package:org.beta.tool
        """.trimIndent()
        assertEquals(
            listOf("com.alpha.app", "com.zeta.app", "org.beta.tool"),
            AdbControl.parsePackages(out),
        )
        assertEquals(emptyList<String>(), AdbControl.parsePackages("garbage\nno prefix"))
    }

    @Test
    fun getpropValueExtractsBracketedValue() {
        val props = """
            [ro.product.model]: [Pixel 8]
            [ro.build.version.release]: [14]
            [ro.build.version.sdk]: [34]
        """.trimIndent()
        assertEquals("Pixel 8", AdbControl.getpropValue(props, "ro.product.model"))
        assertEquals("14", AdbControl.getpropValue(props, "ro.build.version.release"))
        assertEquals("34", AdbControl.getpropValue(props, "ro.build.version.sdk"))
        assertNull(AdbControl.getpropValue(props, "ro.missing.key"))
    }

    @Test
    fun inputCommandBuildersAreSafe() {
        assertEquals("input tap 100 200", AdbControl.tapCmd(100, 200))
        // out-of-range coords clamp instead of emitting nonsense
        assertEquals("input tap 0 99999", AdbControl.tapCmd(-5, 123_456))
        assertEquals(
            "input swipe 0 100 500 900 300",
            AdbControl.swipeCmd(0, 100, 500, 900, 300),
        )
        assertEquals("input keyevent 3", AdbControl.keyCmd(3))
        assertEquals("input keyevent 0", AdbControl.keyCmd(-1))
    }

    @Test
    fun textArgEscapesSpacesAndQuotes() {
        assertEquals("hello%sworld", AdbControl.textArg("hello world"))
        // quotes/backslashes/newlines stripped — never shell-interpreted
        assertEquals("rm%sthefile", AdbControl.textArg("rm the\"file'"))
        assertEquals("nonewlines", AdbControl.textArg("no\nnewlines"))
        // long text is capped
        assertEquals(512, AdbControl.textArg("a".repeat(1000)).length)
        assertEquals("input text hello%sworld", AdbControl.textCmd("hello world"))
    }
}
