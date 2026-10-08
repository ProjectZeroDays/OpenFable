package com.projectzerodays.quantumcli.ops

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * In-app honeypot manager — runs fake services on THIS device to observe who
 * connects. Every listener is an ordinary local ServerSocket; presets bind
 * unprivileged ports (>=1024) so no root is required. Ports below 1024 need
 * root and are NOT offered — the UI says so instead of failing at bind time.
 *
 * Honest scope: hits are inbound connections to this phone/host only; service
 * banners emulate real daemons (vsFTPd, OpenSSH, etc.) to encourage a payload
 * or credential probe, and the first received lines are captured verbatim.
 * Binary-protocol clients (MQTT/redis) may send no newline — the hit is still
 * recorded on connect, with an empty detail.
 */
object Honeypot {

    data class Service(
        val id: String,
        val name: String,
        val banner: String,          // greeting sent immediately on connect
        val port: Int,               // 0 = ephemeral (tests)
        val replies: List<String> = emptyList(), // replied per received line, last repeats
        val rootOnly: Boolean = false,
    )

    data class Hit(
        val ts: Long,
        val serviceId: String,
        val remoteIp: String,
        val remotePort: Int,
        val detail: String,
    )

    /** Unprivileged preset catalog — all bindable without root. */
    val PRESETS: List<Service> = listOf(
        Service(
            "http-admin", "HTTP admin panel",
            "HTTP/1.1 200 OK\r\nServer: nginx/1.18.0\r\n" +
                "Content-Type: text/html\r\nConnection: close\r\n\r\n" +
                "<html><head><title>Administration</title></head>" +
                "<body><h1>401 Unauthorized</h1></body></html>",
            port = 8080,
        ),
        Service(
            "ssh", "SSH",
            "SSH-2.0-OpenSSH_8.9p1 Ubuntu-3ubuntu0.6\r\n",
            port = 2222,
        ),
        Service(
            "ftp", "FTP",
            "220 (vsFTPd 3.0.5)\r\n",
            port = 2121,
            replies = listOf("331 Please specify the password.\r\n", "530 Login incorrect.\r\n"),
        ),
        Service(
            "telnet", "Telnet",
            "\r\nlogin: ",
            port = 2323,
            replies = listOf("Password: ", "Login incorrect\r\nlogin: "),
        ),
        Service(
            "mqtt", "MQTT broker",
            "", // MQTT servers send nothing first; CONNACK hex below
            port = 1883,
        ),
        Service(
            "redis", "Redis",
            "", // redis replies per command instead of greeting
            port = 6379,
            replies = listOf("+OK\r\n"),
        ),
        Service(
            "mysql", "MySQL",
            "", // server greeting is a binary packet — capture-only here
            port = 3306,
        ),
    )

    private data class Running(val socket: ServerSocket, val thread: Thread)

    private val running = ConcurrentHashMap<String, Running>()
    private val boundPorts = ConcurrentHashMap<String, Int>()

    private val _hits = MutableStateFlow<List<Hit>>(emptyList())
    val hits: StateFlow<List<Hit>> = _hits.asStateFlow()

    private val _runningIds = MutableStateFlow<Set<String>>(emptySet())
    val runningIds: StateFlow<Set<String>> = _runningIds.asStateFlow()

    /** Max captured lines per connection (keeps memory bounded). */
    private const val MAX_DETAIL_LINES = 10

    // ------------------------------------------------------------ control

