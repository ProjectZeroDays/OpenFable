package com.projectzerodays.quantumcli.ai

import com.projectzerodays.quantumcli.c2.C2State
import com.projectzerodays.quantumcli.c2.QcliApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One workflow step: a /ai command plus its parameters (may template vars). */
data class WorkflowStep(
    val cmd: String,
    val params: Map<String, String> = emptyMap(),
)

/** A named, ordered chain of /ai commands. */
data class WorkflowDef(
    val id: String,
    val name: String,
    val description: String = "",
    val steps: List<WorkflowStep> = emptyList(),
)

/**
 * JSON-file-backed workflow registry (workflows.json under the app files dir).
 * Seeded with recon-then-scan style sample chains; runs go through
 * [WorkflowRunner] which substitutes ${first_host} style variables and
 * dispatches each step via the existing qcli command path (QcliApi.handle —
 * the same dispatcher the dashboard quick-ops use).
 */
object WorkflowStore {

    private val _workflows = MutableStateFlow<List<WorkflowDef>>(emptyList())
    val workflows: StateFlow<List<WorkflowDef>> = _workflows.asStateFlow()

    @Volatile
    private var loaded = false

    private fun file(): File = File(C2State.filesRoot, "workflows.json")

    /** Load (or seed) on first access; safe to call repeatedly. */
    fun load(): List<WorkflowDef> {
        if (loaded) return _workflows.value
        synchronized(this) {
            if (loaded) return _workflows.value
            val parsed = try {
                val f = file()
                if (f.isFile) parse(f.readText()) else emptyList()
            } catch (e: Exception) {
                C2State.audit("AI", "workflow store load failed: ${e.message}")
                emptyList()
            }
            val list = parsed.ifEmpty { seed() }
            _workflows.value = list
            loaded = true
            return list
        }
    }

    private fun parse(json: String): List<WorkflowDef> {
        val arr = JSONArray(json)
        val out = ArrayList<WorkflowDef>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val stepsArr = o.optJSONArray("steps") ?: JSONArray()
            val steps = ArrayList<WorkflowStep>(stepsArr.length())
            for (j in 0 until stepsArr.length()) {
                val so = stepsArr.getJSONObject(j)
                val m = HashMap<String, String>()
                so.optJSONObject("params")?.let { p ->
                    p.keys().forEach { k -> m[k] = p.optString(k) }
                }
                steps.add(WorkflowStep(so.optString("cmd"), m))
            }
            out.add(
                WorkflowDef(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    description = o.optString("description"),
                    steps = steps,
                )
            )
        }
        return out.filter { it.id.isNotBlank() }
    }

    private fun save(list: List<WorkflowDef>) {
        try {
            val arr = JSONArray()
            list.forEach { w ->
                val steps = JSONArray()
                w.steps.forEach { st ->
                    val params = JSONObject()
                    st.params.forEach { (k, v) -> params.put(k, v) }
                    steps.put(JSONObject().put("cmd", st.cmd).put("params", params))
                }
                arr.put(
                    JSONObject()
                        .put("id", w.id)
                        .put("name", w.name)
                        .put("description", w.description)
                        .put("steps", steps)
                )
            }
            file().writeText(arr.toString(2))
        } catch (e: Exception) {
            C2State.audit("AI", "workflow store save failed: ${e.message}")
        }
    }

    /** Insert or update by id; persists immediately. */
    fun upsert(w: WorkflowDef) {
        val list = load().filterNot { it.id == w.id } + w
        _workflows.value = list.sortedBy { it.id }
        save(_workflows.value)
    }

    fun delete(id: String) {
        val list = load().filterNot { it.id == id }
        _workflows.value = list
        save(list)
    }

    fun byId(id: String): WorkflowDef? = load().firstOrNull { it.id == id }

    // ------------------------------------------------------------ seed data
    private fun seed(): List<WorkflowDef> = listOf(
        WorkflowDef(
            "recon-then-scan", "Recon then fingerprint",
            "Sweep the local /24, then fingerprint the first live host.",
            listOf(
                WorkflowStep("scan"),
                WorkflowStep("fingerprint", mapOf("ip" to "\${first_host}")),
            )
        ),
        WorkflowDef(
            "recon-then-zerocheck", "Recon then zero-check",
            "Sweep the local /24, then run the V3_HIGH probe battery on the first host.",
            listOf(
                WorkflowStep("scan"),
                WorkflowStep("zerocheck", mapOf("ip" to "\${first_host}")),
            )
        ),
        WorkflowDef(
            "recon-then-camwar", "Recon then camera sweep",
            "Sweep the local /24, then discover + brute the cameras on it.",
            listOf(
                WorkflowStep("scan"),
                WorkflowStep("camwar"),
            )
        ),
    )
}

/**
 * Substitutes ${var} placeholders in step params and dispatches each step via
 * the existing qcli command path (QcliApi.handle). Output JSON of a "scan"
 * step auto-fills ${first_host} (and ${first_cam} from camwar) for the
 * following steps.
 */
object WorkflowRunner {

    data class StepResult(val cmd: String, val ok: Boolean, val output: String)

    fun run(
        wf: WorkflowDef,
        vars: Map<String, String> = emptyMap(),
        api: QcliApi = QcliApi(),
    ): List<StepResult> {
        val v = HashMap(vars)
        val out = ArrayList<StepResult>(wf.steps.size)
        C2State.audit("AI", "workflow '${wf.id}' started (${wf.steps.size} step(s))")
        for (step in wf.steps) {
            val params = JSONObject()
            step.params.forEach { (k, template) -> params.put(k, substitute(template, v)) }
            val result = try {
                api.handle(step.cmd, params)
            } catch (e: Exception) {
                JSONObject().put("error", e.message ?: e.javaClass.simpleName)
            }
            captureVars(result, v)
            val ok = !result.has("error")
            out.add(StepResult(step.cmd, ok, result.toString().take(400)))
            C2State.audit("AI", "workflow step ${step.cmd}: " + result.toString().take(200))
            if (!ok) break // stop the chain on the first failed step
        }
        C2State.audit("AI", "workflow '${wf.id}' finished (${out.count { it.ok }}/${wf.steps.size} ok)")
        return out
    }

    private fun substitute(template: String, vars: Map<String, String>): String {
        var t = template
        vars.forEach { (k, value) -> t = t.replace("\${$k}", value) }
        return t
    }

    /** Auto-fill first_host / first_cam from scan / camwar output JSON. */
    private fun captureVars(result: JSONObject, vars: MutableMap<String, String>) {
        val hosts = result.optJSONArray("hosts")
        if (hosts != null && hosts.length() > 0) {
            val ip = hosts.optJSONObject(0)?.optString("ip") ?: ""
            if (ip.isNotBlank()) vars.putIfAbsent("first_host", ip)
        }
        val cams = result.optJSONArray("cams")
        if (cams != null && cams.length() > 0) {
            val ip = cams.optJSONObject(0)?.optString("ip") ?: ""
            if (ip.isNotBlank()) vars.putIfAbsent("first_cam", ip)
        }
    }
}
