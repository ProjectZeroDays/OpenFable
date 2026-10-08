package com.projectzerodays.quantumcli.c2

import com.projectzerodays.quantumcli.BuildConfig
import com.projectzerodays.quantumcli.ops.Net
import org.json.JSONObject

/**
 * GitHub release check — compares the installed APK version against the
 * latest published release and classifies the bump as a safe (patch/minor)
 * or breaking (major) upgrade so the operator knows what to expect.
 */
object UpdateChecker {

    const val RELEASES_API =
        "https://api.github.com/repos/projectzerodays/Quantum-CLI/releases/latest"

    data class Result(
        val current: String,
        val latest: String,
        val updateAvailable: Boolean,
        val safe: String,          // "safe" | "breaking" | "unknown"
        val notes: String?,
        val url: String?,
        val error: String? = null,
    ) {
        fun headline(): String = when {
            error != null -> "Update check failed: $error"
            updateAvailable -> "Update available: v$latest (installed v$current) — $safe"
            else -> "Up to date (v$current)."
        }
    }

    fun check(current: String = BuildConfig.VERSION_NAME): Result {
        val r = Net.httpGet(
            RELEASES_API,
            mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to "QUANTUM-CLI-Android/$current"
            ),
            10_000
        )
        if (r.error != null || !r.ok) {
            return Result(current, "", false, "unknown", null, null,
                r.error ?: "HTTP ${r.status}")
        }
        return try {
            val o = JSONObject(r.text())
            val tag = o.optString("tag_name").removePrefix("v").removePrefix("V")
            val body = o.optString("body").ifBlank { null }
            val url = o.optString("html_url").ifBlank { null }
            val cmp = semverCompare(tag, current)
            Result(
                current = current,
                latest = tag,
                updateAvailable = cmp > 0,
                safe = when {
                    cmp <= 0 -> "unknown"
                    major(tag) > major(current) -> "breaking release — review the notes before upgrading"
                    else -> "safe to upgrade (patch/minor bump)"
                },
                notes = body?.take(600),
                url = url,
            )
        } catch (e: Exception) {
            Result(current, "", false, "unknown", null, null, e.message)
        }
    }

    fun semverCompare(a: String, b: String): Int {
        val pa = a.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return if (x > y) 1 else -1
        }
        return 0
    }

    private fun major(v: String): Int =
        v.split(".").firstOrNull()?.filter { it.isDigit() }?.toIntOrNull() ?: 0
}
