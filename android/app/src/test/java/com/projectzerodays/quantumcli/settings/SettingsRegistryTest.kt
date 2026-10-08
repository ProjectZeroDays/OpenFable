package com.projectzerodays.quantumcli.settings

import com.projectzerodays.quantumcli.data.SettingsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry gate: unique keys, type-correct bounds/options, and every
 * declared default cross-checked against the REAL runtime default on
 * `QuantSettings.SettingsData` (via the data-class getter named by
 * `dataField`) — the schema can never drift from the app's actual defaults.
 */
class SettingsRegistryTest {

    private val dataInstance = SettingsData()

    private fun actual(field: String): Any? {
        val getter = SettingsData::class.java.getMethod(
            "get" + field.replaceFirstChar { it.uppercaseChar() },
        )
        return getter.invoke(dataInstance)
    }

    @Test
    fun keysAreUniqueAndWellFormed() {
        val keys = SettingsRegistry.definitions.map { it.key }
        assertEquals(keys.distinct(), keys)
        keys.forEach { assertTrue("key format: $it", it.matches(Regex("[a-z0-9]+(\\.[a-z0-9_]+)+"))) }
    }

    @Test
    fun everyDefinitionHasDefaultTooltipScopeAndMatchingType() {
        SettingsRegistry.definitions.forEach { def ->
            assertNotNull("${def.key} default must not be null", def.defaultValue)
            assertTrue("${def.key} tooltip id", def.tooltipId.isNotBlank())
            when (def.type) {
                SettingType.SWITCH -> assertTrue(
                    "${def.key} SWITCH default must be Boolean",
                    def.defaultValue is Boolean,
                )
                SettingType.SLIDER_INT -> {
                    assertTrue("${def.key} Int default", def.defaultValue is Int)
                    assertNotNull("${def.key} needs min", def.min)
                    assertNotNull("${def.key} needs max", def.max)
                    val v = def.defaultValue as Int
                    assertTrue(
                        "${def.key} default $v in [${def.min}, ${def.max}]",
                        v >= def.min!! && v <= def.max!!,
                    )
                }
                SettingType.SLIDER_FLOAT -> {
                    assertTrue("${def.key} Float default", def.defaultValue is Float)
                    assertNotNull("${def.key} needs minF", def.minF)
                    assertNotNull("${def.key} needs maxF", def.maxF)
                    val v = def.defaultValue as Float
                    assertTrue(
                        "${def.key} default $v in [${minOf(def.minF!!, def.maxF!!)}, ${maxOf(def.minF!!, def.maxF!!)}]",
                        v >= minOf(def.minF!!, def.maxF!!) && v <= maxOf(def.minF!!, def.maxF!!),
                    )
                }
                SettingType.DROPDOWN -> {
                    assertTrue(
                        "${def.key} dropdown needs options",
                        def.dropdownOptions.isNotEmpty(),
                    )
                    assertTrue(
                        "${def.key} default must be an option",
                        def.dropdownOptions.any { it.first == def.defaultValue },
                    )
                }
                else -> assertTrue("${def.key} String default", def.defaultValue is String)
            }
        }
    }

    @Test
    fun declaredDefaultsMatchRealSettingsDataDefaults() {
        val checked = SettingsRegistry.definitions.filter { it.dataField != null }
        assertTrue("registry should cover real fields", checked.size >= 10)
        checked.forEach { def ->
            assertEquals(
                "${def.key} (${def.dataField}) default drifted from SettingsData",
                def.defaultValue,
                actual(def.dataField!!),
            )
        }
    }

    @Test
    fun requireRejectsUnknownKeys() {
        assertNotNull(SettingsRegistry["ml.temperature"])
        try {
            SettingsRegistry.require("does.not.exist")
            throw AssertionError("expected error for unregistered key")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("does.not.exist"))
        }
    }

    @Test
    fun mlMaxTokensRequiresRestartSemantics() {
        // engine must reload to apply — runtimeMutable=false drives Apply/Restart UI
        val def = SettingsRegistry.require("ml.max_tokens")
        assertTrue(!def.runtimeMutable)
        val live = SettingsRegistry.require("ml.telemetry")
        assertTrue(live.runtimeMutable)
    }
}
