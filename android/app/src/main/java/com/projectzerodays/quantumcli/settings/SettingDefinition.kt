package com.projectzerodays.quantumcli.settings

/**
 * Typed setting schema — the single source of truth that drives validation,
 * the settings UI (later sprint), docs generation, and the tooltip linter
 * gate. Deviation from the master-doc skeleton, documented honestly:
 * - [SettingDefinition.tooltipId] is a string id instead of an embedded
 *   [com.projectzerodays.quantumcli.education.TooltipContent] because tooltip
 *   content ships in versioned JSON (hot-fixable) and is resolved through
 *   [com.projectzerodays.quantumcli.education.TooltipRegistry].
 * - float ranges use [minF]/[maxF] (the Int-only range in the skeleton
 *   cannot express temperature-style settings).
 * - [dataField] names the matching property on [com.projectzerodays.quantumcli
 *   .data.QuantSettings.SettingsData] so the registry test can cross-check
 *   every declared default against the real runtime default.
 *
 * `runtimeMutable = false` means changing the value requires Apply/Restart
 * semantics (evaluate → mark pending restart) rather than taking effect
 * on the next read.
 */
enum class SettingType { SWITCH, SLIDER_INT, SLIDER_FLOAT, DROPDOWN, TEXT, PASSWORD, TIME_INTERVAL, ACTION }

enum class SettingScope { GLOBAL, DASHBOARD, MODULE }

data class SettingDefinition(
    val key: String,
    val type: SettingType,
    val defaultValue: Any,
    val scope: SettingScope = SettingScope.GLOBAL,
    val runtimeMutable: Boolean = true,
    val min: Int? = null,
    val max: Int? = null,
    val minF: Float? = null,
    val maxF: Float? = null,
    val dropdownOptions: List<Pair<String, String>> = emptyList(),
    val tooltipId: String,
    val dataField: String? = null,
)
