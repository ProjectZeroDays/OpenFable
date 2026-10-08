package com.projectzerodays.quantumcli.education

import org.json.JSONArray
import org.json.JSONObject

/** Tooltip copy shown on an anchored control (id matches a registry key). */
data class ContentTip(
    val id: String,
    val title: String,
    val body: String,
    val anchor: String? = null,
)

/** One walkthrough step; [advanceOn] is the semantic event that completes it. */
data class WalkStep(
    val id: String,
    val title: String,
    val body: String,
    val advanceOn: String? = null,
)

/** A route-scoped walkthrough (route matches the screen that owns the steps). */
data class Walkthrough(
    val route: String,
    val steps: List<WalkStep>,
) {
    companion object {
        fun fromJson(route: String, o: JSONObject): Walkthrough {
            val arr: JSONArray = o.getJSONArray("steps")
            val steps = (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                WalkStep(
                    id = s.getString("id"),
                    title = s.getString("title"),
                    body = s.getString("body"),
                    advanceOn = s.optString("advanceOn").takeIf { it.isNotEmpty() },
                )
            }
            return Walkthrough(route, steps)
        }
    }
}
