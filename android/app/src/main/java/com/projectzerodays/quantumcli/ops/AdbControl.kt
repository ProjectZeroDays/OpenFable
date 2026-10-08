package com.projectzerodays.quantumcli.ops

import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.io.IOException

/**
 * ADB device control over wireless TCP (port 5555) using dadb 1.2.9 — a
 * pure-Kotlin ADB client that speaks the ADB protocol directly (no adb
 * binary, no ADB server process).
 *
 * Scope (honest):
 *  - Wireless ADB (TCP) is fully supported: connect, shell, input injection,
 *    screencap pull, pm list/install/uninstall.
 *  - USB (OTG host) mode is NOT supported on Android by dadb's JVM transport
 *    — [USB_NOTE] is surfaced verbatim in the UI instead of a fake control.
 *  - The target must have "Wireless debugging" enabled and must accept this
 *    device's ADB RSA key on first connect (authorization prompt on target).
 *  - The RSA key lives in the app's files dir (Android has no ~/.android).
 *  - dadb 1.2.9 API: install/uninstall/pull return Unit and throw IOException
 *    on failure; shell() returns (output, errorOutput, exitCode).
 *
 * Pure helpers ([parseTarget], [parsePackages], [getpropValue], the input
 * command builders) are unit-tested without a device; live connect flows
 * need a real target on the LAN.
 */
object AdbControl {

    const val DEFAULT_PORT = 5555

    /** Honest USB statement — shown in the UI, never a fake toggle. */
    const val USB_NOTE =
        "USB (OTG) control is not supported by this ADB client on Android — " +
            "dadb's USB transport targets desktop JVMs. Use wireless ADB: enable " +
            "'Wireless debugging' on the target (same Wi-Fi), then scan + connect."

    data class ShellResult(val exitCode: Int, val output: String) {
        val ok: Boolean get() = exitCode == 0
    }

    data class DeviceInfo(
        val model: String,
        val androidVersion: String,
        val sdk: String,
        val serial: String,
    )

    // ------------------------------------------------------ pure helpers

    /** "1.2.3.4" / "1.2.3.4:5555" / "host" -> (host, port); null if blank/invalid. */
    fun parseTarget(raw: String, defaultPort: Int = DEFAULT_PORT): Pair<String, Int>? {
        val t = raw.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        val colons = t.count { it == ':' }
        if (colons > 1) return null // IPv6 targets unsupported by wireless ADB here
        val colon = t.lastIndexOf(':')
        if (colon >= 0) {
            if (colon == 0) return null // ":5555" — empty host
            val host = t.substring(0, colon)
            val port = t.substring(colon + 1).toIntOrNull()
            if (host.isNotEmpty() && port != null && port in 1..65535) return host to port
            return null
        }
        return t to defaultPort
    }

    /** "pm list packages" output -> sorted package names (prefix stripped). */
    fun parsePackages(pmOutput: String): List<String> =
        pmOutput.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .distinct()
            .sorted()
            .toList()

    /** getprop output -> value for [key] ("[ro.build.x]: [14]"). */
    fun getpropValue(getpropOutput: String, key: String): String? {
        val needle = "[$key]: ["
        val line = getpropOutput.lineSequence().firstOrNull { needle in it } ?: return null
        val start = line.indexOf(needle) + needle.length
        val end = line.lastIndexOf(']')
        return if (end > start) line.substring(start, end) else null
    }

    /** `input text` argument: spaces -> %s, shell/quote chars stripped. */
    fun textArg(s: String): String =
        s.replace(Regex("[\\\\\"'\r\n]"), "")
            .replace(" ", "%s")
            .take(512)

    fun tapCmd(x: Int, y: Int): String =
        "input tap ${x.coerceIn(0, 99999)} ${y.coerceIn(0, 99999)}"

    fun swipeCmd(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300): String =
        "input swipe ${x1.coerceIn(0, 99999)} ${y1.coerceIn(0, 99999)} " +
            "${x2.coerceIn(0, 99999)} ${y2.coerceIn(0, 99999)} " +
            "${durationMs.coerceIn(50, 10_000)}"

    fun textCmd(s: String): String = "input text ${textArg(s)}"

    fun keyCmd(code: Int): String = "input keyevent ${code.coerceIn(0, 9999)}"

