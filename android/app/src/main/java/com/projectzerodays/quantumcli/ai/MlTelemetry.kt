package com.projectzerodays.quantumcli.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Session ML telemetry — one ring buffer per app run (last 200 samples),
 * fed by [AiClient] around every real inference attempt (local MediaPipe
 * generateResponse and online /chat/completions calls; deterministic
 * replies are not ML and are never recorded).
 *
 * Pure Kotlin (no Android APIs) so all the math is unit-testable on the JVM.
 * Gated by the `mlTelemetry` setting — when off, [record] is a no-op.
 */
object MlTelemetry {

    data class Sample(
        val ts: Long,
        val mode: String,       // "local" | "online"
        val latencyMs: Long,
        val outChars: Int,
        val ok: Boolean,
    )

    data class Stats(
        val count: Int,
        val okCount: Int,
        val failCount: Int,
        val avgLatencyMs: Long,
        val maxLatencyMs: Long,
        val p95LatencyMs: Long,
        val totalOutChars: Int,
        /** aggregate output throughput: total chars / total latency seconds */
        val charsPerSec: Double,
        /** rough token estimate (chars / 4) — honest approximation, not a tokenizer */
        val estTokens: Int,
    )

    private const val MAX_SAMPLES = 200

    private val _samples = MutableStateFlow<List<Sample>>(emptyList())
    val samples: StateFlow<List<Sample>> = _samples.asStateFlow()

    fun record(
        mode: String,
        latencyMs: Long,
        outChars: Int,
        ok: Boolean,
        now: Long = System.currentTimeMillis(),
    ) {
        val next = _samples.value + Sample(now, mode, latencyMs.coerceAtLeast(0), outChars, ok)
        _samples.value = if (next.size > MAX_SAMPLES) next.takeLast(MAX_SAMPLES) else next
    }

    fun stats(samples: List<Sample> = _samples.value): Stats {
        if (samples.isEmpty()) {
            return Stats(0, 0, 0, 0, 0, 0, 0, 0.0, 0)
        }
        val lat = samples.map { it.latencyMs }
        val sorted = lat.sorted()
        val p95Idx = (Math.ceil(0.95 * sorted.size).toInt() - 1).coerceIn(0, sorted.size - 1)
        val okCount = samples.count { it.ok }
        val totalChars = samples.sumOf { it.outChars }
        val totalMs = lat.sum().coerceAtLeast(1)
        return Stats(
            count = samples.size,
            okCount = okCount,
            failCount = samples.size - okCount,
            avgLatencyMs = lat.average().toLong(),
            maxLatencyMs = sorted.last(),
            p95LatencyMs = sorted[p95Idx],
            totalOutChars = totalChars,
            charsPerSec = totalChars / (totalMs / 1000.0),
            estTokens = totalChars / 4,
        )
    }

    fun clear() {
        _samples.value = emptyList()
    }
}
