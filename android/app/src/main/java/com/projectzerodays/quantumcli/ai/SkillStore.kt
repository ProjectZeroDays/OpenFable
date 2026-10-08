package com.projectzerodays.quantumcli.ai

import com.projectzerodays.quantumcli.c2.C2State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One skill pack: a reusable prompt template for the chat brain. */
data class SkillDef(
    val id: String,
    val name: String,
    val description: String,
    val prompt: String,
)

/**
 * JSON-file-backed skill registry (skills.json under the app files dir).
 * Seeded with the operator skill packs (recon, weapon-craft, report).
 */
object SkillStore {

    private val _skills = MutableStateFlow<List<SkillDef>>(emptyList())
    val skills: StateFlow<List<SkillDef>> = _skills.asStateFlow()

    @Volatile
    private var loaded = false

    private fun file(): File = File(C2State.filesRoot, "skills.json")

    /** Load (or seed) on first access; safe to call repeatedly. */
    fun load(): List<SkillDef> {
        if (loaded) return _skills.value
        synchronized(this) {
            if (loaded) return _skills.value
            val parsed = try {
                val f = file()
                if (f.isFile) parse(f.readText()) else emptyList()
            } catch (e: Exception) {
                C2State.audit("AI", "skill store load failed: ${e.message}")
                emptyList()
            }
            val list = (parsed.ifEmpty { seedBase() } + OpenFableSeed.skills).distinctBy { it.id }
            _skills.value = list
            loaded = true
            return list
        }
    }

    private fun parse(json: String): List<SkillDef> {
        val arr = JSONArray(json)
        val out = ArrayList<SkillDef>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                SkillDef(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    description = o.optString("description"),
                    prompt = o.optString("prompt"),
                )
            )
        }
        return out.filter { it.id.isNotBlank() }
    }

    private fun save(list: List<SkillDef>) {
        try {
            val arr = JSONArray()
            list.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("name", s.name)
                        .put("description", s.description)
                        .put("prompt", s.prompt)
                )
            }
            file().writeText(arr.toString(2))
        } catch (e: Exception) {
            C2State.audit("AI", "skill store save failed: ${e.message}")
        }
    }

    /** Insert or update by id; persists immediately. */
    fun upsert(s: SkillDef) {
        val list = load().filterNot { it.id == s.id } + s
        _skills.value = list.sortedBy { it.id }
        save(_skills.value)
    }

    fun delete(id: String) {
        val list = load().filterNot { it.id == id }
        _skills.value = list
        save(list)
    }

    fun byId(id: String): SkillDef? = load().firstOrNull { it.id == id }

    // ------------------------------------------------------------ seed data
    private fun seedBase(): List<SkillDef> = listOf(
        SkillDef(
            "recon", "Recon pass",
            "Turn a CIDR sweep into a ranked target shortlist.",
            "Run a recon pass over the local scope. List live hosts with open ports, " +
                "rank the most promising targets and name the single best next probe " +
                "for each. Reply as a compact list, no fluff."
        ),
        SkillDef(
            "weapon-craft", "Weapon craft",
            "Match a fingerprint to the ZERO_DAY registry.",
            "Given this fingerprint, pick the best matching weapon from the ZERO_DAY " +
                "registry, justify the tier choice, and draft the exact fire " +
                "parameters as JSON {\"cve\": ..., \"ip\": ...}."
        ),
        SkillDef(
            "report", "Engagement report",
            "Compress the audit journal into a clean report.",
            "Summarize this engagement as an operator report: timeline of actions with " +
                "targets and results, MITRE techniques touched, loot collected and the " +
                "recommended cleanup pass. Terminal tone, tight prose."
        ),
        // ---- c2.* operator playbooks (from the Quantum C2 skill library) ----
        SkillDef(
            "c2.evasive-maneuvers", "Evasive maneuvers",
            "Adversary emulation with detection evasion in mind.",
            "Plan the engagement for stealth: assume EDR/SIEM coverage. Prefer " +
                "living-off-the-land techniques, jittered timing, masqueraded " +
                "process names, and reversible persistence. Map every evasion " +
                "choice to its MITRE ATT&CK defense-evasion technique and state " +
                "the rollback step."
        ),
        SkillDef(
            "c2.zero-day-research", "Zero-day research",
            "Triage a new CVE: exploitability and weapon fit.",
            "For the given CVE: summarize the root cause class (memory corruption, " +
                "logic, injection), the prerequisites, whether it is remotely " +
                "exploitable pre-auth, whether it is in the CISA KEV, and " +
                "whether the app's ZERO_DAY registry or exploit_payloads folders " +
                "already weaponize it. Cite sources, mark uncertainty honestly."
        ),
        SkillDef(
            "c2.incident-response", "Incident response",
            "Structured IR: triage, contain, eradicate, recover.",
            "Act as the incident commander: triage the reported indicators, form " +
                "containment options ranked by business impact, list eradication " +
                "steps (persistence sweep, credential resets), and the recovery " +
                "verification pass. Keep a strict evidence timeline."
        ),
        SkillDef(
            "c2.threat-intel", "Threat intel digest",
            "KEV-driven prioritization of vulnerabilities.",
            "Given the current CISA KEV feed (kev command), rank the entries by " +
                "exposure of our environment, highlight zero-click and pre-auth " +
                "entries, and produce a patch-priority table."
        ),
        SkillDef(
            "c2.evasion", "OPSEC doctrine",
            "Operate as if watched — breadcrumbs minimized.",
            "Assume defenders have full telemetry. Review the planned technique " +
                "chain and flag attribution breadcrumbs, noisy artifacts and " +
                "irreversible actions; propose quieter alternates."
        ),
        SkillDef(
            "c2.payload-customizer", "Payload craft",
            "Adapt payload staging to the target profile.",
            "Given the target OS/build/AV context, recommend the delivery format " +
                "(document, script, wrapper APK), the stage protocol and the " +
                "beacon schedule. Justify each choice in one line."
        ),
        SkillDef(
            "c2.osint", "Social engineering OSINT",
            "Pretext building from open sources.",
            "For the given organization, build a pretext: likely roles, " +
                "communication norms, public events to reference, and a " +
                "credibility plan. No fabrication — only verifiable details."
        ),
    )
}
