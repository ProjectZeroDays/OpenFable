package com.projectzerodays.quantumcli.ops

import org.junit.Assert.assertEquals
import org.junit.Test

class ZenScanTest {

    @Test
    fun `port spec parses lists and ranges`() {
        assertEquals(
            listOf(22, 80, 443),
            ZenScan.parsePorts("443,22,80")
        )
        assertEquals(
            (1000..1002).toList() + listOf(8080),
            ZenScan.parsePorts("1000-1002,8080")
        )
    }

    @Test
    fun `port spec is sorted deduped and capped`() {
        assertEquals(listOf(80, 443), ZenScan.parsePorts("80,443,80,443"))
        assertEquals(100, ZenScan.parsePorts("1-500", cap = 100).size)
    }

    @Test
    fun `invalid segments are skipped never thrown`() {
        assertEquals(emptyList<Int>(), ZenScan.parsePorts(""))
        assertEquals(listOf(80), ZenScan.parsePorts("nope, 80, 99999, 0, -5-3"))
        assertEquals(emptyList<Int>(), ZenScan.parsePorts("9-2")) // reversed range
    }

    @Test
    fun `timing templates map to worker and timeout pairs`() {
        assertEquals(1 to 1500, ZenScan.timingProfile(0))
        assertEquals(64 to 500, ZenScan.timingProfile(3))
        assertEquals(256 to 250, ZenScan.timingProfile(5))
        // out-of-range clamps
        assertEquals(1 to 1500, ZenScan.timingProfile(-2))
        assertEquals(256 to 250, ZenScan.timingProfile(99))
    }

    @Test
    fun `timing labels match nmap names`() {
        assertEquals("T0 paranoid", ZenScan.timingLabel(0))
        assertEquals("T5 insane", ZenScan.timingLabel(5))
    }

    @Test
    fun `empty scan inputs return empty results`() {
        assertEquals(emptyList<ZenScan.HostResult>(), ZenScan.scan(emptyList(), listOf(80), 3))
        assertEquals(emptyList<ZenScan.HostResult>(), ZenScan.scan(listOf("10.0.0.1"), emptyList(), 3))
    }
}
