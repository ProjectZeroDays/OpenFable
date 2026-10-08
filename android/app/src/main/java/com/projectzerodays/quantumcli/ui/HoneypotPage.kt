package com.projectzerodays.quantumcli.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.projectzerodays.quantumcli.c2.C2State
import com.projectzerodays.quantumcli.ops.Honeypot
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Honeypot dashboard — start/stop embedded fake services, watch inbound
 * connections land live, export the captured session log as JSON.
 *
 * Honest scope (stated in-page): listeners run inside this app on THIS
 * device; presets bind unprivileged ports (>=1024) so no root is needed,
 * ports below 1024 are not offered. Hits only show what actually connects.
 */
@Composable
fun HoneypotPage() {
    val ctx = LocalContext.current
    val hits by Honeypot.hits.collectAsState()
    val runningIds by Honeypot.runningIds.collectAsState()
    var msg by remember { mutableStateOf<String?>(null) }

    val dateFmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.US) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(Honeypot.exportJson().toByteArray(Charsets.UTF_8))
                }
                msg = "exported ${hits.size} hit(s)"
            } catch (e: Exception) {
                msg = "export failed: ${e.message}"
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Sensors, null, tint = Cyan)
            Spacer(Modifier.width(8.dp))
            Text("Honeypot", style = QuantumTypography.displaySmall, color = Cyan)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Embedded fake services that log who connects — banners emulate real " +
                "daemons to draw out a probe or credential attempt.",
            style = QuantumTypography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(12.dp))

        // ------------------------------------------------------- services
        QCard(Modifier.fillMaxWidth(), title = "SERVICES") {
            Honeypot.PRESETS.forEach { svc ->
                val running = svc.id in runningIds
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 3.dp),
                ) {
                    Icon(
                        Icons.Outlined.Sensors,
                        contentDescription = null,
                        tint = if (running) Ok else Muted,
                        modifier = Modifier.width(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            svc.name,
                            style = QuantumTypography.titleSmall,
                            color = if (running) Ok else Softest,
                        )
                        Text(
                            "0.0.0.0:${svc.port} · " +
                                (if (running) "LISTENING" else "stopped"),
                            style = QuantumTypography.labelSmall,
                            color = if (running) Ok else Muted,
                        )
                    }
                    if (running) {
                        OutlinedButton(onClick = {
                            val r = Honeypot.stop(svc.id)
                            msg = "${svc.name}: $r"
                            C2State.audit("HONEYPOT", "${svc.name} $r")
                        }) { Text("STOP") }
                    } else {
                        Button(onClick = {
                            val r = Honeypot.start(svc)
                            msg = r
                            C2State.audit("HONEYPOT", r)
                        }) { Text("START") }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "All presets bind unprivileged ports (>=1024) — no root needed. " +
                    "Ports below 1024 require root and are not offered here. " +
                    "Services stop when the app process is killed.",
                style = QuantumTypography.labelSmall,
                color = Muted,
            )
            msg?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = QuantumTypography.bodySmall, color = SoftCyan)
            }
        }
        Spacer(Modifier.height(10.dp))

        // ----------------------------------------------------- hit log
        QCard(Modifier.fillMaxWidth(), title = "CONNECTION LOG") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${hits.size} hit(s) · ${runningIds.size} service(s) up",
                    style = QuantumTypography.labelMedium,
                    color = if (hits.isEmpty()) Muted else Warn,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        exportLauncher.launch("qcli-honeypot-hits.json")
                    },
                    enabled = hits.isNotEmpty(),
                ) { Text("EXPORT") }
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        Honeypot.clearHits()
                        C2State.audit("HONEYPOT", "hit log cleared")
                    },
                    enabled = hits.isNotEmpty(),
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Clear hit log",
                        tint = Danger,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            if (hits.isEmpty()) {
                Text(
                    "No hits yet — start a service and point something at it.",
                    style = QuantumTypography.bodySmall,
                    color = Muted,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                ) {
                    items(hits.asReversed()) { hit ->
                        val svc = Honeypot.PRESETS.firstOrNull { it.id == hit.serviceId }
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .quantumPanel()
                                .padding(6.dp)
                                .padding(bottom = 4.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${svc?.name ?: hit.serviceId}  " +
                                        "${hit.remoteIp}:${hit.remotePort}",
                                    style = QuantumTypography.labelMedium,
                                    color = Warn,
                                )
                                if (hit.detail.isNotEmpty()) {
                                    Text(
                                        hit.detail,
                                        style = QuantumTypography.bodySmall,
                                        color = Softest,
                                    )
                                } else {
                                    Text(
                                        "(connected, no payload received)",
                                        style = QuantumTypography.labelSmall,
                                        color = Muted,
                                    )
                                }
                            }
                            Text(
                                dateFmt.format(Date(hit.ts)),
                                style = QuantumTypography.labelSmall,
                                color = Muted,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Newest first. Detail shows the first lines the peer sent " +
                        "(e.g. USER/PASS probes) — verbatim, best-effort within " +
                        "8 s read timeout.",
                    style = QuantumTypography.labelSmall,
                    color = Muted,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
