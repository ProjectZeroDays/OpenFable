package com.projectzerodays.quantumcli.widgetengine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.projectzerodays.quantumcli.education.TooltipContent
import com.projectzerodays.quantumcli.education.TooltipRegistry
import com.projectzerodays.quantumcli.ui.theme.Cyan
import com.projectzerodays.quantumcli.ui.theme.Muted
import com.projectzerodays.quantumcli.ui.theme.QuantumTypography
import com.projectzerodays.quantumcli.ui.theme.Softest
import com.projectzerodays.quantumcli.ui.theme.Warn

/**
 * ℹ️ chip that opens the tooltip's full bottom sheet. Content comes from
 * [TooltipRegistry] (versioned asset JSON). A missing tooltip degrades
 * honestly to a muted "content missing" chip — the CI linter gate
 * (`TooltipPipelineTest`) is what prevents that from shipping, and the
 * message tells an operator exactly what to run.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipChip(tooltipId: String) {
    var open by remember { mutableStateOf(false) }
    val tooltip = remember(tooltipId) { TooltipRegistry[tooltipId] }

    IconButton(onClick = { open = true }) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = "Help: $tooltipId",
            tint = if (tooltip == null) Warn else Cyan,
        )
    }

    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            containerColor = Color(0xFF12151C),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                if (tooltip == null) {
                    Text(
                        "Content missing",
                        style = QuantumTypography.titleMedium,
                        color = Warn,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "No tooltip entry for '$tooltipId'. This is a content gap — " +
                            "the tooltip linter fails CI when a registered widget is " +
                            "missing its entry, so this should never appear in a release.",
                        style = QuantumTypography.bodySmall,
                        color = Muted,
                    )
                } else {
                    Text(tooltip.title, style = QuantumTypography.titleMedium, color = Cyan)
                    Spacer(Modifier.height(8.dp))
                    Text(tooltip.howTo, style = QuantumTypography.bodySmall, color = Softest)

                    if (tooltip.troubleshooting.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "TROUBLESHOOTING",
                            style = QuantumTypography.labelMedium,
                            color = Warn,
                        )
                        tooltip.troubleshooting.forEach { t ->
                            Spacer(Modifier.height(8.dp))
                            Column {
                                Text(t.symptom, style = QuantumTypography.labelLarge, color = Softest)
                                Text(
                                    "cause: ${t.cause}",
                                    style = QuantumTypography.bodySmall,
                                    color = Muted,
                                )
                                Text(
                                    "fix: ${t.fix}",
                                    style = QuantumTypography.bodySmall,
                                    color = Cyan,
                                )
                            }
                        }
                    }

                    if (tooltip.relatedTooltips.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "RELATED: " + tooltip.relatedTooltips.joinToString(", "),
                            style = QuantumTypography.labelSmall,
                            color = Muted,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    TextButton(onClick = { open = false }) { Text("Close", color = Cyan) }
                }
            }
        }
    }
}

/** Small colored dot used as the status light in widget title bars. */
@Composable
fun StatusLight(color: Color) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(8.dp)
            .background(color, CircleShape),
    )
}
