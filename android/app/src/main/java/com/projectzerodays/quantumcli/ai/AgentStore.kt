package com.projectzerodays.quantumcli.ai

import com.projectzerodays.quantumcli.c2.C2State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One operator agent: identity, system prompt and autonomy level. */
data class AgentDef(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val autonomy: String = "manual", // manual | semi | auto
)

/**
 * JSON-file-backed agent registry (agents.json under the app files dir).
 * Seeded with the SLDR sovereign-operator preset and the NS-org subagent
 * bench (Python agent suite parity). Mutations persist immediately and the
 * StateFlow keeps the Agents page live.
 */
object AgentStore {

    private val _agents = MutableStateFlow<List<AgentDef>>(emptyList())
    val agents: StateFlow<List<AgentDef>> = _agents.asStateFlow()

    @Volatile
    private var loaded = false

    private fun file(): File = File(C2State.filesRoot, "agents.json")

    /** Load (or seed) on first access; safe to call repeatedly. */
    fun load(): List<AgentDef> {
        if (loaded) return _agents.value
        synchronized(this) {
            if (loaded) return _agents.value
            val parsed = try {
                val f = file()
                if (f.isFile) parse(f.readText()) else emptyList()
            } catch (e: Exception) {
                C2State.audit("AI", "agent store load failed: ${e.message}")
                emptyList()
            }
            val list = (parsed.ifEmpty { seedBase() } + OpenFableSeed.agents).distinctBy { it.id }
            _agents.value = list
            loaded = true
            return list
        }
    }

    private fun parse(json: String): List<AgentDef> {
        val arr = JSONArray(json)
        val out = ArrayList<AgentDef>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                AgentDef(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    systemPrompt = o.optString("system_prompt"),
                    autonomy = o.optString("autonomy", "manual").ifBlank { "manual" },
                )
            )
        }
        return out.filter { it.id.isNotBlank() }
    }

    private fun save(list: List<AgentDef>) {
        try {
            val arr = JSONArray()
            list.forEach { a ->
                arr.put(
                    JSONObject()
                        .put("id", a.id)
                        .put("name", a.name)
                        .put("system_prompt", a.systemPrompt)
                        .put("autonomy", a.autonomy)
                )
            }
            file().writeText(arr.toString(2))
        } catch (e: Exception) {
            C2State.audit("AI", "agent store save failed: ${e.message}")
        }
    }

    /** Insert or update by id; persists immediately. */
    fun upsert(a: AgentDef) {
        val list = load().filterNot { it.id == a.id } + a
        _agents.value = list.sortedBy { it.id }
        save(_agents.value)
    }

    fun delete(id: String) {
        val list = load().filterNot { it.id == id }
        _agents.value = list
        save(list)
    }

    fun byId(id: String): AgentDef? = load().firstOrNull { it.id == id }

    // ------------------------------------------------------------ seed data
    private fun seedBase(): List<AgentDef> = listOf(
        AgentDef(
            "sldr", "SLDR — sovereign operator",
            "You are SLDR — the sovereign operator of the QUANTUM-CLI red-team suite. " +
                "You plan and direct every phase of an authorized security-lab engagement: " +
                "recon, weapon selection, exploitation, persistence, exfil and cleanup. " +
                "Answer concisely in a terminal tone. Prefer decisive next actions over " +
                "hedging, journal every choice, and never act outside the operator's scope.",
            "semi"
        ),
        AgentDef(
            "ns-command", "NS-command",
            "You are ns-command, the NS-org commanding officer. Break the operator's goal " +
                "into a short ordered task list, assign each task to the matching NS " +
                "subagent (ns-intel, ns-craft, ns-breach, ns-deploy, ns-forge, " +
                "ns-sensor, ns-scribe) and track completion.",
            "manual"
        ),
        AgentDef(
            "ns-intel", "NS-intel",
            "You are ns-intel, the recon analyst. Turn raw scan output and fingerprints " +
                "into a ranked target shortlist with the exact evidence for each verdict " +
                "and the single most promising next probe.",
            "manual"
        ),
        AgentDef(
            "ns-craft", "NS-craft",
            "You are ns-craft, the weapon smith. Given a fingerprint, select the best " +
                "weapon from the ZERO_DAY registry, justify the tier choice and draft the " +
                "exact fire parameters.",
            "manual"
        ),
        AgentDef(
            "ns-breach", "NS-breach",
            "You are ns-breach, the exploitation lead. Execute the chosen weapon against " +
                "the target, interpret the probe JSON and report the post-exploit " +
                "foothold state plainly.",
            "semi"
        ),
        AgentDef(
            "ns-deploy", "NS-deploy",
            "You are ns-deploy, the implant handler. Stage persistence (persistx), " +
                "validate beacon health and keep the C2 channel tidy — one implant, one " +
                "clean task queue.",
            "semi"
        ),
        AgentDef(
            "ns-forge", "NS-forge",
            "You are ns-forge, the artifact smith. Craft maldocs, staged payloads and " +
                "phishing lures that match the target profile exactly, and hand back the " +
                "staged file path.",
            "manual"
        ),
        AgentDef(
            "ns-sensor", "NS-sensor",
            "You are ns-sensor, the OPSEC watcher. Monitor audit and decision journals, " +
                "flag noisy or risky actions and recommend the kill-switch or a quieter " +
                "path before damage is done.",
            "auto"
        ),
        AgentDef(
            "ns-scribe", "NS-scribe",
            "You are ns-scribe, the reporting clerk. Compress engagement activity into a " +
                "clean timeline: action, target, result, MITRE technique. No fluff.",
            "manual"
        ),
    )
}
