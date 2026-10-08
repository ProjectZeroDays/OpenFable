package com.projectzerodays.quantumcli.widgetengine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.projectzerodays.quantumcli.ui.theme.Cyan
import com.projectzerodays.quantumcli.ui.theme.Danger
import com.projectzerodays.quantumcli.ui.theme.Muted
import com.projectzerodays.quantumcli.ui.theme.QuantumTypography
import com.projectzerodays.quantumcli.ui.theme.Softest
import com.projectzerodays.quantumcli.ui.theme.Warn
import com.projectzerodays.quantumcli.ui.theme.quantumPanel

/**
 * Universal widget container: title bar (icon + name + status light + ℹ️
 * tooltip chip + overflow slot), the widget body, and a status footer.
 *
 * [RecoveryBoundary] wraps the body: a widget that throws while composing is
 * retried twice, then replaced by a recovery card — one bad widget never
 * kills the dashboard, and the failure is the source of the healing
 * daemon's `WIDGET_CRASH` failure class. Honest limitation (documented in
 * [RecoveryBoundary]): this catches synchronous composition exceptions;
 * render-thread/native crashes still go through the app-level uncaught
 * handler (wired in a later PR).
 */
@Composable
fun WidgetShell(
    widgetId: String,
    title: String,
    icon: ImageVector,
    moduleId: String = "",
    status: WidgetStatus = WidgetStatus(),
    overflow: @Composable () -> Unit = {},
    body: @Composable () -> Unit,
) {
    RecoveryBoundary(widgetId = widgetId, moduleId = moduleId, fallbackTitle = title) {
        Column(
            Modifier
                .fillMaxWidth()
                .quantumPanel(RoundedCornerShape(14.dp))
                .semantics { contentDescription = "$title widget" },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(icon, null, tint = Cyan)
                Spacer(Modifier.height(0.dp))
                Text(
                    title,
                    style = QuantumTypography.titleSmall,
                    color = Softest,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                StatusLight(status.color())
                Spacer(Modifier.padding(horizontal = 3.dp))
                TooltipChip(tooltipId = widgetId)
                overflow()
            }
            body()
            WidgetStatusFooter(status, moduleId)
        }
    }
}

@Composable
private fun WidgetStatusFooter(status: WidgetStatus, moduleId: String) {
    if (status.detail == null && moduleId.isEmpty()) return
    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        status.detail?.let {
            Text(
                it,
                style = QuantumTypography.labelSmall,
                color = when (status.level) {
                    WidgetStatus.Level.OK -> Muted
                    WidgetStatus.Level.DEGRADED -> Warn
                    WidgetStatus.Level.UNAVAILABLE -> Muted
                    WidgetStatus.Level.ERROR -> Danger
                },
            )
        }
        if (moduleId.isNotEmpty()) {
            Text("module: $moduleId", style = QuantumTypography.labelSmall, color = Muted)
        }
    }
}

/**
 * Per-widget exception boundary: retries composition twice, then shows the
 * recovery card. Catches synchronous composition exceptions only (see class
 * doc on [WidgetShell]) — never fakes a healthy widget after the limit.
 */
@Composable
fun RecoveryBoundary(
    widgetId: String,
    moduleId: String,
    fallbackTitle: String = widgetId,
    content: @Composable () -> Unit,
) {
    var failure by remember { mutableStateOf<String?>(null) }
    var attempts by rememberSaveable { mutableIntStateOf(0) }

    if (failure != null && attempts >= 3) {
        RecoveryCard(
            widgetId = widgetId,
            moduleId = moduleId,
            title = fallbackTitle,
            error = failure ?: "unknown error",
            onRetry = {
                failure = null
                attempts = 0
            },
        )
        return
    }

    val result = runCatching { content() }
    result.exceptionOrNull()?.let { e ->
        // state write during composition schedules the retry recomposition
        failure = e.message ?: e.javaClass.simpleName
        attempts = attempts + 1
    }
}

@Composable
private fun RecoveryCard(
    widgetId: String,
    moduleId: String,
    title: String,
    error: String,
    onRetry: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .quantumPanel(RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FavoriteBorder, null, tint = Warn)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text(
                "$title — recovery",
                style = QuantumTypography.titleSmall,
                color = Warn,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "This widget stopped rendering after 3 attempts instead of taking " +
                "the dashboard down. Failure class: WIDGET_CRASH.",
            style = QuantumTypography.bodySmall,
            color = Softest,
        )
        Text(
            "error: $error",
            style = QuantumTypography.labelSmall,
            color = Danger,
        )
        if (moduleId.isNotEmpty()) {
            Text(
                "module: $moduleId — check its pre-flight status in Diagnostics",
                style = QuantumTypography.labelSmall,
                color = Muted,
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRetry) { Text("RETRY") }
    }
}

/** Simple column scaffold for dashboards: title + optional telemetry slot + content. */
@Composable
fun DashboardScaffold(
    route: String,
    title: String,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    onLockToggle: ((Boolean) -> Unit)? = null,
    telemetryHeader: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        ) {
            Text(title, style = QuantumTypography.titleLarge, color = Cyan)
            Spacer(Modifier.weight(1f))
            if (onLockToggle != null) {
                OutlinedButton(onClick = { onLockToggle(!locked) }) {
                    Text(if (locked) "LOCKED" else "EDIT")
                }
            }
        }
        telemetryHeader?.invoke()
        if (!locked && onLockToggle != null) {
            Text(
                "edit mode — layout persistence + drag/drop grid (WidgetLayouts) " +
                    "arrive with WidgetGrid in Sprint 1; $route keeps its default order until then.",
                style = QuantumTypography.labelSmall,
                color = Muted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        content()
    }
}
