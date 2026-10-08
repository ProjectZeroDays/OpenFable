package com.projectzerodays.quantumcli.ops

import com.projectzerodays.quantumcli.c2.C2State
import org.json.JSONArray
import org.json.JSONObject

/**
 * OSINT engine — real lookups over public APIs: RDAP/whois, DNS records via
 * DoH, GitHub code-search, HIBP breach check, IP geo. Every result lands in
 * the audit log tagged OSINT. Authorized engagements only.
 */
object Osint {

    data class Finding(val kind: String, val value: String, val detail: String)

    /** RDAP (modern whois) — registration data for a domain. */
    fun rdap(domain: String): JSONObject {
        val tld = domain.substringAfterLast('.')
        val boot = Net.httpGet("https://rdap.org/domain/$domain", timeoutMs = 10_000)
        if (boot.ok) return JSONObject(boot.text(20_000))
        return JSONObject().put("error", "rdap lookup failed (HTTP ${boot.status})")
    }

    /** DNS records over DoH (json) — A/AAAA/MX/TXT/NS in one pass. */
    fun dnsRecords(host: String): List<Finding> {
        val out = ArrayList<Finding>()
        for (type in listOf("A", "AAAA", "MX", "TXT", "NS")) {
            val r = Net.httpGet(
                "https://dns.google/resolve?name=${android.net.Uri.encode(host)}&type=$type",
                timeoutMs = 8000,
            )
            if (!r.ok) continue
            val answers = runCatching { JSONObject(r.text()).optJSONArray("answers") }.getOrNull() ?: continue
            for (i in 0 until answers.length()) {
                val a = answers.optJSONObject(i) ?: continue
                val data = a.opt("data")?.toString() ?: continue
                out.add(Finding("DNS $type", a.optString("name"), data))
            }
        }
        return out
    }

    /** IP geolocation + org via ip-api.com (free, no key). */
    fun ipInfo(ip: String): JSONObject {
        val r = Net.httpGet("http://ip-api.com/json/$ip?fields=status,country,regionName,city,isp,org,as,reverse,proxy,hosting", timeoutMs = 8000)
        return if (r.ok) JSONObject(r.text()) else JSONObject().put("error", "geo lookup failed")
    }

    /** GitHub code search — find leaked keys/configs for a domain (api unauthenticated: 10 req/min). */
    fun githubSearch(query: String, limit: Int = 10): List<Finding> {
        val r = Net.httpGet(
            "https://api.github.com/search/code?q=${android.net.Uri.encode(query)}",
            headers = mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "QuantumCLI"),
            timeoutMs = 10_000,
        )
        if (!r.ok) return listOf(Finding("GH", "error", "HTTP ${r.status} (auth may be required)"))
        val items = runCatching { JSONObject(r.text()).optJSONArray("items") }.getOrNull() ?: return emptyList()
        val out = ArrayList<Finding>()
        for (i in 0 until minOf(items.length(), limit)) {
            val it = items.optJSONObject(i) ?: continue
            val repo = it.optJSONObject("repository")?.optString("full_name") ?: "?"
            out.add(Finding("GH-CODE", repo, it.optString("path")))
        }
        return out
    }

    /** HIBP breach check — has this email appeared in a known breach (unauthenticated adds a suffix check). */
    fun breach(email: String): JSONObject {
        val r = Net.httpGet(
            "https://haveibeenpwned.com/api/v3/breachedaccount/${android.net.Uri.encode(email)}?truncateResponse=true",
            headers = mapOf("User-Agent" to "QuantumCLI"),
            timeoutMs = 8000,
        )
        return when {
            r.status == 404 -> JSONObject().put("breaches", JSONArray()).put("result", "clean")
            r.ok -> JSONObject(r.text())
            else -> JSONObject().put("error", "HIBP HTTP ${r.status} (API key may be required)")
        }
    }

    /** Subdomain discovery via crt.sh certificate transparency. */
    fun subdomains(domain: String, limit: Int = 50): List<Finding> {
        val r = Net.httpGet("https://crt.sh/?q=%25.${android.net.Uri.encode(domain)}&output=json", timeoutMs = 15_000)
        if (!r.ok) return listOf(Finding("SUB", "error", "crt.sh HTTP ${r.status}"))
        val arr = runCatching { JSONArray(r.text(2_000_000)) }.getOrNull() ?: return emptyList()
        val names = sortedSetOf<String>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            e.optString("name_value").split("\n").forEach { n -> if (n.isNotBlank()) names.add(n.trim()) }
        }
        return names.take(limit).map { Finding("SUBDOMAIN", it, "") }
    }

    /** Full sweep — every module against one target; results audited. */
    fun sweep(target: String): List<Finding> {
        val out = ArrayList<Finding>()
        val isIp = Net.validIp(target)
        if (isIp) {
            out.add(Finding("IP", target, ipInfo(target).toString()))
        } else {
            out.addAll(dnsRecords(target))
            out.addAll(subdomains(target))
            val w = rdap(target)
            if (!w.has("error")) {
                out.add(Finding("RDAP", target, "registrar=${w.optJSONObject("events")?.toString()?.take(200) ?: "?"}"))
            }
        }
        C2State.audit("OSINT", "sweep $target: ${out.size} finding(s)")
        return out
    }
}
