package com.projectzerodays.quantumcli.ops

import com.projectzerodays.quantumcli.c2.C2State
import org.json.JSONObject

/**
 * MAC / OUI intelligence — IEEE OUI prefix matching against an embedded
 * table of common vendors, with the macvendors.com API as a live fallback
 * for unknown prefixes (free, keyless, ~1000 req/day).
 */
object Oui {

    private val PREFIXES: Map<String, String> = mapOf(
        "00:00:0C" to "Cisco",
        "00:00:1B" to "Novell",
        "00:01:6C" to "Foxconn",
        "00:03:93" to "Apple",
        "00:05:02" to "Apple",
        "00:0A:95" to "SanDisk",
        "00:0D:93" to "Apple",
        "00:0E:35" to "Siemens",
        "00:11:20" to "Cisco",
        "00:11:24" to "Samsung",
        "00:12:17" to "Samsung",
        "00:14:22" to "Dell",
        "00:14:51" to "Cisco-Linksys",
        "00:15:AF" to "Willcom",
        "00:16:B4" to "Sonicwall",
        "00:17:F2" to "Apple",
        "00:18:39" to "Cisco-Linksys",
        "00:1A:11" to "Google",
        "00:1B:63" to "Apple",
        "00:1C:B3" to "Apple",
        "00:1D:4F" to "Hewlett-Packard",
        "00:1E:52" to "AzureWave",
        "00:1F:5B" to "Apple",
        "00:21:E9" to "Cisco",
        "00:22:41" to "Cisco-Linksys",
        "00:23:12" to "Apple",
        "00:23:CD" to "Dell",
        "00:24:36" to "Apple",
        "00:25:00" to "Apple",
        "00:25:BC" to "Apple",
        "00:26:08" to "Apple",
        "00:26:BB" to "Apple",
        "00:40:96" to "Cisco",
        "00:50:56" to "VMware",
        "00:50:F2" to "Philips",
        "00:55:DA" to "Hewlett-Packard",
        "00:60:2F" to "Cisco",
        "00:A0:40" to "Cabletron",
        "00:C0:49" to "Motorola",
        "00:E0:4C" to "Realtek",
        "08:00:27" to "Oracle VirtualBox",
        "00:1A:A0" to "Google",
        "02:00:00" to "Locally administered (randomized)",
        "3C:07:54" to "Hikvision",
        "3C:5A:B4" to "Google",
        "40:B0:FA" to "Hikvision",
        "44:47:CC" to "Cisco",
        "48:8A:DF" to "Cisco",
        "50:C7:BF" to "Ubiquiti",
        "54:9F:13" to "Ubiquiti",
        "60:D0:A9" to "Dahua",
        "64:16:66" to "Oki",
        "68:5D:43" to "Dahua",
        "70:56:81" to "Apple",
        "78:A3:51" to "Samsung",
        "84:16:F9" to "TP-Link",
        "8C:1D:96" to "Apple",
        "90:B2:1F" to "Ubiquiti",
        "98:DA:C4" to "Dahua",
        "A4:2B:B0" to "Apple",
        "AC:DE:48" to "Private (Apple)",
        "B8:27:EB" to "Raspberry Pi",
        "C8:3A:35" to "Cisco",
        "D8:97:BA" to "Hikvision",
        "DC:A6:32" to "Raspberry Pi",
        "E4:5F:01" to "Hikvision",
        "F0:9F:C2" to "Ubiquiti",
        "F4:F5:D8" to "Dahua",
        "F8:1D:78" to "Apple",
    )

    /** Normalize any MAC spelling (aabbcc-ddeeff, aabb.ccddeeff, mixed separators). */
    fun normalize(mac: String): String {
        val hex = mac.filter { it.isLetterOrDigit() }.lowercase()
        return if (hex.length == 12) hex.chunked(2).joinToString(":") else mac.trim().lowercase()
    }

    fun isLocallyAdministered(mac: String): Boolean {
        val n = normalize(mac)
        if (n.length != 17) return false
        val secondNibble = n[1]
        return secondNibble == '2' || secondNibble == '6' || secondNibble == 'a' || secondNibble == 'e'
    }

    /** Offline prefix lookup; null when the OUI is not in the embedded table. */
    fun lookupLocal(mac: String): String? = PREFIXES[normalize(mac).take(8)]

    /** Lookup with live fallback (macvendors.com). Blocks — call from IO. */
    fun lookup(mac: String): String {
        lookupLocal(mac)?.let { return it }
        val n = normalize(mac)
        if (n.length != 17) return "invalid MAC"
        val r = Net.httpGet(
            "https://api.macvendors.com/${n.replace(":", "%3A")}",
            headers = mapOf("User-Agent" to "QuantumCLI"),
            timeoutMs = 8000,
        )
        val body = r.text(120).trim()
        return when {
            r.ok && body.isNotBlank() -> body
            body.contains("rate", ignoreCase = true) -> "unknown (API rate-limited)"
            else -> "unknown"
        }
    }

    /** Broadcast flag set of a WiFi ScanResult capability string, decoded for display. */
    fun describeCaps(caps: String): String {
        val f = listOfNotNull(
            "WPA3" to caps.contains("SAE"), "WPA2" to caps.contains("PSK"),
            "WPA" to (caps.contains("WPA") && !caps.contains("WPA2")),
            "WEP" to caps.contains("WEP"),
            "ESS" to caps.contains("ESS"),
        ).filter { it.second }.joinToString(" ") { it.first }
        return f.ifEmpty { "OPEN" }
    }

    fun auditLookup(mac: String) {
        C2State.audit("OUI", "$mac -> ${lookupLocal(mac) ?: "unknown (prefix not embedded)"}")
    }
}
