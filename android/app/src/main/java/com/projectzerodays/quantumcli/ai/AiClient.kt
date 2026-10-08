package com.projectzerodays.quantumcli.ai

import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.projectzerodays.quantumcli.QuantumApp
import com.projectzerodays.quantumcli.c2.C2State
import com.projectzerodays.quantumcli.data.QuantSettings
import com.projectzerodays.quantumcli.ops.Net
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * AI brains for the chat window:
 *  - Online: Abliterated.ai-compatible /chat/completions endpoint (URL + key
 *    from settings; default https://api.abliteration.ai/v1 with model
 *    abliterated-model-large-v2).
 *  - Local: MediaPipe LLM Inference API over a .task model in the app files
 *    dir; disabled with a hint when no model is present.
 */
object AiClient {

    const val MODE_ONLINE = "online"
    const val MODE_LOCAL = "local"
    const val MODE_DETERMINISTIC = "deterministic"

    const val SYSTEM_PROMPT =
        "You are QUANTUM-CLI's red-team commander running inside an authorized " +
            "security-lab C2 app on Android. Answer concisely in a terminal tone. " +
            "When asked to choose a next action, reply as strict JSON: " +
            "{\"action\": \"...\", \"target\": \"ip-or-domain-or-blank\", \"reason\": \"one sentence\"}."

    // ---------------------------------------------------------------- online
    /** POST /chat/completions; returns choices[0].message.content or null. */
    fun onlineChat(
        messages: List<Pair<String, String>>,
        timeoutMs: Int = 60_000,
    ): String? {
        val r = Providers.resolve(QuantSettings.state.value)
        if (r.url.isBlank() || r.key.isBlank()) return null
        val startedAt = System.currentTimeMillis()
        val url = r.url.trimEnd('/') + "/chat/completions"
        val arr = JSONArray()
        for ((role, content) in messages) {
            arr.put(JSONObject().put("role", role).put("content", content))
        }
        val body = JSONObject().put("model", r.model).put("messages", arr).toString()
        val resp = Net.httpPost(
            url,
            body.toByteArray(Charsets.UTF_8),
            mapOf(
                "Authorization" to "Bearer ${r.key}",
                "Content-Type" to "application/json"
            ),
            timeoutMs
        )
        if (resp.error != null) {
            C2State.audit("AI", "online chat failed: ${resp.error}")
            recordTelemetry("online", startedAt, 0, ok = false)
            return null
        }
        return try {
            val content = JSONObject(resp.text())
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            recordTelemetry("online", startedAt, content.length, ok = true)
            content
        } catch (e: Exception) {
            C2State.audit("AI", "online reply parse failed: ${resp.text(120)}")
            recordTelemetry("online", startedAt, 0, ok = false)
            null
        }
    }

    /** Telemetry gate — samples only land in MlTelemetry when enabled. */
    private fun recordTelemetry(mode: String, startedAt: Long, outChars: Int, ok: Boolean) {
        if (QuantSettings.state.value.mlTelemetry) {
            MlTelemetry.record(mode, System.currentTimeMillis() - startedAt, outChars, ok)
        }
    }

    // ----------------------------------------------------------------- local
    @Volatile
    private var inference: LlmInference? = null

    @Volatile
    private var loadedPath: String? = null

    fun localAvailable(): Boolean = localModelFile() != null

    fun localModelFile(): File? = QuantSettings.localModelFile()

    fun localHint(): String {
        val f = localModelFile()
        return if (f != null) {
            "Local model loaded from files/${f.name}"
        } else {
            "No .task model found — drop a MediaPipe LLM .task file into the app files dir to enable local AI"
        }
    }

    /** MediaPipe LLM Inference API — generateResponse on the .task model. */
    fun localChat(prompt: String): String? {
        val f = localModelFile() ?: return null
        val inf = ensureInference(f) ?: return null
        val startedAt = System.currentTimeMillis()
        return try {
            val out = inf.generateResponse(prompt)
            recordTelemetry("local", startedAt, out.length, ok = true)
            out
        } catch (e: Exception) {
            C2State.audit("AI", "local inference failed: ${e.message}")
            recordTelemetry("local", startedAt, 0, ok = false)
            null
        }
    }

    private fun ensureInference(f: File): LlmInference? {
        if (inference != null && loadedPath == f.absolutePath) return inference
        closeLocal()
        return try {
            val s = QuantSettings.state.value
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(f.absolutePath)
                .setMaxTokens(s.mlMaxTokens)
                .setTemperature(s.mlTemperature)
                .setTopK(s.mlTopK)
                .build()
            val engine = LlmInference.createFromOptions(QuantumApp.ctx, options)
            inference = engine
            loadedPath = f.absolutePath
            engine
        } catch (e: Throwable) {
            C2State.audit("AI", "local model load failed: ${e.message}")
            null
        }
    }

    fun closeLocal() {
        val inf = inference ?: return
        try {
            inf.close()
        } catch (e: Exception) {
            // engine already closed
        }
        inference = null
        loadedPath = null
    }
}
