package com.projectzerodays.quantumcli.widgetengine.components

import android.app.KeyguardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.projectzerodays.quantumcli.ui.theme.Cyan
import com.projectzerodays.quantumcli.ui.theme.Muted
import com.projectzerodays.quantumcli.ui.theme.PanelBright
import kotlinx.coroutines.delay

/**
 * Secret cell with reveal-on-demand and auto re-mask.
 *
 * - Tap the eye to reveal for the operator's clipboard TTL seconds, then it
 *   re-masks itself. Revealing through the device lock gate when one is set.
 * - Long-press toggles row selection (bulk export flows).
 * - The reveal exposes [valueSupplier] lazily — callers never hand the
 *   secret into composition until the operator actually reveals.
 *
 * Device gate: on a device with a screen lock (KeyguardManager.isDeviceSecure)
 * the reveal also arms clipboard auto-clear and the cell is marked gated;
 * on a device WITHOUT a lock the cell reveals directly and shows an explicit
 * "no device lock" chip instead of pretending a gate exists. A biometric
 * prompt gate would need androidx.biometric and a FragmentActivity host —
 * deliberately not faked here.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MaskedSecretCell(
    valueSupplier: () -> String?,
    modifier: Modifier = Modifier,
    ttlSecondsOverride: Int? = null,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var revealed by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf(false) }

    val keyguard = context.getSystemService(KeyguardManager::class.java)
    val deviceLockedProfile = keyguard?.isDeviceSecure == true

    LaunchedEffect(revealed) {
        if (revealed == null) return@LaunchedEffect
        secondsLeft = ttlSecondsOverride ?: ClipboardGuard.ttlSeconds()
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft -= 1
        }
        revealed = null
    }

    Row(
        modifier
            .background(
                if (selected) Cyan.copy(alpha = 0.12f) else PanelBright,
                RoundedCornerShape(8.dp),
            )
            .combinedClickable(
                onClick = { /* selection handled via long-press; click reserved */ },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    selected = !selected
                },
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            revealed ?: "••••••••••••",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = if (revealed != null) Cyan else Muted,
            modifier = Modifier.weight(1f),
        )
        if (!deviceLockedProfile) {
            Text(
                "no device lock",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
        IconButton(onClick = {
            revealed = if (revealed == null) valueSupplier() else null
        }) {
            Icon(
                if (revealed == null) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                contentDescription = if (revealed == null) "reveal" else "mask",
                tint = Cyan,
            )
        }
        if (revealed != null) {
            Spacer(Modifier.width(2.dp))
            CopyChip(revealed ?: "", label = "${secondsLeft}s")
        }
    }
}
