package com.projectzerodays.quantumcli.ops

import org.json.JSONObject
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

/**
 * Network dashboard engine — device interfaces (IPv4/IPv6/MAC), VPN & proxy
 * detection, DNS resolution and public-IP geolocation for connection mapping.
 *
 * Geolocation is fetched from ip-api.com (free tier, http only) and only for
 * PUBLIC addresses — loopback, private, link-local, CGNAT and reserved ranges
 * are never sent to any external service and are labeled locally instead.
 */
object NetDiag {

    data class IfaceInfo(
        val name: String,
        val ipv4: String?,
        val ipv6: String?,
        val mac: String?,
        val up: Boolean,
        val loopback: Boolean,
        val vpn: Boolean,
    )

    data class GeoInfo(
        val ip: String,
        val country: String?,
        val city: String?,
        val isp: String?,
        val as_: String?,
    ) {
        fun line(): String = listOfNotNull(country, city, isp).joinToString(" | ")
    }

    data class Snapshot(
        val ifaces: List<IfaceInfo>,
        val gateway: String?,
        val publicIp: String?,
        val geo: GeoInfo?,
        val vpnActive: Boolean,
        val proxy: String?,
    )

    // ------------------------------------------------------------ interfaces
    fun interfaces(): List<IfaceInfo> {
        return try {
            val out = ArrayList<IfaceInfo>()
            for (ni in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                var v4: String? = null
                var v6: String? = null
                for (a in Collections.list(ni.inetAddresses)) {
                    if (a is java.net.Inet4Address && !a.isLoopbackAddress) {
                        v4 = a.hostAddress ?: v4
                    } else if (a is java.net.Inet6Address) {
                        v6 = a.hostAddress?.split("%")?.firstOrNull() ?: v6
                    }
                }
                val macBytes = ni.hardwareAddress
                val mac = if (macBytes != null && macBytes.isNotEmpty()) {
                    macBytes.joinToString(":") { "%02x".format(it) }
                } else null
                val name = ni.name ?: "?"
                out.add(
                    IfaceInfo(
                        name = name,
                        ipv4 = v4,
                        ipv6 = v6,
                        mac = mac,
                        up = ni.isUp,
                        loopback = ni.isLoopback,
                        vpn = isVpnIface(name),
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun isVpnIface(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith("tun") || n.startsWith("tap") ||
            n.startsWith("ppp") || n.contains("wireguard") ||
            n.startsWith("ipsec") || n.startsWith("wg")
    }

    /** Default gateway from /proc/net/route (dest 00000000). */
    fun gateway(): String? = try {
        java.io.File("/proc/net/route").bufferedReader().useLines { lines ->
            for (line in lines.drop(1)) {
                val cols = line.trim().split(Regex("\\s+"))
                if (cols.size >= 3 && cols[1] == "00000000") {
                    val hex = cols[2]
                    if (hex.length == 8) {
                        val ip = (3 downTo 0).joinToString(".") {
                            hex.substring(it * 2, it * 2 + 2).toInt(16).toString()
                        }
                        if (ip != "0.0.0.0") return@useLines ip
                    }
                }
            }
            null
        }
    } catch (e: Exception) {
        null
    }

    fun proxyInfo(): String? {
        val host = System.getProperty("http.proxyHost")
            ?: System.getProperty("https.proxyHost")
            ?: return null
        val port = System.getProperty("http.proxyPort")
            ?: System.getProperty("https.proxyPort") ?: ""
        return if (port.isBlank()) host else "$host:$port"
    }

    // -------------------------------------------------------------- addressing
    /** True only for globally-routable public IPv4 addresses. */
    fun isPublicIp(ip: String): Boolean = try {
        val a = InetAddress.getByName(ip)
        if (a !is java.net.Inet4Address) false
        else !a.isLoopbackAddress && !a.isAnyLocalAddress &&
            !a.isLinkLocalAddress && !a.isSiteLocalAddress &&
            !a.isMulticastAddress && isGloballyRoutable(ip)
    } catch (e: Exception) {
        false
    }

    private fun isGloballyRoutable(ip: String): Boolean {
        val o = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (o.size != 4) return false
        val (a, b) = o
        return when {
            a == 0 -> false                      // this-network
            a == 10 -> false                     // RFC1918
            a == 100 && b in 64..127 -> false    // CGNAT
            a == 127 -> false                    // loopback
            a == 169 && b == 254 -> false        // link-local
            a == 172 && b in 16..31 -> false     // RFC1918
            a == 192 && b == 168 -> false        // RFC1918
            a == 192 && b == 0 && (o[2] == 0 || o[2] == 2) -> false // test/bench
            a == 198 && (b == 18 || b == 19) -> false               // bench
            a >= 224 -> false                    // multicast + reserved
            else -> true
        }
    }

    // -------------------------------------------------------------------- DNS
    /** Resolve a hostname to all A/AAAA records (system resolver). */
    fun dnsLookup(host: String): List<String> = try {
        val clean = host.trim().removePrefix("http://").removePrefix("https://")
            .substringBefore('/').substringBefore(':')
        InetAddress.getAllByName(clean).mapNotNull { it.hostAddress }.distinct()
    } catch (e: Exception) {
        emptyList()
    }

    // -------------------------------------------------------------------- geo
    private const val GEO_API = "http://ip-api.com/json/"

    fun geolocate(ip: String): GeoInfo? {
        if (!isPublicIp(ip)) return null
        val r = Net.httpGet("$GEO_API$ip?fields=status,message,country,city,isp,as,query", emptyMap(), 6000)
        if (r.error != null || !r.ok) return null
        return try {
            val o = JSONObject(r.text())
            if (o.optString("status") != "success") null
            else GeoInfo(
                ip = o.optString("query", ip),
                country = o.optString("country").ifBlank { null },
                city = o.optString("city").ifBlank { null },
                isp = o.optString("isp").ifBlank { null },
                as_ = o.optString("as").ifBlank { null },
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Public egress IP + its geolocation in one call. */
    fun publicIpAndGeo(): Pair<String?, GeoInfo?> = try {
        val r = Net.httpGet("$GEO_API?fields=status,country,city,isp,as,query", emptyMap(), 6000)
        if (r.error != null || !r.ok) null to null
        else {
            val o = JSONObject(r.text())
            if (o.optString("status") != "success") null to null
            else {
                val ip = o.optString("query").ifBlank { null }
                val geo = ip?.let {
                    GeoInfo(
                        ip = it,
                        country = o.optString("country").ifBlank { null },
                        city = o.optString("city").ifBlank { null },
                        isp = o.optString("isp").ifBlank { null },
                        as_ = o.optString("as").ifBlank { null },
                    )
                }
                ip to geo
            }
        }
    } catch (e: Exception) {
        null to null
    }

    /** Full dashboard snapshot; geo lookups stay on Dispatchers.IO callers. */
    fun snapshot(): Snapshot {
        val ifaces = interfaces()
        val (pub, geo) = publicIpAndGeo()
        return Snapshot(
            ifaces = ifaces,
            gateway = gateway(),
            publicIp = pub,
            geo = geo,
            vpnActive = ifaces.any { it.vpn && it.up },
            proxy = proxyInfo(),
        )
    }
}
