package com.projectzerodays.quantumcli.ai

import com.projectzerodays.quantumcli.ui.Page
import org.json.JSONObject

/**
 * Deterministic intent parser — the 'No AI' chat mode. Recognized intents:
 * loot, camera scan, task dispatch, background job control, kill-switch.
 */
object IntentParser {

    sealed class ChatAction {
        data class OpenPage(val page: Page) : ChatAction()
        data class StartCameraScan(val cidr: String? = null) : ChatAction()
        data class RunAiCommand(val cmd: String, val params: JSONObject = JSONObject()) : ChatAction()
        data class StartOverlord(val cameraOnly: Boolean = false) : ChatAction()
        object StopOverlord : ChatAction()
        data class ArmKillswitch(val arm: Boolean) : ChatAction()
    }

    data class Reply(val text: String, val action: ChatAction? = null)

    private val CIDR_RE = Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(?:/\\d{1,2})?\\b")
    private val IPV4_RE = Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b")

    fun parse(input: String): Reply {
        val low = input.trim().lowercase()

        // ---- loot ----
        if (low.contains("loot")) {
            return Reply("Opening the sealed loot vault…", ChatAction.OpenPage(Page.LOOT))
        }

        // ---- kill-switch ----
        if (low.contains("kill") || low.contains("killswitch") || low.contains("kill-switch")) {
            val disarm = low.contains("disarm") || low.contains("off")
            return Reply(
                if (disarm) "Kill-switch disarmed — implants keep beaconing." else "Kill-switch ARMED — wipe/die tasks queued on every implant.",
                ChatAction.ArmKillswitch(!disarm)
            )
        }

        // ---- camera scan ----
        if ((low.contains("scan") || low.contains("discover")) &&
            (low.contains("cam") || low.contains("camera"))
        ) {
            val cidr = CIDR_RE.find(input)?.value
            return Reply(
                "Camera sweep starting${cidr?.let { " on $it" } ?: " on the local /24"} — Cameras page opened.",
                ChatAction.StartCameraScan(cidr)
            )
        }

        // ---- background job / overlord ----
        if (low.contains("background job") || low.contains("overlord") || low.contains("autonomous")) {
            return if (low.contains("stop") || low.contains("halt")) {
                Reply("Overlord cycle stopped.", ChatAction.StopOverlord)
            } else {
                val cameraOnly = low.contains("camera") || low.contains("cam only")
                Reply(
                    "Overlord cycle started${if (cameraOnly) " (camera-only acts)" else ""} — decisions will journal to the Dashboard.",
                    ChatAction.StartOverlord(cameraOnly)
                )
            }
        }

        // ---- run task X ----
        val taskMatch = Regex(
            "(?:run|exec|execute|dispatch)\\s+(?:task\\s+)?(?:/ai\\s+)?([a-z_0-9]+)(.*)"
        ).find(low)
        if (taskMatch != null) {
            val cmd = taskMatch.groupValues[1]
            if (cmd in KNOWN_CMDS) {
                val rest = taskMatch.groupValues[2]
                val ip = IPV4_RE.find(rest)?.value
                val params = JSONObject()
                if (ip != null) params.put("ip", ip)
                return Reply(
                    "Dispatching '$cmd'${ip?.let { " against $it" } ?: ""} via the local /ai dispatcher…",
                    ChatAction.RunAiCommand(cmd, params)
                )
            }
        }

        // ---- fallback: try the input itself as an /ai command ----
        val bare = low.replace(Regex("^/ai\\s+"), "").trim()
        if (bare in KNOWN_CMDS) {
            return Reply("Dispatching '$bare' via the local /ai dispatcher…", ChatAction.RunAiCommand(bare))
        }

        return Reply(
            "No AI brain engaged (No-AI mode). Deterministic intents I understand: " +
                "show loot · scan for cameras [cidr] · run task <cmd> [ip] · " +
                "start background job · stop background job · kill-switch [arm|disarm] · " +
                "or a bare /ai command (${KNOWN_CMDS.joinToString(", ")})."
        )
    }

    val KNOWN_CMDS = setOf(
        "status", "zerocheck", "camwar", "mitre", "decide", "plan", "kill",
        "fire", "scan", "fingerprint", "brute", "rtspbrute", "roast", "phish",
        "kitlogger", "persistx", "footprint", "flashfill", "emojisms", "macreset",
        "mshtmaldoc", "camshot"
    )
}