    // ------------------------------------------------------- connection

    @Volatile
    private var conn: Dadb? = null

    @Volatile
    var endpoint: String? = null
        private set

    fun isConnected(): Boolean = conn != null

    /** RSA keypair in [keyDir] — generated once (Android has no ~/.android). */
    fun keyPair(keyDir: File): AdbKeyPair {
        val priv = File(keyDir, "adbkey")
        val pub = File(keyDir, "adbkey.pub")
        if (!priv.isFile) {
            AdbKeyPair.generate(priv, pub)
        }
        return AdbKeyPair.read(priv, pub)
    }

    /**
     * Connect to host:port over wireless ADB. Returns an honest status string:
     * "connected ..." on success, or the failure reason (e.g. the target has
     * not authorized this key) otherwise. Never throws.
     */
    fun connect(host: String, port: Int, keyDir: File): String {
        disconnect()
        return try {
            val d = Dadb.create(
                host, port,
                keyPair = keyPair(keyDir),
                connectTimeout = 5_000,
                socketTimeout = 15_000,
            )
            // Prove the session actually works before declaring success.
            val probe = d.shell("echo qcli-ok")
            if (!probe.output.contains("qcli-ok")) {
                try {
                    d.close()
                } catch (_: IOException) {
                }
                return "connected but shell probe failed — target may be " +
                    "unauthorized (accept the RSA prompt on the target)"
            }
            conn = d
            endpoint = "$host:$port"
            "connected $host:$port"
        } catch (e: Exception) {
            "connect failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun disconnect() {
        try {
            conn?.close()
        } catch (_: Exception) {
        }
        conn = null
        endpoint = null
    }

    /**
     * Run a shell command; never throws — errors land in output, exit -1.
     * stdout and stderr are combined (dadb tracks them separately).
     */
    fun shell(cmd: String): ShellResult {
        val d = conn ?: return ShellResult(-1, "not connected")
        return try {
            val r = d.shell(cmd)
            val all = listOf(r.output, r.errorOutput)
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .trimEnd()
            ShellResult(r.exitCode, all)
        } catch (e: Exception) {
            ShellResult(-1, "shell failed: ${e.message}")
        }
    }

    // ------------------------------------------------------- operations

    fun deviceInfo(): DeviceInfo? {
        val out = shell(
            "getprop ro.product.model; echo ---; getprop ro.build.version.release" +
                "; echo ---; getprop ro.build.version.sdk; echo ---; getprop ro.serialno",
        )
        if (out.exitCode == -1) return null // not connected / shell error
        val parts = out.output.split("---")
        if (parts.size < 4) return null
        return DeviceInfo(
            model = parts[0].trim().ifEmpty { "unknown" },
            androidVersion = parts[1].trim().ifEmpty { "?" },
            sdk = parts[2].trim().ifEmpty { "?" },
            serial = parts[3].trim().ifEmpty { "?" },
        )
    }

    /**
     * Screenshot: remote screencap -> pull to [dest]. Returns true on success
     * (dest holds a PNG) — false with no fake image otherwise. The remote
     * artifact is always removed.
     */
    fun screenshot(dest: File): Boolean {
        val d = conn ?: return false
        val remote = "/data/local/tmp/qcli_screen.png"
        try {
            val cap = shell("screencap -p $remote")
            if (!cap.ok) return false
            d.pull(dest, remote)
            return dest.isFile && dest.length() > 0
        } catch (e: Exception) {
            return false
        } finally {
            shell("rm -f $remote") // leave no artifact on the target
        }
    }

    fun listPackages(thirdPartyOnly: Boolean = true): List<String> {
        val flag = if (thirdPartyOnly) " -3" else ""
        return parsePackages(shell("pm list packages$flag").output)
    }

    /** dadb 1.2.9: install returns Unit and throws IOException on failure. */
    fun installApk(file: File): String {
        val d = conn ?: return "not connected"
        return try {
            d.install(file)
            "installed ${file.name}"
        } catch (e: Exception) {
            "install failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun uninstall(pkg: String): String {
        val d = conn ?: return "not connected"
        return try {
            d.uninstall(pkg)
            "uninstalled $pkg"
        } catch (e: Exception) {
            "uninstall failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
