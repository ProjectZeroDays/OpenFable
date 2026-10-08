package com.projectzerodays.quantumcli.ai

import com.projectzerodays.quantumcli.c2.QcliApi
import com.projectzerodays.quantumcli.data.QuantSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Chat window controller. Routes user input by the configured AI mode:
 * Online (Abliterated.ai-compatible endpoint), Local (MediaPipe .task model),
 * or No-AI (deterministic intent parser). Deterministic side-effects
 * (navigation, scans, overlord, kill-switch) surface via [onAction].
 */
object ChatController {

    data class Msg(val role: String, val content: String, val ts: String)

    private val _messages = MutableStateFlow(
        listOf(
            Msg(
                "assistant",
                "QUANTUM-CLI chat online. Modes: Online AI, Local on-device LLM, " +
                    "or deterministic No-AI intents (show loot · scan for cameras · " +
                    "run task <cmd> · start background job · kill-switch).",
                stamp()
            )
        )
    )
    val messages: StateFlow<List<Msg>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    var api: QcliApi = QcliApi()

    private fun stamp(): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    fun addAssistant(text: String) {
        _messages.value = _messages.value + Msg("assistant", text, stamp())
    }

    fun clear() {
        _messages.value = emptyList()
    }

    fun send(
        text: String,
        scope: CoroutineScope,
        onAction: (IntentParser.ChatAction) -> Unit,
    ) {
        if (text.isBlank() || _busy.value) return
        _messages.value = _messages.value + Msg("user", text, stamp())
        _busy.value = true
        scope.launch {
            try {
                val mode = QuantSettings.state.value.aiMode
                when (mode) {
                    AiClient.MODE_ONLINE -> {
                        if (Providers.resolve(QuantSettings.state.value).key.isBlank()) {
                            addAssistant(
                                "Online AI selected but no API key set — add it in Settings. " +
                                    deterministic(text, onAction)
                            )
                        } else {
                            val reply = withContext(Dispatchers.IO) {
                                AiClient.onlineChat(
                                    listOf("system" to AiClient.SYSTEM_PROMPT) +
                                        _messages.value.takeLast(10)
                                            .map { it.role to it.content }
                                )
                            }
                            if (reply != null) {
                                addAssistant(reply)
                            } else {
                                addAssistant(
                                    "Online AI unreachable — deterministic fallback:\n" +
                                        deterministic(text, onAction)
                                )
                            }
                        }
                    }
                    AiClient.MODE_LOCAL -> {
                        if (AiClient.localAvailable()) {
                            val prompt = buildString {
                                append(AiClient.SYSTEM_PROMPT).append("\n\n")
                                _messages.value.takeLast(6).forEach {
                                    append(it.role).append(": ").append(it.content).append("\n")
                                }
                            }
                            val reply = withContext(Dispatchers.IO) { AiClient.localChat(prompt) }
                            if (reply != null) {
                                addAssistant(reply)
                            } else {
                                addAssistant(
                                    "Local LLM failed — deterministic fallback:\n" +
                                        deterministic(text, onAction)
                                )
                            }
                        } else {
                            addAssistant(
                                AiClient.localHint() + "\nDeterministic reply: " +
                                    deterministic(text, onAction)
                            )
                        }
                    }
                    else -> addAssistant(deterministic(text, onAction))
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun deterministic(
        text: String,
        onAction: (IntentParser.ChatAction) -> Unit,
    ): String {
        val parsed = IntentParser.parse(text)
        return when (val a = parsed.action) {
            is IntentParser.ChatAction.RunAiCommand -> {
                val result = withContext(Dispatchers.IO) {
                    try {
                        api.handle(a.cmd, a.params).toString()
                    } catch (e: Exception) {
                        """{"error": "${e.message}"}"""
                    }
                }
                "cmd ${a.cmd} -> " + result.take(600)
            }
            is IntentParser.ChatAction.StartCameraScan -> {
                onAction(IntentParser.ChatAction.OpenPage(com.projectzerodays.quantumcli.ui.Page.CAMERAS))
                onAction(a)
                parsed.text
            }
            is IntentParser.ChatAction.OpenPage,
            is IntentParser.ChatAction.StartOverlord,
            is IntentParser.ChatAction.StopOverlord,
            is IntentParser.ChatAction.ArmKillswitch,
            null,
            -> {
                parsed.action?.let(onAction)
                parsed.text
            }
        }
    }
}
