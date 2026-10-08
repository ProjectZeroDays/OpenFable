package com.projectzerodays.quantumcli.education

import org.json.JSONObject

/**
 * Walkthrough (guided tour) content — versioned JSON under
 * `assets/content/walkthroughs/` JSON files. The engine that spotlights targets
 * and consumes `advanceOn` events is a later phase; the CONTENT and its
 * tooltip-id gate land now so every step resolves against real tooltip files.
 */
data class WalkthroughContent(
    val source: String,
    val route: String,
    val version: Int,
    val autoStartOnFirstVisit: Boolean,
    val steps: List<Step>,
) {
    data class Step(
        val targetTooltipId: String,
        val title: String,
        val description: String,
        val gesture: String,
        val advanceOn: String?,
    )

    companion object {
        fun parse(source: String, json: String): WalkthroughContent {
            val root = JSONObject(json)
            val steps = buildList {
                val arr = root.optJSONArray("steps") ?: return@buildList
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    add(
                        Step(
                            targetTooltipId = s.getString("targetTooltipId"),
                            title = s.optString("title"),
                            description = s.optString("description"),
                            gesture = s.optString("gesture", "tap"),
                            advanceOn = if (s.has("advanceOn")) s.getString("advanceOn") else null,
                        ),
                    )
                }
            }
            return WalkthroughContent(
                source = source,
                route = root.optString("route", source),
                version = root.optInt("version", 0),
                autoStartOnFirstVisit = root.optBoolean("autoStartOnFirstVisit", false),
                steps = steps,
            )
        }
    }
}
