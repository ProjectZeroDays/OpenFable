package com.projectzerodays.quantumcli.ops

import com.projectzerodays.quantumcli.c2.CameraHit
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** ReconModule parity — CIDR scan, target fingerprinting, pre-fire Validator. */
object Recon {

    val COMMON: List<Int> = listOf(21, 22, 23, 80, 443, 445, 554, 1433, 3389, 7001, 8000, 8080, 8443, 8899)

    data class HostResult(val ip: String, val ports: List<Int>)

    /** CIDR sweep over COMMON ports (32 workers, 400 ms probe parity). */
    fun scan(cidr: String, onHost: (HostResult) -> Unit = {}): List<HostResult> {
        val hosts = Net.cidrHosts(cidr)
        if (hosts.isEmpty()) return emptyList()
        val found = ConcurrentHashMap<String, MutableList<Int>>()
        Net.parMap(hosts, 32) { ip ->
            val open = COMMON.filter { Net.tcpProbe(ip, it, 400) }
            if (open.isNotEmpty()) {
                found[ip] = open.toMutableList()
            }
            null
        }
        val out = found.entries
            .map { HostResult(it.key, it.value.toList()) }
            .sortedBy { it.ip }
        out.forEach(onHost)
        return out
    }

    /** fingerprint parity: ports + banners + camera hints. */
    fun fingerprint(ip: String): JSONObject {
        val fp = JSONObject()
            .put("ip", ip)
            .put("ports", org.json.JSONArray())
            .put("banners", JSONObject())
            .put("smb", JSONObject())
            .put("camera", false)
            .put("vendor", JSONObject.NULL)
            .put("ms17_010", JSONObject.NULL)

        val ports = COMMON.filter { Net.tcpProbe(ip, it, 500) }
        fp.put("ports", org.json.JSONArray(ports))

        val banners = JSONObject()
        for (port in ports) {
            if (port in listOf(80, 443, 7001, 8000, 8080, 8443)) {
                val scheme = if (port == 443 || port == 8443) "https" else "http"
                val r = Net.httpGet("$scheme://$ip:$port/", timeoutMs = 3000)
                if (r.error == null) {
                    val hay = r.text(2000) + " " +
                        (r.headers["server"] ?: "") + " " +
                        (r.headers["www-authenticate"] ?: "")
                    banners.put(port.toString(), hay.take(200))
                }
            }
        }
        fp.put("banners", banners)
        if (ports.contains(445) || ports.contains(139)) {
            fp.put("smb", JSONObject().put("open", true))
        }
        var vendor: String? = null
        for (key in banners.keys()) {
            val v = CamWar.vendorFromStr(banners.optString(key))
            if (v != "unknown") {
                vendor = v
                fp.put("vendor", v)
                fp.put("camera", true)
                break
            }
        }
        if (ports.contains(554)) {
            fp.put("camera", true)
        }
        return fp
    }

    /** Pre-fire risk scoring parity (cameras + appliances win). */
    fun validate(ip: String, fp: JSONObject): JSONObject {
        val camPorts = setOf(554, 37777, 37778, 8000, 8001, 8081, 8899, 34567, 34571, 34599)
        val applianceHints = listOf(
            "citrix", "pan-os", "panos", "vmware", "vcenter",
            "weblogic", "confluence", "cisco ios", "moveit"
        )
        var s = 10
        val portsArr = fp.optJSONArray("ports")
        val ports = HashSet<Int>()
        if (portsArr != null) {
            for (i in 0 until portsArr.length()) ports.add(portsArr.optInt(i))
        }
        if (ports.intersect(camPorts).isNotEmpty() || fp.optBoolean("camera")) s += 50
        val vendor = fp.optString("vendor")
        if (!fp.isNull("vendor") && vendor.isNotBlank()) s += 40
        val hay = fp.toString().lowercase()
        if (applianceHints.any { hay.contains(it) }) s += 40
        if (fp.has("ms17_010") && !fp.isNull("ms17_010") && fp.optBoolean("ms17_010", false)) s += 30
        s += minOf(ports.size, 10)
        val decision = if (s >= 60) "high_value" else "low_value"
        return JSONObject().put("score", s).put("decision", decision).put("ip", ip)
    }

    fun camHint(ip: String, ports: List<Int>): CameraHit? {
        val vendor = "generic"
        if (ports.isEmpty()) return null
        return CameraHit(ip, vendor, ports, "recon")
    }
}
