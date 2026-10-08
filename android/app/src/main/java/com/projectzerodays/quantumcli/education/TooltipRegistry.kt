package com.projectzerodays.quantumcli.education

import org.json.JSONObject

/**
 * Parses and serves the versioned tooltip JSON shipped in
 * `assets/content/tooltips/` JSON files. Parsing is pure (org.json) so the JVM
 * test suite can lint the real shipped files; [install] is called once at
 * app start (or after a signed hot-fix replaces the assets).
 */
object TooltipRegistry {

    data class TooltipFile(
        val source: String,
        val version: Int,
        val tooltips: Map<String, TooltipContent>,
    )

    private var byId: Map<String, TooltipContent> = emptyMap()

    /** Parse one tooltip file. Keys of `tooltips` are the ids. */
    fun parse(source: String, json: String): TooltipFile {
        val root = JSONObject(json)
        val version = root.optInt("version", 0)
        val obj = root.optJSONObject("tooltips") ?: JSONObject()
        val map = LinkedHashMap<String, TooltipContent>()
        for (key in obj.keys()) {
            val t = obj.getJSONObject(key)
            val troubleshooting = buildList {
                val arr = t.optJSONArray("troubleshooting")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val s = arr.getJSONObject(i)
                        add(
                            TooltipContent.Trouble(
                                symptom = s.optString("symptom"),
                                cause = s.optString("cause"),
                                fix = s.optString("fix"),
                            ),
                        )
                    }
                }
            }
            val related = buildList {
                val arr = t.optJSONArray("relatedTooltips")
                if (arr != null) {
                    for (i in 0 until arr.length()) add(arr.getString(i))
                }
            }
            map[key] = TooltipContent(
                id = key,
                title = t.optString("title", key),
                howTo = t.optString("howTo"),
                troubleshooting = troubleshooting,
                relatedTooltips = related,
            )
        }
        return TooltipFile(source = source, version = version, tooltips = map)
    }

    /** Merge-parse several files and serve them. Later files override ids. */
    fun install(files: List<TooltipFile>) {
        val merged = LinkedHashMap<String, TooltipContent>()
        files.forEach { merged.putAll(it.tooltips) }
        byId = merged
    }

    operator fun get(id: String): TooltipContent? = byId[id]

    fun ids(): Set<String> = byId.keys

    fun clear() {
        byId = emptyMap()
    }
}
