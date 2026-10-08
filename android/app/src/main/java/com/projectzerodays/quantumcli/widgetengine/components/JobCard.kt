package com.projectzerodays.quantumcli.widgetengine.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectzerodays.quantumcli.ui.theme.Ok
import com.projectzerodays.quantumcli.ui.theme.Danger
import com.projectzerodays.quantumcli.ui.theme.Muted
import com.projectzerodays.quantumcli.ui.theme.SoftCyan
import com.projectzerodays.quantumcli.ui.theme.Cyan

/** Lifecycle of the universal job card. */
enum class JobPhase { QUEUED, RUNNING, DONE, FAILED, DEGRADED }

/**
 * Immutable snapshot rendered by [JobCard]. `tail` is the newest-first log
 * window (rendered newest-last, capped at 3 lines), `progress` null renders
 * the indeterminate indicator, `verdict` is an optional result badge
 * (e.g. "verified", "cracked", "compiled").
 */
data class JobCardState(
    val phase: JobPhase,
    val title: String,
    val etaText: String? = null,
    val progress: Float? = null,
    val tail: List<String> = emptyList(),
    val cancelable: Boolean = false,
    val lastLine: String? = null,
    val verdict: String? = null,
    val linkedTo: String? = null,
)

/**
 * The universal job UX: every long-running operation in the app renders as
 * one of these — same shape, same controls, same streaming tail.
 */
@Composable
fun JobCard(
    state: JobCardState,
    modifier: Modifier = Modifier,
    onOpenLogs: () -> Unit = {},
    onStop: () -> Unit = {},
) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (state.phase) {
                        JobPhase.QUEUED -> Icons.Outlined.Pause
                        JobPhase.RUNNING -> Icons.Outlined.PlayArrow
                        JobPhase.DONE -> Icons.Outlined.CheckCircle
                        JobPhase.FAILED, JobPhase.DEGRADED -> Icons.Outlined.Error
                    },
                    contentDescription = state.phase.name,
                    tint = phaseTint(state.phase),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.etaText?.let {
                    Text(
                        "~$it",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                OverflowMenu(state, onOpenLogs, onStop)
            }

            state.progress?.let { p ->
                LinearProgressIndicator(
                    progress = { p.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = phaseTint(state.phase),
                )
            } ?: if (state.phase == JobPhase.RUNNING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Cyan)
            } else {
                Unit
            }

            if (state.tail.isNotEmpty()) StreamingText(state.tail)

            Row(verticalAlignment = Alignment.CenterVertically) {
                state.linkedTo?.let {
                    AssistChip(onClick = {}, label = { Text(it, style = MaterialTheme.typography.labelSmall) })
                    Spacer(Modifier.width(8.dp))
                }
                state.verdict?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = Ok,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverflowMenu(state: JobCardState, onOpenLogs: () -> Unit, onStop: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.Description, contentDescription = "job actions")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        if (state.cancelable && state.phase == JobPhase.RUNNING) {
            DropdownMenuItem(
                text = { Text("Stop", color = Danger) },
                leadingIcon = { Icon(Icons.Outlined.Stop, contentDescription = null, tint = Danger) },
                onClick = { open = false; onStop() },
            )
        }
        DropdownMenuItem(
            text = { Text("Open logs") },
            onClick = { open = false; onOpenLogs() },
        )
        state.lastLine?.let { line ->
            DropdownMenuItem(
                text = { Text("Copy last line") },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                onClick = { open = false; ClipboardGuard.copyAndScheduleClear(line) },
            )
        }
    }
}

/** Capped, monospace, auto-scrolling log tail shared by all job surfaces. */
@Composable
fun StreamingText(lines: List<String>, maxLines: Int = 3) {
    Text(
        lines.takeLast(maxLines).joinToString("\n"),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = Muted,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 56.dp),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun phaseTint(phase: JobPhase): Color = when (phase) {
    JobPhase.QUEUED -> Muted
    JobPhase.RUNNING -> Cyan
    JobPhase.DONE -> Ok
    JobPhase.DEGRADED -> SoftCyan
    JobPhase.FAILED -> Danger
}
