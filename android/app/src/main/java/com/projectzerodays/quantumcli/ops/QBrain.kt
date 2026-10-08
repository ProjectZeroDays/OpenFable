package com.projectzerodays.quantumcli.ops

import org.json.JSONArray
import org.json.JSONObject

/** QBrain parity — deterministic decision/CVE brain (the no-AI path). */
object QBrain {

    val PORT_CVES: Map<Int, List<String>> = mapOf(
        445 to listOf("CVE-2020-1472", "CVE-2017-0144"),
        139 to listOf("CVE-2017-0144"),
        3389 to listOf("CVE-2017-0144"),
        8080 to listOf("CVE-2021-44228"),
        443 to listOf("CVE-2021-26855"),
        7001 to listOf("CVE-2020-14882"),
    )

    val SERVICE_CVES: Map<String, List<String>> = mapOf(
        "smb" to listOf("CVE-2020-1472", "CVE-2017-0144"),
        "rtsp" to listOf("CVE-2017-7921", "CVE-2021-33044"),
        "ssh" to listOf("CVE-2016-5195"),
        "exchange" to listOf("CVE-2021-26855"),
        "weblogic" to listOf("CVE-2020-14882"),
        "citrix" to listOf("CVE-2023-4966"),
        "panos" to listOf("CVE-2024-3400"),
        "vmware" to listOf("CVE-2021-21972"),
        "confluence" to listOf("CVE-2022-26134"),
    )

    fun decide(state: JSONObject, acts: List<String>): JSONObject {
        val cams = state.optJSONArray("cams") ?: JSONArray()
        val fired = state.optJSONArray("fired") ?: JSONArray()
        val hasControlled = (0 until cams.length()).any { cams.getJSONObject(it).optBoolean("controlled") }
        val targets = state.optJSONArray("targets")

        var pick: String
        pick = when {
            cams.length() > 0 && hasControlled -> "cam_control"
            cams.length() == 0 -> if ("cam_war" in acts) "cam_war" else "cam_discover"
            fired.length() > 0 && "harvest" in acts -> "harvest"
            "exploit" in acts && targets != null && targets.length() > 0 -> "exploit"
            "brute" in acts && cams.length() > 0 -> "brute"
            else -> if ("rest" in acts) "rest" else acts.lastOrNull() ?: "rest"
        }
        if (pick !in acts) pick = acts.firstOrNull() ?: "rest"

        var target = ""
        if (pick == "exploit" && fired.length() > 0) {
            target = fired.optString(fired.length() - 1)
        }
        return JSONObject()
            .put("action", pick)
            .put("target", target)
            .put(
                "reason",
                "QBrain deterministic: state has ${cams.length()} cams, ${fired.length()} fired"
            )
    }

    fun planAttack(recon: JSONObject): String {
        val cands = ArrayList<String>()
        for ((service, cves) in SERVICE_CVES) {
            if (recon.optBoolean(service)) cands.addAll(cves)
        }
        val ports = recon.optJSONArray("ports")
        if (ports != null) {
            for (i in 0 until ports.length()) {
                PORT_CVES[ports.optInt(i)]?.let { cands.addAll(it) }
            }
        }
        val registryCves = Weapons.REGISTRY.map { it.cve }.toSet()
        for (cve in cands) {
            if (cve in registryCves) return cve
        }
        return Weapons.REGISTRY.firstOrNull()?.cve ?: "CVE-2021-44228"
    }

    fun phishBody(email: String, subject: String = "Payroll"): String =
        "Hi,\n\nPlease review the updated $subject document for your " +
            "account ($email). Open the attachment to confirm your details " +
            "before Friday.\n\nRegards,\nPayroll Team"

    fun summary(params: JSONObject): String =
        "QBrain summary: ${params.toString().take(300)}"
}
