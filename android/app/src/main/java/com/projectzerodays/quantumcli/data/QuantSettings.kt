package com.projectzerodays.quantumcli.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class SettingsData(
    val c2Port: Int = 8443,
    val aiMode: String = "deterministic", // online | local | deterministic
    val apiUrl: String = "https://api.abliteration.ai/v1",
    val apiKey: String = "",
    val model: String = "abliterated-model-large-v2",
    val rootMode: Boolean = false,
    val autostart: Boolean = true,
    val overlordCameraOnly: Boolean = false,
    val overlordIntervalSec: Int = 45,
    val wizardDone: Boolean = false,
    val autoDownload: Boolean = true,
    val overlordAggression: String = "balanced", // balanced | aggressive | patient
    val overlordCycles: Int = 0,                 // 0 = infinite
    val overlordScope: String = "",              // blank = local /24; else CIDR
    val c2Domain: String = "",                   // GhostDNS / DoH operator domain
    val clipboardClearSec: Int = 30,             // clipboard hygiene TTL (CopyChip/MaskedSecretCell)
    val hotFixChannel: String = "",              // signed content hot-fix base URL; blank = disabled
    val aiProvider: String = "abliteration",     // Providers preset id (ai/Providers.kt)
    val aiApiKey: String = "",                   // per-provider key; blank = legacy apiKey (migration)
    // ---- ML dashboard (local MediaPipe inference controls) ----
    val mlTemperature: Float = 0.8f,             // sampling temperature 0.0..2.0
    val mlTopK: Int = 40,                        // top-K sampling (1..100)
    val mlMaxTokens: Int = 1024,                 // max output tokens (64..4096)
    val mlTelemetry: Boolean = true,             // record inference telemetry samples
    // ---- C2 destination gate ----
    val c2Mode: String = "self_host",            // "self_host" | "remote_connect"
    val c2RemoteHost: String = "",               // e.g. "http://192.168.1.100:8443"
    val c2DestinationGateShown: Boolean = false, // true once the gate has been dismissed
)

/**
 * App settings — persisted in EncryptedSharedPreferences (API key at rest is
 * ciphertext). Exposes a StateFlow snapshot so Compose pages recompose live.
 */
object QuantSettings {

    private lateinit var prefs: SharedPreferences

    private val _state = MutableStateFlow(SettingsData())
    val state: StateFlow<SettingsData> = _state.asStateFlow()

    fun init(context: Context) {
        prefs = try {
            val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "quantum_settings",
                alias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("quantum_settings_plain", Context.MODE_PRIVATE)
        }
        _state.value = load()
    }

    private fun load(): SettingsData = SettingsData(
        c2Port = prefs.getInt("c2_port", 8443),
        aiMode = prefs.getString("ai_mode", "deterministic") ?: "deterministic",
        apiUrl = prefs.getString(
            "ai_url",
            "https://api.abliteration.ai/v1"
        ) ?: "https://api.abliteration.ai/v1",
        apiKey = prefs.getString("ai_key", "") ?: "",
        model = prefs.getString(
            "ai_model",
            "abliterated-model-large-v2"
        ) ?: "abliterated-model-large-v2",
        rootMode = prefs.getBoolean("root_mode", false),
        autostart = prefs.getBoolean("autostart", true),
        overlordCameraOnly = prefs.getBoolean("overlord_camera_only", false),
        overlordIntervalSec = prefs.getInt("overlord_interval", 45),
        wizardDone = prefs.getBoolean("wizard_done", false),
        autoDownload = prefs.getBoolean("auto_download", true),
        overlordAggression = prefs.getString("overlord_aggression", "balanced") ?: "balanced",
        overlordCycles = prefs.getInt("overlord_cycles", 0),
        overlordScope = prefs.getString("overlord_scope", "") ?: "",
        c2Domain = prefs.getString("c2_domain", "") ?: "",
        clipboardClearSec = prefs.getInt("clipboard_clear_sec", 30),
        hotFixChannel = prefs.getString("hotfix_channel", "") ?: "",
        aiProvider = prefs.getString("ai_provider", "abliteration") ?: "abliteration",
        aiApiKey = prefs.getString("ai_api_key", "") ?: "",
        mlTemperature = prefs.getFloat("ml_temperature", 0.8f),
        mlTopK = prefs.getInt("ml_top_k", 40),
        mlMaxTokens = prefs.getInt("ml_max_tokens", 1024),
        mlTelemetry = prefs.getBoolean("ml_telemetry", true),
        c2Mode = prefs.getString("c2_mode", "self_host") ?: "self_host",
        c2RemoteHost = prefs.getString("c2_remote_host", "") ?: "",
        c2DestinationGateShown = prefs.getBoolean("c2_dest_gate_shown", false),
    )

    fun update(transform: (SettingsData) -> SettingsData) {
        val next = transform(_state.value)
        prefs.edit()
            .putInt("c2_port", next.c2Port)
            .putString("ai_mode", next.aiMode)
            .putString("ai_url", next.apiUrl)
            .putString("ai_key", next.apiKey)
            .putString("ai_model", next.model)
            .putBoolean("root_mode", next.rootMode)
            .putBoolean("autostart", next.autostart)
            .putBoolean("overlord_camera_only", next.overlordCameraOnly)
            .putInt("overlord_interval", next.overlordIntervalSec)
            .putBoolean("wizard_done", next.wizardDone)
            .putBoolean("auto_download", next.autoDownload)
            .putString("overlord_aggression", next.overlordAggression)
            .putInt("overlord_cycles", next.overlordCycles)
            .putString("overlord_scope", next.overlordScope)
            .putString("c2_domain", next.c2Domain)
            .putInt("clipboard_clear_sec", next.clipboardClearSec)
            .putString("hotfix_channel", next.hotFixChannel)
            .putString("ai_provider", next.aiProvider)
            .putString("ai_api_key", next.aiApiKey)
            .putFloat("ml_temperature", next.mlTemperature)
            .putInt("ml_top_k", next.mlTopK)
            .putInt("ml_max_tokens", next.mlMaxTokens)
            .putBoolean("ml_telemetry", next.mlTelemetry)
            .putString("c2_mode", next.c2Mode)
            .putString("c2_remote_host", next.c2RemoteHost)
            .putBoolean("c2_dest_gate_shown", next.c2DestinationGateShown)
            .apply()
        _state.value = next
    }

    /** MediaPipe LLM model discovery in the app files dir (.task or .bin). */
    fun localModelFile(): File? =
        com.projectzerodays.quantumcli.c2.C2State.filesRoot.listFiles()
            ?.firstOrNull {
                it.isFile && (it.name.endsWith(".task") || it.name.endsWith(".bin"))
            }
}
