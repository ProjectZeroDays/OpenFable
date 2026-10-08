package com.projectzerodays.quantumcli.education

import com.projectzerodays.quantumcli.settings.SettingsRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Content-pipeline gate — parses the REAL shipped assets and runs the
 * TooltipLinter over them (this is the CI enforcement for Artifact C; the
 * CI workflow runs `:app:testFullDebugUnitTest`, so this test is the gate).
 */
class TooltipPipelineTest {

    private fun asset(path: String): String {
        val candidates = listOf(
            "src/main/assets/$path",
            "app/src/main/assets/$path",
            "../app/src/main/assets/$path",
        )
        candidates.forEach { c ->
            val f = File(c)
            if (f.isFile) return f.readText(Charsets.UTF_8)
        }
        throw AssertionError("asset not found: $path (tried $candidates)")
    }

    private fun tooltipFiles(): List<TooltipRegistry.TooltipFile> =
        listOf("home", "credentials", "exploit", "settings").map {
            TooltipRegistry.parse("$it.json", asset("content/tooltips/$it.json"))
        }

    private fun walkthroughs(): List<WalkthroughContent> =
        listOf("exploit", "home", "credentials").map {
            WalkthroughContent.parse("$it.json", asset("content/walkthroughs/$it.json"))
        }

    @Test
    fun shippedTooltipFilesParseWithContent() {
        val files = tooltipFiles()
        files.forEach { f ->
            assertTrue("${f.source} has version", f.version >= 1)
            assertTrue("${f.source} has tooltips", f.tooltips.isNotEmpty())
            f.tooltips.values.forEach { t ->
                assertTrue("${t.id} title", t.title.isNotBlank())
                assertTrue("${t.id} howTo", t.howTo.isNotBlank())
            }
        }
        TooltipRegistry.install(files)
        assertNotNull(TooltipRegistry["home.root.status"])
        assertNotNull(TooltipRegistry["settings.ai.mode"])
        assertNotNull(TooltipRegistry["exploit.adb.hunter"])
        assertNotNull(TooltipRegistry["creds.browser.table"])
        TooltipRegistry.clear()
    }

    @Test
    fun walkthroughStepsAllResolveAgainstTooltipFiles() {
        val wts = walkthroughs()
        assertEquals(3, wts.size)
        wts.forEach { w ->
            assertTrue("${w.route} version", w.version >= 1)
            assertTrue("${w.route} steps", w.steps.isNotEmpty())
            w.steps.forEach { s ->
                assertTrue("${w.route} step title", s.title.isNotBlank())
                assertTrue("${w.route} step gesture", s.gesture in setOf("tap", "swipe", "long-press", "type"))
            }
        }
        val report = TooltipLinter.check(
            widgetIds = emptySet(),
            tooltipFiles = tooltipFiles(),
            walkthroughs = wts,
            settingTooltipIds = SettingsRegistry.allTooltipIds,
        )
        assertTrue(report.describe(), report.passed)
        assertTrue(report.danglingWalkthroughTargets.isEmpty())
    }

    @Test
    fun everySettingDefinitionResolvesItsTooltip() {
        val report = TooltipLinter.check(
            widgetIds = emptySet(),
            tooltipFiles = tooltipFiles(),
            walkthroughs = walkthroughs(),
            settingTooltipIds = SettingsRegistry.allTooltipIds,
        )
        assertTrue(report.describe(), report.passed)
        assertTrue(report.missingSettingTooltipIds.isEmpty())
    }

    @Test
    fun linterCatchesMissingWidgetTooltip() {
        val report = TooltipLinter.check(
            widgetIds = setOf("home.root.status", "widget.not.shipped.yet"),
            tooltipFiles = tooltipFiles(),
            walkthroughs = walkthroughs(),
            settingTooltipIds = emptySet(),
        )
        assertFalse(report.passed)
        assertEquals(setOf("widget.not.shipped.yet"), report.missingWidgetTooltips)
        assertTrue(report.describe().contains("FAILED"))
    }

    @Test
    fun linterCatchesDanglingWalkthroughTarget() {
        val fake = WalkthroughContent(
            source = "fake.json", route = "fake", version = 1,
            autoStartOnFirstVisit = false,
            steps = listOf(
                WalkthroughContent.Step(
                    targetTooltipId = "ghost.tooltip", title = "t", description = "d",
                    gesture = "tap", advanceOn = null,
                ),
            ),
        )
        val report = TooltipLinter.check(
            widgetIds = emptySet(),
            tooltipFiles = tooltipFiles(),
            walkthroughs = walkthroughs() + fake,
            settingTooltipIds = emptySet(),
        )
        assertFalse(report.passed)
        assertEquals(setOf("ghost.tooltip"), report.danglingWalkthroughTargets)
    }

    @Test
    fun duplicateIdsAcrossFilesAreBlocking() {
        val a = TooltipRegistry.parse(
            "a.json",
            """{"version":1,"tooltips":{"shared.id":{"title":"A","howTo":"a"}}}""",
        )
        val b = TooltipRegistry.parse(
            "b.json",
            """{"version":1,"tooltips":{"shared.id":{"title":"B","howTo":"b"}}}""",
        )
        val report = TooltipLinter.check(
            widgetIds = emptySet(),
            tooltipFiles = listOf(a, b),
            walkthroughs = emptyList(),
            settingTooltipIds = emptySet(),
        )
        assertFalse(report.passed)
        assertEquals(setOf("shared.id"), report.duplicateIds)
    }

    @Test
    fun danglingRelatedIsWarningOnly() {
        val report = TooltipLinter.check(
            widgetIds = emptySet(),
            tooltipFiles = tooltipFiles(),
            walkthroughs = walkthroughs(),
            settingTooltipIds = SettingsRegistry.allTooltipIds,
        )
        // home.json intentionally links forward to widgets from later sprints
        assertTrue(report.passed)
        // (diag.feature.matrix / rat.map.full / ai.provider.grid are future ids)
        assertTrue(report.danglingRelated.contains("diag.feature.matrix"))
    }
}
