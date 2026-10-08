package com.projectzerodays.quantumcli.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzerodays.quantumcli.ops.BleRecon
import com.projectzerodays.quantumcli.ops.Oui
import com.projectzerodays.quantumcli.ops.WifiDefense
import com.projectzerodays.quantumcli.ui.theme.Cyan
import com.projectzerodays.quantumcli.ui.theme.Danger
import com.projectzerodays.quantumcli.ui.theme.Ok
import com.projectzerodays.quantumcli.ui.theme.Muted
import com.projectzerodays.quantumcli.ui.theme.QuantumTypography
import com.projectzerodays.quantumcli.ui.theme.SoftCyan
import com.projectzerodays.quantumcli.ui.theme.Softest
import com.projectzerodays.quantumcli.ui.theme.Warn
import com.projectzerodays.quantumcli.ui.theme.quantumPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun defensePermissions(): List<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= 31) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
}

// ---------------------------------------------------------------------------
// Wi-Fi Defense — hidden APs, rogue twins, presence deltas, root deauth watch
// ---------------------------------------------------------------------------

@Composable
fun WifiDefensePage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val aps by WifiDefense.aps.collectAsState()
    val alerts by WifiDefense.alerts.collectAsState()
    val presence by WifiDefense.presence.collectAsState()
    var refreshing by remember { mutableStateOf(false) }
    var deauthOut by remember { mutableStateOf<String?>(null) }
    var permsOk by remember { mutableStateOf(false) }
    var macQuery by rememberSaveable { mutableStateOf("") }
    var macResult by remember { mutableStateOf<String?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> permsOk = grants.values.all { it } }

    LaunchedEffect(Unit) { permLauncher.launch(defensePermissions().toTypedArray()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        PageHeader(
            "Wi-Fi Defense",
            "Hidden AP revealer · rogue/evil-twin detector · presence deltas · root deauth watch (NRSuite parity)",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    refreshing = true
                    scope.launch {
                        WifiDefense.refresh(ctx)
                        refreshing = false
                    }
                },
                enabled = !refreshing && permsOk,
            ) { Text(if (refreshing) "SCANNING…" else "REFRESH SCAN") }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        deauthOut = withContext(Dispatchers.IO) {
                            WifiDefense.monitorIface()?.let { WifiDefense.deauthWatch(it) }
                                ?: "no monitor interface / no root — deauth watch unavailable on this device"
                        }
                    }
                },
            ) { Text("DEAUTH WATCH") }
        }
        if (!permsOk) {
            Spacer(Modifier.height(4.dp))
            Text("location permission required for scan results", style = QuantumTypography.labelSmall, color = Warn)
        }
        if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))

        if (alerts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Alerts", style = QuantumTypography.titleSmall, color = Danger)
            alerts.forEach { a ->
                Text("[${a.kind}] ${a.detail}", style = QuantumTypography.bodySmall, color = Danger, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("APs (${aps.size})", style = QuantumTypography.titleSmall, color = SoftCyan)
        aps.take(40).forEach { ap ->
            val delta = presence[ap.bssid]
            Column(Modifier.fillMaxWidth().quantumPanel().padding(8.dp)) {
                Text(
                    (if (ap.hidden) "[HIDDEN] " else "") + ap.ssid.ifBlank { "(no ssid)" },
                    style = QuantumTypography.bodyMedium,
                    color = if (ap.hidden) Warn else Softest,
                )
                Text(
                    "${ap.bssid} · ${Oui.describeCaps(ap.caps)} · ${ap.level}dBm · ${ap.freq}MHz · ${ap.vendor}" +
                        (delta?.let { " · Δ${if (it > 0) "+" else ""}$it dB" } ?: ""),
                    style = QuantumTypography.labelSmall,
                    color = Muted,
                    fontSize = 10.sp,
                )
            }
        }
        deauthOut?.let {
            Spacer(Modifier.height(8.dp))
            Text("Deauth watch", style = QuantumTypography.titleSmall, color = SoftCyan)
            ResultList(it.lines().take(8))
        }

        Spacer(Modifier.height(8.dp))
        Text("MAC / OUI lookup", style = QuantumTypography.titleSmall, color = SoftCyan)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = macQuery,
                onValueChange = { macQuery = it },
                label = { Text("MAC address") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.height(0.dp))
            Button(
                onClick = {
                    scope.launch {
                        macResult = withContext(Dispatchers.IO) { "${macQuery} → ${Oui.lookup(macQuery)}" }
                    }
                },
                enabled = macQuery.isNotBlank(),
            ) { Text("LOOKUP") }
        }
        macResult?.let { Text(it, style = QuantumTypography.bodySmall, color = Ok) }
        Spacer(Modifier.height(20.dp))
    }
}

// ---------------------------------------------------------------------------
// BLE Recon — scanner, vendor decode, tracker detection
// ---------------------------------------------------------------------------

@Composable
fun BleReconPage() {
    val ctx = LocalContext.current
    val scanning by BleRecon.scanning.collectAsState()
    val devices by BleRecon.devices.collectAsState()
    val alerts by BleRecon.alerts.collectAsState()
    var permsOk by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> permsOk = grants.values.all { it } }

    LaunchedEffect(Unit) { permLauncher.launch(defensePermissions().toTypedArray()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        PageHeader(
            "BLE Recon",
            "Live Bluetooth LE sweep with OUI vendor decode, service-UUID inventory and rotating-identity tracker detection",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!scanning) {
                Button(
                    onClick = { BleRecon.start(ctx) },
                    enabled = permsOk && BleRecon.ready(ctx),
                ) { Text("START SCAN") }
            } else {
                Button(onClick = { BleRecon.stop(ctx) }) { Text("STOP") }
            }
            OutlinedButton(onClick = { BleRecon.clear() }) { Text("CLEAR") }
        }
        if (!permsOk) {
            Spacer(Modifier.height(4.dp))
            Text("Bluetooth + location permissions required", style = QuantumTypography.labelSmall, color = Warn)
        } else if (!BleRecon.ready(ctx)) {
            Spacer(Modifier.height(4.dp))
            Text("Bluetooth adapter off or unavailable", style = QuantumTypography.labelSmall, color = Warn)
        }
        if (scanning) {
            Spacer(Modifier.height(6.dp))
            Text("scanning… ${devices.size} device(s)", style = QuantumTypography.labelMedium, color = Ok)
        }
        if (alerts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Tracker alerts", style = QuantumTypography.titleSmall, color = Danger)
            alerts.forEach { a ->
                Text("[${a.reason}] ${a.detail}", style = QuantumTypography.bodySmall, color = Danger, fontSize = 11.sp)
                Text("  MACs: ${a.macs.joinToString().take(120)}", style = QuantumTypography.labelSmall, color = Muted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Devices (${devices.size})", style = QuantumTypography.titleSmall, color = SoftCyan)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .quantumPanel()
                .padding(8.dp),
        ) {
            items(devices.values.sortedByDescending { it.rssi }) { d ->
                Column(Modifier.padding(vertical = 2.dp)) {
                    Text(
                        "${d.name ?: "(unnamed)"}  ${d.rssi}dBm",
                        style = QuantumTypography.bodyMedium,
                        color = Softest,
                    )
                    Text(
                        "${d.mac} · ${d.vendor}" +
                            (d.services.take(3).let { if (it.isEmpty()) "" else " · svc ${it.joinToString(",")}" }),
                        style = QuantumTypography.labelSmall,
                        color = Muted,
                        fontSize = 10.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
