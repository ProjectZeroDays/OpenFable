package com.projectzerodays.quantumcli.settings

import com.projectzerodays.quantumcli.ai.Providers

/**
 * The real settings registry — every entry mirrors an existing field of
 * `data.QuantSettings.SettingsData` with its actual default (cross-checked
 * by `SettingsRegistryTest` via the property getter named in [SettingDefinition.dataField]).
 *
 * The gate (enforced by tests + the tooltip linter): every definition needs
 * a default, a tooltip id that resolves in `assets/content/tooltips/`,
 * type-correct bounds/options, and a unique key.
 */
object SettingsRegistry {

    val definitions: List<SettingDefinition> = buildList {
        add(
            SettingDefinition(
                key = "ai.mode",
                type = SettingType.DROPDOWN,
                defaultValue = "deterministic",
                dropdownOptions = listOf(
                    "deterministic" to "Deterministic (offline, no ML)",
                    "local" to "Local on-device model",
                    "online" to "Online provider",
                ),
                tooltipId = "settings.ai.mode",
                dataField = "aiMode",
            ),
        )
        add(
            SettingDefinition(
                key = "ai.provider",
                type = SettingType.DROPDOWN,
                defaultValue = "abliteration",
                dropdownOptions = Providers.PRESETS.map { it.id to it.label },
                tooltipId = "settings.ai.provider",
                dataField = "aiProvider",
            ),
        )
        add(
            SettingDefinition(
                key = "ai.api_key",
                type = SettingType.PASSWORD,
                defaultValue = "",
                scope = SettingScope.MODULE,
                tooltipId = "settings.ai.api_key",
                dataField = "aiApiKey",
            ),
        )
        add(
            SettingDefinition(
                key = "ml.temperature",
                type = SettingType.SLIDER_FLOAT,
                defaultValue = 0.8f,
                minF = 0.0f,
                maxF = 2.0f,
                tooltipId = "settings.ml.temperature",
                dataField = "mlTemperature",
            ),
        )
        add(
            SettingDefinition(
                key = "ml.top_k",
                type = SettingType.SLIDER_INT,
                defaultValue = 40,
                min = 1,
                max = 100,
                tooltipId = "settings.ml.top_k",
                dataField = "mlTopK",
            ),
        )
        add(
            SettingDefinition(
                key = "ml.max_tokens",
                type = SettingType.SLIDER_INT,
                defaultValue = 1024,
                min = 64,
                max = 4096,
                runtimeMutable = false, // engine must reload to apply
                tooltipId = "settings.ml.max_tokens",
                dataField = "mlMaxTokens",
            ),
        )
        add(
            SettingDefinition(
                key = "ml.telemetry",
                type = SettingType.SWITCH,
                defaultValue = true,
                tooltipId = "settings.ml.telemetry",
                dataField = "mlTelemetry",
            ),
        )
        add(
            SettingDefinition(
                key = "general.autostart",
                type = SettingType.SWITCH,
                defaultValue = true,
                tooltipId = "settings.general.autostart",
                dataField = "autostart",
            ),
        )
        add(
            SettingDefinition(
                key = "overlord.aggression",
                type = SettingType.DROPDOWN,
                defaultValue = "balanced",
                dropdownOptions = listOf(
                    "balanced" to "Balanced",
                    "aggressive" to "Aggressive",
                    "patient" to "Patient",
                ),
                tooltipId = "settings.overlord.aggression",
                dataField = "overlordAggression",
            ),
        )
        add(
            SettingDefinition(
                key = "privacy.clipboard_clear_sec",
                type = SettingType.SLIDER_INT,
                defaultValue = 30,
                min = 0,
                max = 300,
                tooltipId = "settings.privacy.clipboard_clear_sec",
                dataField = "clipboardClearSec",
            ),
        )
        add(
            SettingDefinition(
                key = "updates.hotfix_channel",
                type = SettingType.TEXT,
                defaultValue = "",
                tooltipId = "settings.updates.hotfix_channel",
                dataField = "hotFixChannel",
            ),
        )
        add(
            SettingDefinition(
                key = "c2.mode",
                type = SettingType.DROPDOWN,
                defaultValue = "self_host",
                dropdownOptions = listOf(
                    "self_host" to "Self-Host (on-device)",
                    "remote_connect" to "Remote-Connect (external)",
                ),
                tooltipId = "settings.c2.mode",
                dataField = "c2Mode",
            ),
        )
        add(
            SettingDefinition(
                key = "c2.remote_host",
                type = SettingType.TEXT,
                defaultValue = "",
                tooltipId = "settings.c2.remote_host",
                dataField = "c2RemoteHost",
            ),
        )
        add(
            SettingDefinition(
                key = "c2.destination_gate_shown",
                type = SettingType.SWITCH,
                defaultValue = false,
                tooltipId = "settings.c2.destination_gate_shown",
                dataField = "c2DestinationGateShown",
            ),
        )
    }

    private val byKey: Map<String, SettingDefinition> = definitions.associateBy { it.key }

    operator fun get(key: String): SettingDefinition? = byKey[key]

    fun require(key: String): SettingDefinition =
        byKey[key] ?: error("Unregistered setting: $key")

    val allTooltipIds: Set<String> get() = definitions.map { it.tooltipId }.toSet()
}
