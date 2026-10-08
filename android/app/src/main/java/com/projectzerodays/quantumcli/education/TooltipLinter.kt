package com.projectzerodays.quantumcli.education

/**
 * Coverage gate for the content pipeline. Runs in CI as part of the JVM test
 * suite (`TooltipPipelineTest`) — the honest equivalent of the planned
 * `:app:lintTooltips` Gradle task, because CI already runs unit tests and a
 * custom task would duplicate the same check.
 *
 * Blocking rules (fail the build):
 *  1. every registered widget id has tooltip content,
 *  2. every setting definition's tooltip id resolves,
 *  3. every walkthrough step targets an existing tooltip id,
 *  4. no duplicate tooltip ids across files (ambiguous content).
 * Non-blocking: dangling `relatedTooltips` links (reported as warnings —
 * related ids may point at widgets scheduled for a later sprint).
 */
object TooltipLinter {

    data class Report(
        val missingWidgetTooltips: Set<String>,
        val missingSettingTooltipIds: Set<String>,
        val danglingWalkthroughTargets: Set<String>,
        val duplicateIds: Set<String>,
        val danglingRelated: Set<String>,
    ) {
        val blockingProblems: List<String> get() = buildList {
            if (missingWidgetTooltips.isNotEmpty()) {
                add("widgets missing tooltips: $missingWidgetTooltips")
            }
            if (missingSettingTooltipIds.isNotEmpty()) {
                add("settings referencing missing tooltips: $missingSettingTooltipIds")
            }
            if (danglingWalkthroughTargets.isNotEmpty()) {
                add("walkthrough steps targeting missing tooltips: $danglingWalkthroughTargets")
            }
            if (duplicateIds.isNotEmpty()) {
                add("duplicate tooltip ids across files: $duplicateIds")
            }
        }

        val passed: Boolean get() = blockingProblems.isEmpty()

        fun describe(): String = buildString {
            append(if (passed) "tooltip lint PASSED" else "tooltip lint FAILED")
            blockingProblems.forEach { append("; it") }
            if (danglingRelated.isNotEmpty()) append(" (warnings: related $danglingRelated)")
        }
    }

    fun check(
        widgetIds: Set<String>,
        tooltipFiles: List<TooltipRegistry.TooltipFile>,
        walkthroughs: List<WalkthroughContent>,
        settingTooltipIds: Set<String>,
    ): Report {
        val seen = HashMap<String, String>()
        val dupes = LinkedHashSet<String>()
        tooltipFiles.forEach { file ->
            file.tooltips.keys.forEach { id ->
                val prev = seen.put(id, file.source)
                if (prev != null && prev != file.source) dupes.add(id)
            }
        }
        val covered = tooltipFiles.flatMap { it.tooltips.keys }.toSet()

        return Report(
            missingWidgetTooltips = (widgetIds - covered).sorted().toSet(),
            missingSettingTooltipIds = (settingTooltipIds - covered).sorted().toSet(),
            danglingWalkthroughTargets = walkthroughs
                .flatMap { w -> w.steps.map { it.targetTooltipId } }
                .filter { it !in covered }
                .toSet()
                .sorted()
                .toSet(),
            duplicateIds = dupes,
            danglingRelated = tooltipFiles
                .flatMap { f -> f.tooltips.values }
                .flatMap { it.relatedTooltips }
                .filter { it !in covered }
                .toSet()
                .sorted()
                .toSet(),
        )
    }
}
