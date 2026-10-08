package com.projectzerodays.quantumcli.widgetengine.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * The copy chip everything reuses. Copies through [ClipboardGuard] so the
 * clipboard auto-clears after the operator's TTL, ticks the haptic, and
 * flips to a check mark for a beat. Copying a secret the guard already
 * tracks re-arms the same TTL rather than stacking timers.
 */
@Composable
fun CopyChip(
    value: String,
    label: String? = null,
    modifier: Modifier = Modifier,
    onCopied: (ttlSeconds: Int) -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }

    AssistChip(
        onClick = {
            val ttl = ClipboardGuard.copyAndScheduleClear(value)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            copied = true
            onCopied(ttl)
        },
        label = {
            Text(
                when {
                    copied -> "Copied"
                    label != null -> label
                    else -> "Copy"
                },
                style = MaterialTheme.typography.labelSmall,
            )
        },
        leadingIcon = {
            Icon(
                if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                contentDescription = null,
                Modifier.padding(end = 2.dp),
            )
        },
        modifier = modifier,
    )
}
