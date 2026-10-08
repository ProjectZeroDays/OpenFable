package com.projectzerodays.quantumcli.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MlTelemetryTest {

    @Test
    fun emptyStatsAreAllZeros() {
        val s = MlTelemetry.stats(emptyList())
        assertEquals(0, s.count)
        assertEquals(0, s.okCount)
        assertEquals(0, s.failCount)
        assertEquals(0L, s.avgLatencyMs)
        assertEquals(0L, s.maxLatencyMs)
        assertEquals(0L, s.p95LatencyMs)
        assertEquals(0, s.totalOutChars)
        assertEquals(0.0, s.charsPerSec, 0.0001)
        assertEquals(0, s.estTokens)
    }

    @Test
    fun statsMathOverKnownSamples() {
        val samples = listOf(
            MlTelemetry.Sample(1L, "local", 10, 40, true),
            MlTelemetry.Sample(2L, "local", 20, 80, true),
            MlTelemetry.Sample(3L, "online", 30, 120, false),
            MlTelemetry.Sample(4L, "online", 40, 160, true),
        )
        val s = MlTelemetry.stats(samples)
        assertEquals(4, s.count)
        assertEquals(3, s.okCount)
        assertEquals(1, s.failCount)
        assertEquals(25L, s.avgLatencyMs)
        assertEquals(40L, s.maxLatencyMs)
        // p95 of 4 samples → ceil(0.95*4)=4 → index 3 → 40
        assertEquals(40L, s.p95LatencyMs)
        assertEquals(400, s.totalOutChars)
        // 400 chars over 100 ms → 4000 chars/s
        assertEquals(4000.0, s.charsPerSec, 0.0001)
        assertEquals(100, s.estTokens)
    }

    @Test
    fun p95WithManySamplesIsNotTheMax() {
        val samples = (1..20).map {
            MlTelemetry.Sample(it.toLong(), "local", it * 10L, 4, true)
        }
        val s = MlTelemetry.stats(samples)
        // ceil(0.95*20)=19 → index 18 → 190 ms (max is 200)
        assertEquals(190L, s.p95LatencyMs)
        assertEquals(200L, s.maxLatencyMs)
    }

    @Test
    fun recordKeepsRingBufferCapAndNewest() {
        MlTelemetry.clear()
        for (i in 1..250) {
            MlTelemetry.record("local", 5, 10, true, now = i.toLong())
        }
        val buf = MlTelemetry.samples.value
        assertEquals(200, buf.size)
        assertEquals(51L, buf.first().ts)   // oldest retained sample
        assertEquals(250L, buf.last().ts)   // newest kept
    }

    @Test
    fun negativeLatencyIsClamped() {
        MlTelemetry.clear()
        MlTelemetry.record("local", -50, 8, true, now = 1L)
        assertEquals(0L, MlTelemetry.samples.value.last().latencyMs)
    }

    @Test
    fun clearEmptiesBuffer() {
        MlTelemetry.record("online", 12, 4, true, now = 1L)
        assertTrue(MlTelemetry.samples.value.isNotEmpty())
        MlTelemetry.clear()
        assertEquals(0, MlTelemetry.samples.value.size)
    }
}
