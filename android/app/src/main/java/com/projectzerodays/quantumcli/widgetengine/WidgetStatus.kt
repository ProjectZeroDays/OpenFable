package com.projectzerodays.quantumcli.widgetengine

import androidx.compose.ui.graphics.Color
import com.projectzerodays.quantumcli.ui.theme.Muted
import com.projectzerodays.quantumcli.ui.theme.Ok
import com.projectzerodays.quantumcli.ui.theme.Danger
import com.projectzerodays.quantumcli.ui.theme.Warn

/**
 * Widget health — drives the status light + footer in [WidgetShell].
 * `detail` is what the footer shows (e.g. a pre-flight message or freshness).
 */
data class WidgetStatus(
    val level: Level = Level.OK,
    val detail: String? = null,
) {
    enum class Level { OK, DEGRADED, UNAVAILABLE, ERROR }

    fun color(): Color = when (level) {
        Level.OK -> Ok
        Level.DEGRADED -> Warn
        Level.UNAVAILABLE -> Muted
        Level.ERROR -> Danger
    }

    fun label(): String = when (level) {
        Level.OK -> "OK"
        Level.DEGRADED -> "DEGRADED"
        Level.UNAVAILABLE -> "UNAVAILABLE"
        Level.ERROR -> "ERROR"
    }

    companion object {
        fun ok(detail: String? = null) = WidgetStatus(Level.OK, detail)
        fun degraded(detail: String? = null) = WidgetStatus(Level.DEGRADED, detail)
        fun unavailable(detail: String? = null) = WidgetStatus(Level.UNAVAILABLE, detail)
        fun error(detail: String? = null) = WidgetStatus(Level.ERROR, detail)
    }
}
