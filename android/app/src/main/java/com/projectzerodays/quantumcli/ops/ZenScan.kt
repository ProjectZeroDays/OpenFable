package com.projectzerodays.quantumcli.ops

/**
 * Zenmap-style manual scan controls — port-spec parsing, timing templates
 * and a TCP-connect sweep over the existing Net engine. Pure-JVM logic
 * (parsePorts / timingProfile) is unit-tested; [scan] wraps
 * Net.tcpProbe with the chosen concurrency and timeout.
 */
object ZenScan {

    data class HostResult(val ip: String, val openPorts: List<Int>)

    /**
     * Parse a port spec — "22,80,443" and "1000-2000" segments, mixed and
     * in any order. Sorted, de-duplicated, capped at [cap] ports to keep
     * scans bounded. Invalid segments are skipped, never thrown.
     */
    fun parsePorts(spec: String, cap: Int = 4096): List<Int> {
        val out = sortedSetOf<Int>()
        for (seg in spec.split(",", ";", " ")) {
            val s = seg.trim()
            if (s.isEmpty()) continue
            if (s.contains("-")) {
                val parts = s.split("-", limit = 2)
                val lo = parts[0].trim().toIntOrNull()
                val hi = parts.getOrNull(1)?.trim()?.toIntOrNull()
                if (lo != null && hi != null && lo in 1..65535 && hi in 1..65535 && lo <= hi) {
                    var p = lo
                    while (p <= hi && out.size < cap) {
                        out.add(p)
                        p++
                    }
                }
            } else {
                val p = s.toIntOrNull()
                if (p != null && p in 1..65535 && out.size < cap) out.add(p)
            }
        }
        return out.toList()
    }

    /**
     * nmap timing templates T0..T5 mapped to (concurrency, probe timeout).
     * T0 paranoid → single-threaded and slow; T5 insane → 256 workers with
     * an aggressive 250ms timeout.
     */
    fun timingProfile(t: Int): Pair<Int, Int> = when (t.coerceIn(0, 5)) {
        0 -> 1 to 1500
        1 -> 8 to 1000
        2 -> 32 to 700
        3 -> 64 to 500
        4 -> 128 to 350
        else -> 256 to 250
    }

    fun timingLabel(t: Int): String = when (t.coerceIn(0, 5)) {
        0 -> "T0 paranoid"
        1 -> "T1 sneaky"
        2 -> "T2 polite"
        3 -> "T3 normal"
        4 -> "T4 aggressive"
        else -> "T5 insane"
    }

    /**
     * TCP-connect sweep — hosts × ports with the timing profile's
     * concurrency/timeout. Returns hosts with at least one open port,
     * sorted by IP. Never throws; unreachable hosts are simply absent.
     */
    fun scan(hosts: List<String>, ports: List<Int>, timing: Int): List<HostResult> {
        if (hosts.isEmpty() || ports.isEmpty()) return emptyList()
        val (workers, timeout) = timingProfile(timing)
        return Net.parMap(hosts, workers) { ip ->
            val open = ports.filter { Net.tcpProbe(ip, it, timeout) }
            if (open.isNotEmpty()) HostResult(ip, open) else null
        }.sortedBy { it.ip }
    }
}
