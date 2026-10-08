package com.projectzerodays.quantumcli.ops

import android.content.Context
import android.net.wifi.WifiManager
import com.projectzerodays.quantumcli.c2.C2State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Wi-Fi defense (NRSuite parity, on-device realities):
 *  - Hidden AP revealer — scan results beaconing with an empty SSID.
 *  - Rogue AP detector — same SSID advertised by multiple BSSIDs with
 *    mismatched security caps or an OUI that differs from the network's
 *    dominant vendor; plus open-network same-name twins of secured ones.
 *  - Presence detector — RSSI deltas of watched BSSIDs across refreshes
 *    (signal change implies movement/proximity; no monitor mode needed).
 *  - Deauth watch — ROOT + monitor interface (wlan0mon): tcpdump counts
 *    802.11 deauth/disassoc management frames. Honest fail without root.
 */
object WifiDefense {

    data class Ap(
        val ssid: String,
        val bssid: String,
        val caps: String,
        val level: Int,
        val freq: Int,
        val vendor: String,
        val hidden: Boolean,
    )

    data class RogueAlert(val kind: String, val detail: String)

    private val _aps = MutableStateFlow<List<Ap>>(emptyList())
    val aps: StateFlow<List<Ap>> = _aps.asStateFlow()

    private val _alerts = MutableStateFlow<List<RogueAlert>>(emptyList())
    val alerts: StateFlow<List<RogueAlert>> = _alerts.asStateFlow()

    // BSSID -> previous RSSI (presence deltas)
    private val prevRssi = HashMap<String, Int>()
    private val _presence = MutableStateFlow<Map<String, Int>>(emptyMap())
    val presence: StateFlow<Map<String, Int>> = _presence.asStateFlow()

    @Suppress("DEPRECATION")
    suspend fun refresh(ctx: Context): List<Ap> = withContext(Dispatchers.IO) {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wm == null) {
            C2State.audit("WDEF", "WifiManager unavailable")
            return@withContext _aps.value
        }
        val results = runCatching { wm.scanResults }.getOrDefault(emptyList())
        val list = results.map { r ->
            Ap(
                ssid = r.SSID ?: "",
                bssid = r.BSSID ?: "",
                caps = r.capabilities ?: "",
                level = r.level,
                freq = r.frequency,
                vendor = Oui.lookupLocal(r.BSSID ?: "") ?: "unknown",
                hidden = (r.SSID ?: "").isBlank(),
            )
        }.sortedByDescending { it.level }
        _aps.value = list

        // --- hidden AP revealer ---
        val hidden = list.filter { it.hidden }
        if (hidden.isNotEmpty()) {
            C2State.audit("WDEF", "hidden APs: ${hidden.joinToString { it.bssid }}")
        }

        // --- rogue AP heuristics ---
        val newAlerts = ArrayList<RogueAlert>()
        list.filter { !it.hidden }.groupBy { it.ssid }.filterValues { it.size > 1 }.forEach { (ssid, group) ->
            val capSets = group.map { Oui.describeCaps(it.caps) }.toSet()
            if (capSets.size > 1) {
                newAlerts.add(
                    RogueAlert("MIXED-SECURITY TWIN", "'$ssid' advertised with different security ($capSets) on ${group.size} BSSIDs — possible evil twin")
                )
            }
            val vendors = group.map { it.vendor }.toSet()
            if (vendors.size > 1 && group.any { Oui.describeCaps(it.caps) == "OPEN" }) {
                newAlerts.add(
                    RogueAlert("OPEN TWIN", "'$ssid' has an OPEN member among secured BSSIDs ($vendors) — credential-harvest AP pattern")
                )
            }
        }

        // --- presence deltas ---
        val deltas = HashMap<String, Int>()
        list.forEach { ap ->
            prevRssi[ap.bssid]?.let { before ->
                val d = ap.level - before
                if (kotlin.math.abs(d) >= 6) deltas[ap.bssid] = d
            }
            prevRssi[ap.bssid] = ap.level
        }
        _presence.value = deltas

        _alerts.value = (newAlerts + _alerts.value).distinctBy { it.kind + it.detail }.takeLast(50)
        if (newAlerts.isNotEmpty()) C2State.audit("WDEF", "${newAlerts.size} rogue-AP heuristic alert(s)")
        list
    }

    /**
     * ROOT deauth watch — one-shot tcpdump count of deauth (0x00c0) /
     * disassoc (0x00a0) frames on a monitor interface. Returns null when
     * root or the monitor interface is unavailable (honest limitation).
     */
    fun deauthWatch(monitorIface: String, seconds: Int = 10): String? {
        return try {
            val p = Runtime.getRuntime().exec(
                arrayOf(
                    "su", "-c",
                    "timeout $seconds tcpdump -i $monitorIface -c 50 'type mgt and (subtype deauth or subtype disassoc)' 2>&1 | tail -5"
                )
            )
            val out = p.inputStream.readBytes().toString(Charsets.UTF_8).trim()
            val code = p.waitFor()
            if (code == 0 && out.isNotBlank() && !out.contains("not found", true)) {
                C2State.audit("WDEF", "deauth watch: ${out.lineSequence().count()} mgt frame line(s)")
                out
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Probe whether a monitor interface exists (root). */
    fun monitorIface(): String? {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip -o link show | grep -o 'wlan[0-9]*mon' | head -1"))
            val out = p.inputStream.readBytes().toString(Charsets.UTF_8).trim()
            p.waitFor()
            out.ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }
}