    /** Start [service]; returns an honest status string (never throws). */
    fun start(service: Service): String {
        if (running.containsKey(service.id)) return "${service.name} already running"
        if (service.port in 1..1023) {
            return "port ${service.port} is privileged — root required " +
                "(presets stay on unprivileged ports)"
        }
        val server = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress("0.0.0.0", service.port))
            }
        } catch (e: Exception) {
            return "bind failed on :${service.port} — ${e.message ?: "port busy"}"
        }
        boundPorts[service.id] = server.localPort
        val thread = Thread({
            acceptLoop(service, server)
        }, "honeypot-${service.id}").apply { isDaemon = true }
        running[service.id] = Running(server, thread)
        thread.start()
        _runningIds.value = running.keys.toSet()
        return "started ${service.name} on :${server.localPort}"
    }

    fun stop(id: String): String {
        val r = running.remove(id) ?: return "not running"
        boundPorts.remove(id)
        try {
            r.socket.close()
        } catch (_: Exception) {
        }
        r.thread.interrupt()
        _runningIds.value = running.keys.toSet()
        return "stopped"
    }

    fun stopAll() {
        running.keys.toList().forEach { stop(it) }
    }

    fun boundPort(id: String): Int? = boundPorts[id]

    fun clearHits() {
        _hits.value = emptyList()
    }

    // -------------------------------------------------------------- serve

    private fun acceptLoop(service: Service, server: ServerSocket) {
        while (!server.isClosed) {
            val sock = try {
                server.accept()
            } catch (_: Exception) {
                return // closed
            }
            Thread({ handle(service, sock) }, "honeypot-conn").apply {
                isDaemon = true
            }.start()
        }
    }

    private fun handle(service: Service, sock: Socket) {
        try {
            sock.use { s ->
                s.soTimeout = 8_000
                val ip = s.inetAddress?.hostAddress ?: "?"
                val port = s.port // Android Socket: getPort() = remote port
                // record immediately — silent scanners count too
                record(Hit(System.currentTimeMillis(), service.id, ip, port, ""))

                val out = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
                if (service.banner.isNotEmpty()) {
                    out.write(service.banner)
                    out.flush()
                }

                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val seen = mutableListOf<String>()
                var replyIdx = 0
                while (seen.size < MAX_DETAIL_LINES) {
                    val line = try {
                        reader.readLine() ?: break
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    } catch (_: Exception) {
                        break
                    }
                    seen.add(line.take(256))
                    if (service.replies.isNotEmpty()) {
                        out.write(service.replies[minOf(replyIdx, service.replies.lastIndex)])
                        out.flush()
                        replyIdx++
                    }
                }
                if (seen.isNotEmpty()) {
                    updateDetail(service.id, ip, port, seen.joinToString("; "))
                }
            }
        } catch (_: Exception) {
            // connection dropped — hit already recorded
        }
    }

    // --------------------------------------------------------------- hits

    @Synchronized
    private fun record(hit: Hit) {
        _hits.value = _hits.value + hit
    }

    /** Attach captured lines to the most recent connect from that peer. */
    @Synchronized
    private fun updateDetail(serviceId: String, ip: String, port: Int, detail: String) {
        val list = _hits.value
        for (i in list.indices.reversed()) {
            val h = list[i]
            if (h.serviceId == serviceId && h.remoteIp == ip && h.remotePort == port &&
                h.detail.isEmpty()
            ) {
                _hits.value = list.toMutableList().also { it[i] = h.copy(detail = detail) }
                return
            }
        }
    }

    // -------------------------------------------------------------- export

    /** Hits as a JSON array (manual escaping — org.json is Android-only). */
    fun exportJson(): String {
        val sb = StringBuilder("[")
        _hits.value.forEachIndexed { i, h ->
            if (i > 0) sb.append(',')
            sb.append("{")
                .append("\"ts\":").append(h.ts).append(',')
                .append("\"service\":").append(jsonStr(h.serviceId)).append(',')
                .append("\"ip\":").append(jsonStr(h.remoteIp)).append(',')
                .append("\"port\":").append(h.remotePort).append(',')
                .append("\"detail\":").append(jsonStr(h.detail))
                .append('}')
        }
        return sb.append(']').toString()
    }

    internal fun jsonStr(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) {
                    sb.append("\\u").append(String.format("%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        return sb.append('"').toString()
    }

    /** Test seam: inject a hit without a socket. */
    internal fun recordForTest(hit: Hit) = record(hit)
}
