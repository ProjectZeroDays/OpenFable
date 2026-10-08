package com.projectzerodays.quantumcli.ai

import com.projectzerodays.quantumcli.data.QuantSettings
import com.projectzerodays.quantumcli.data.SettingsData
import org.json.JSONObject

/**
 * Provider presets (quantum.ai.providers parity) — every preset speaks an
 * OpenAI-compatible /chat/completions dialect. The active preset id lives in
 * settings (aiProvider); [resolve] merges it with any manual URL/model
 * override and the per-provider key (aiApiKey), falling back to the legacy
 * abliteration key so existing installs keep working untouched.
 */
data class ProviderPreset(
    val id: String,
    val label: String,
    val url: String,
    val model: String,
    val keySetting: String,
)

object Providers {

    const val DEFAULT_ID = "abliteration"
    const val DEFAULT_URL = "https://api.abliteration.ai/v1"
    const val DEFAULT_MODEL = "abliterated-model-large-v2"

    val PRESETS: List<ProviderPreset> = listOf(
        ProviderPreset(
            "abliteration", "Abliteration.ai",
            "https://api.abliteration.ai/v1", "abliterated-model-large-v2", "ai_key"
        ),
        ProviderPreset(
            "openrouter", "OpenRouter",
            "https://openrouter.ai/api/v1", "openrouter/auto", "ai_api_key"
        ),
        ProviderPreset(
            "venice", "Venice.ai",
            "https://api.venice.ai/api/v1", "venice/uncensored", "ai_api_key"
        ),
        ProviderPreset(
            "agnes", "Agnes",
            "https://inference.agnes.ai/v1", "agnes/agi-1", "ai_api_key"
        ),
        ProviderPreset(
            "opencode", "OpenCode",
            "http://127.0.0.1:4096/v1", "opencode/grok-code", "ai_api_key"
        ),
    )

    /** Preset lookup — unknown ids fall back to the default preset. */
    fun byId(id: String): ProviderPreset =
        PRESETS.firstOrNull { it.id == id } ?: PRESETS.first()

    /** Effective endpoint / model / key for the current settings snapshot. */
    data class Resolved(val url: String, val model: String, val key: String)

    /**
     * Selection order (Python resolve_provider parity):
     *  1. manual URL/model overrides in settings win over the preset
     *  2. otherwise the selected preset's url/model
     *  3. key: per-provider aiApiKey, falling back to the legacy apiKey
     */
    fun resolve(s: SettingsData): Resolved {
        val preset = byId(s.aiProvider)
        val url = if (s.apiUrl.isNotBlank() && s.apiUrl != DEFAULT_URL) s.apiUrl else preset.url
        val model = if (s.model.isNotBlank() && s.model != DEFAULT_MODEL) s.model else preset.model
        val key = s.aiApiKey.ifBlank { s.apiKey }
        return Resolved(url, model, key)
    }

    /** Provider-status listing for UIs (Python provider_status parity). */
    fun status(): List<JSONObject> =
        PRESETS.map { p ->
            JSONObject()
                .put("id", p.id)
                .put("label", p.label)
                .put("url", p.url)
                .put("model", p.model)
                .put("key_found", keyFor(p).isNotBlank())
        }

    private fun keyFor(p: ProviderPreset): String {
        val s = QuantSettings.state.value
        return if (p.id == s.aiProvider) s.aiApiKey.ifBlank { s.apiKey } else ""
    }
}
