package com.projectzerodays.quantumcli.education

/**
 * Tooltip content model — one authoring surface consumed by the tooltip
 * registry (in-app chips/sheets), the CI linter gate, and the auto-doc job.
 *
 * Content itself lives in versioned JSON under `assets/content/tooltips/`
 * (hot-fixable via the signed content channel); this type is the parsed form.
 */
data class TooltipContent(
    val id: String,
    val title: String,
    val howTo: String,
    val troubleshooting: List<Trouble> = emptyList(),
    val relatedTooltips: List<String> = emptyList(),
) {
    data class Trouble(val symptom: String, val cause: String, val fix: String)

    companion object {
        /** One-paragraph tooltip (settings registry style). */
        fun brief(id: String, title: String, howTo: String): TooltipContent =
            TooltipContent(id = id, title = title, howTo = howTo)

        /** Tooltip with a troubleshooting table. */
        fun troubleshoot(
            id: String,
            title: String,
            howTo: String,
            steps: List<Triple<String, String, String>>,
        ): TooltipContent = TooltipContent(
            id = id,
            title = title,
            howTo = howTo,
            troubleshooting = steps.map { (symptom, cause, fix) -> Trouble(symptom, cause, fix) },
        )
    }
}
