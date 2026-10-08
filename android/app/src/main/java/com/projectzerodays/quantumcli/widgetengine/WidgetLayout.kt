package com.projectzerodays.quantumcli.widgetengine

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * A route-scoped widget ordering. `moved` is the single mutation the grid
 * performs on a drag drop; persistence is per-route in app files so the
 * operator's arrangement survives restarts.
 */
data class WidgetLayout(
    val route: String,
    val orderedIds: List<String>,
) {
    fun moved(fromIndex: Int, toIndex: Int): WidgetLayout {
        if (fromIndex !in orderedIds.indices || toIndex !in orderedIds.indices) return this
        val ids = orderedIds.toMutableList()
        ids.add(toIndex, ids.removeAt(fromIndex))
        return copy(orderedIds = ids)
    }

    /** Move widget [id] to the position currently occupied by [targetId]. */
    fun moveTo(id: String, targetId: String): WidgetLayout {
        val from = orderedIds.indexOf(id)
        val to = orderedIds.indexOf(targetId)
        return if (id == targetId || from < 0 || to < 0) this else moved(from, to)
    }
}

/** Per-route persistence for [WidgetLayout] + lock state. */
object WidgetLayoutStore {

    private lateinit var dir: File

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "widget_layouts").apply { mkdirs() }
    }

    fun load(route: String, fallback: List<String>): List<String> {
        if (!::dir.isInitialized) return fallback
        val f = layoutFile(route)
        if (!f.exists()) return fallback
        val saved = runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotEmpty() } }
        }.getOrElse { emptyList() }
        if (saved.isEmpty()) return fallback
        // Reconcile against current defaults: widgets added since the last
        // save append at the end; removed widgets drop out.
        return saved.filter { it in fallback } + fallback.filter { it !in saved }
    }

    fun save(layout: WidgetLayout) {
        if (!::dir.isInitialized) return
        val arr = JSONArray()
        layout.orderedIds.forEach { arr.put(it) }
        runCatching { layoutFile(layout.route).writeText(arr.toString()) }
    }

    fun isLocked(route: String): Boolean =
        ::dir.isInitialized && dir.resolve("lock_$route").exists()

    fun setLocked(route: String, locked: Boolean) {
        if (!::dir.isInitialized) return
        val f = dir.resolve("lock_$route")
        if (locked) f.createNewFile() else f.delete()
    }

    private fun layoutFile(route: String) = File(dir, "layout_$route.json")
}
