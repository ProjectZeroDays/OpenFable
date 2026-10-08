package com.projectzerodays.quantumcli.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Adb
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzerodays.quantumcli.c2.C2State
import com.projectzerodays.quantumcli.ops.AdbControl
import com.projectzerodays.quantumcli.ops.Net
import com.projectzerodays.quantumcli.ops.ZenScan
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Devices — PhantomDroid-style Android device control over wireless ADB.
 *
 * Scan the LAN for port 5555, connect via dadb (direct ADB protocol, no adb
 * binary), then drive the target: shell, tap/swipe/text/key injection,
 * screenshots with a live stills view, package list/install/uninstall.
 * USB (OTG) is honestly reported as unsupported by this client on Android —
 * see [AdbControl.USB_NOTE]. No fake controls, no fake states.
 */
@Composable
fun DevicesPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var scanTarget by rememberSaveable { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var foundHosts by remember { mutableStateOf<List<String>>(emptyList()) }

    var target by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf("not connected") }
    var connected by remember { mutableStateOf(AdbControl.isConnected()) }
    var device by remember { mutableStateOf<AdbControl.DeviceInfo?>(null) }

    var shellCmd by rememberSaveable { mutableStateOf("getprop ro.product.model") }
    var shellOut by remember { mutableStateOf("") }

    var packages by remember { mutableStateOf<List<String>>(emptyList()) }
    var pkgMsg by remember { mutableStateOf<String?>(null) }

    var showShot by remember { mutableStateOf(false) }
    var shotBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var shotError by remember { mutableStateOf<String?>(null) }
    var live by remember { mutableStateOf(false) }
    var liveTs by remember { mutableStateOf("") }

    val keyDir = remember { File(ctx.filesDir, "adb") }
    val shotFile = remember { File(ctx.cacheDir, "qcli_screen.png") }

    fun audit(msg: String) {
        C2State.audit("ADB", msg)
    }

    // ---------------------------------------------------------- scanning
    fun runScan() {
        val t = scanTarget.trim()
        if (t.isEmpty() || scanning) return
        scanning = true
        foundHosts = emptyList()
        scope.launch {
            val hosts = withContext(Dispatchers.IO) {
                if ("/" in t) Net.cidrHosts(t) else listOf(t)
            }
            val results = withContext(Dispatchers.IO) {
                ZenScan.scan(hosts, listOf(AdbControl.DEFAULT_PORT), timing = 3)
            }
            foundHosts = results.filter { AdbControl.DEFAULT_PORT in it.openPorts }
                .map { it.ip }
            scanning = false
            audit("adb scan: ${foundHosts.size} host(s) with port 5555 open")
        }
    }

    // --------------------------------------------------------- lifecycle
    fun refreshDevice() {
        device = AdbControl.deviceInfo()
        if (device == null && !connected) status = "not connected"
    }

    fun doConnect(host: String, port: Int) {
        scope.launch {
            status = withContext(Dispatchers.IO) {
                AdbControl.connect(host, port, keyDir)
            }
            connected = AdbControl.isConnected()
            audit("adb connect $host:$port -> $status")
            if (connected) refreshDevice()
        }
    }

    fun doDisconnect() {
        AdbControl.disconnect()
        connected = false
        device = null
        packages = emptyList()
        status = "disconnected"
        audit("adb disconnected")
    }

    fun runShell(cmd: String) {
        if (cmd.isBlank()) return
        scope.launch {
            val r = withContext(Dispatchers.IO) { AdbControl.shell(cmd) }
            shellOut = if (r.output.isBlank()) "(no output)" else r.output
            audit("adb shell [$cmd] exit=${r.exitCode}")
        }
    }

    fun snap() {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { AdbControl.screenshot(shotFile) }
            if (ok) {
                shotBitmap = BitmapFactory.decodeFile(shotFile.absolutePath)
                shotError = null
                showShot = true
                audit("screenshot captured")
            } else {
                shotError = "screencap failed — target may deny /data/local/tmp writes"
                showShot = true
                audit("screenshot failed")
            }
        }
    }

    // live stills loop (honest: auto-refreshing screencaps, not a video stream)
    LaunchedEffect(live) {
        while (live) {
            if (AdbControl.isConnected() && AdbControl.screenshot(shotFile)) {
                shotBitmap = BitmapFactory.decodeFile(shotFile.absolutePath)
                shotError = null
                liveTs = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date())
            }
            delay(2_000)
        }
    }

    // ------------------------------------------------------- apk install
    val apkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val msg = withContext(Dispatchers.IO) {
                    try {
                        val f = File(ctx.cacheDir, "install_${System.currentTimeMillis()}.apk")
                        ctx.contentResolver.openInputStream(uri)?.use { input ->
                            f.outputStream().use { output -> input.copyTo(output) }
                        }
                        val res = AdbControl.installApk(f)
                        f.delete()
                        res
                    } catch (e: Exception) {
                        "install failed: ${e.message}"
                    }
                }
                pkgMsg = msg
                audit("adb install: $msg")
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
            Icon(Icons.Outlined.Adb, null, tint = Cyan)
            Spacer(Modifier.width(8.dp))
            Text("Devices", style = QuantumTypography.displaySmall, color = Cyan)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Control Android devices over wireless ADB (direct protocol, no adb " +
                "binary): shell, input injection, screenshots, app management.",
            style = QuantumTypography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(12.dp))

        // ------------------------------------------------------------ scan
        QCard(Modifier.fillMaxWidth(), title = "SCAN FOR ADB (PORT 5555)") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = scanTarget,
                    onValueChange = { scanTarget = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = {
                        Text("192.168.1.0/24 or single ip", style = QuantumTypography.bodySmall)
                    },
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { runScan() }, enabled = !scanning) {
                    Text(if (scanning) "SCANNING…" else "SCAN")
                }
            }
            if (foundHosts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                foundHosts.forEach { ip ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$ip:${AdbControl.DEFAULT_PORT}",
                            style = QuantumTypography.bodySmall,
                            color = Ok,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { target = "$ip:${AdbControl.DEFAULT_PORT}" }) {
                            Text("USE", color = Cyan)
                        }
                        TextButton(onClick = {
                            target = "$ip:${AdbControl.DEFAULT_PORT}"
                            doConnect(ip, AdbControl.DEFAULT_PORT)
                        }) {
                            Text("CONNECT", color = Cyan)
                        }
                    }
                }
            } else if (!scanning && scanTarget.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "no host with 5555 open (target must enable Wireless debugging)",
                    style = QuantumTypography.labelSmall,
                    color = Muted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // --------------------------------------------------------- connect
        QCard(Modifier.fillMaxWidth(), title = "CONNECTION") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = {
                        Text("ip:5555", style = QuantumTypography.bodySmall)
                    },
                )
                Spacer(Modifier.width(8.dp))
                if (connected) {
                    OutlinedButton(onClick = { doDisconnect() }) { Text("DISCONNECT") }
                } else {
                    Button(onClick = {
                        val parsed = AdbControl.parseTarget(target)
                        if (parsed == null) {
                            status = "invalid target — use ip:port"
                        } else {
                            doConnect(parsed.first, parsed.second)
                        }
                    }) { Text("CONNECT") }
                }
            }
            Spacer(Modifier.height(6.dp))
            val statusColor = when {
                connected -> Ok
                status.startsWith("connect failed") -> Danger
                else -> SoftCyan
            }
            Text(status, style = QuantumTypography.bodySmall, color = statusColor)
            Text(
                AdbControl.USB_NOTE,
                style = QuantumTypography.labelSmall,
                color = Warn,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (connected) {
            Spacer(Modifier.height(10.dp))

            // ------------------------------------------------ device info
            QCard(Modifier.fillMaxWidth(), title = "TARGET DEVICE") {
                val d = device
                if (d != null) {
                    ConfigRowSmall("model", d.model)
                    ConfigRowSmall("android", "${d.androidVersion} (sdk ${d.sdk})")
                    ConfigRowSmall("serial", d.serial)
                } else {
                    Text("reading getprop…", style = QuantumTypography.bodySmall, color = Muted)
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { refreshDevice() }) { Text("REFRESH INFO") }
            }
            Spacer(Modifier.height(10.dp))

            // -------------------------------------------------- screenshots
            QCard(Modifier.fillMaxWidth(), title = "SCREEN") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { snap() }) {
                        Icon(Icons.Outlined.PlayArrow, null)
                        Spacer(Modifier.width(4.dp))
                        Text("SNAPSHOT")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { live = true }) { Text("LIVE VIEW") }
                }
                shotError?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = QuantumTypography.bodySmall, color = Danger)
                }
                Text(
                    "Live view auto-captures a still every 2 s (screencap over ADB) — " +
                        "honest stills, not a mirrored video stream.",
                    style = QuantumTypography.labelSmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(10.dp))

            // ------------------------------------------------------ input
            QCard(Modifier.fillMaxWidth(), title = "INPUT INJECTION") {
                InputInjectionRow { runShell(it) }
                Spacer(Modifier.height(6.dp))
                KeyRow { code, label ->
                    runShell(AdbControl.keyCmd(code))
                    audit("adb key: $label")
                }
            }
            Spacer(Modifier.height(10.dp))

            // ------------------------------------------------------ shell
            QCard(Modifier.fillMaxWidth(), title = "SHELL") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = shellCmd,
                        onValueChange = { shellCmd = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("command", style = QuantumTypography.bodySmall) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { runShell(shellCmd) }) { Text("RUN") }
                }
                if (shellOut.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .quantumPanel()
                            .padding(8.dp),
                    ) {
                        items(shellOut.lines()) { line ->
                            Text(
                                line,
                                style = QuantumTypography.bodySmall,
                                color = Softest,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // ------------------------------------------------------ apps
            QCard(Modifier.fillMaxWidth(), title = "APPS") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        scope.launch {
                            packages = withContext(Dispatchers.IO) {
                                AdbControl.listPackages(thirdPartyOnly = true)
                            }
                            audit("adb: listed ${packages.size} third-party packages")
                        }
                    }) { Text("LIST") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        apkPicker.launch(arrayOf("*/*"))
                    }) { Text("INSTALL APK") }
                }
                pkgMsg?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = QuantumTypography.bodySmall, color = SoftCyan)
                }
                if (packages.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${packages.size} third-party packages",
                        style = QuantumTypography.labelMedium,
                        color = Muted,
                    )
                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .padding(top = 4.dp),
                    ) {
                        items(packages) { pkg ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    pkg,
                                    style = QuantumTypography.bodySmall,
                                    color = Softest,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = {
                                    scope.launch {
                                        val msg = withContext(Dispatchers.IO) {
                                            AdbControl.uninstall(pkg)
                                        }
                                        pkgMsg = msg
                                        audit("adb uninstall $pkg: $msg")
                                        if (msg.startsWith("uninstalled")) {
                                            packages = packages - pkg
                                        }
                                    }
                                }) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Uninstall $pkg",
                                        tint = Danger,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ------------------------------------------------- screenshot dialog
        if (showShot) {
            AlertDialog(
                onDismissRequest = { showShot = false },
                title = {
                    Text("Target screen", style = QuantumTypography.titleSmall, color = Cyan)
                },
                text = {
                    val bmp = shotBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Target screenshot",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("no frame yet", color = Muted)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showShot = false }) { Text("Close") }
                },
            )
        }

        // ----------------------------------------------------- live dialog
        if (live) {
            AlertDialog(
                onDismissRequest = { live = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "LIVE (stills @2s)",
                            style = QuantumTypography.titleSmall,
                            color = Cyan,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(liveTs, style = QuantumTypography.labelSmall, color = Muted)
                    }
                },
                text = {
                    val bmp = shotBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Live target screen",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("waiting for first frame…", color = Muted)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { live = false }) { Text("STOP") }
                },
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}

/** Tap + swipe + text controls, wired through [onCommand] to the shell runner. */
@Composable
private fun InputInjectionRow(onCommand: (String) -> Unit) {
    var x by rememberSaveable { mutableStateOf("540") }
    var y by rememberSaveable { mutableStateOf("1200") }
    var text by rememberSaveable { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = x,
            onValueChange = { x = it.filter(Char::isDigit) },
            modifier = Modifier.width(74.dp),
            singleLine = true,
            label = { Text("x", style = QuantumTypography.labelSmall) },
        )
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(
            value = y,
            onValueChange = { y = it.filter(Char::isDigit) },
            modifier = Modifier.width(74.dp),
            singleLine = true,
            label = { Text("y", style = QuantumTypography.labelSmall) },
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            val xi = x.toIntOrNull()
            val yi = y.toIntOrNull()
            if (xi != null && yi != null) {
                onCommand(AdbControl.tapCmd(xi, yi))
            }
        }) { Text("TAP") }
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("text to inject", style = QuantumTypography.bodySmall) },
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                onCommand(AdbControl.textCmd(text))
                text = ""
            },
            enabled = text.isNotBlank(),
        ) { Text("SEND") }
    }
}

/** System key buttons: home / back / recents / power / volume. */
@Composable
private fun KeyRow(onKey: (Int, String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onKey(3, "home") }) { Text("HOME") }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { onKey(4, "back") }) { Text("BACK") }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { onKey(187, "recents") }) { Text("RECENTS") }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { onKey(26, "power") }) { Text("POWER") }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { onKey(24, "vol+") }) { Text("VOL+") }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { onKey(25, "vol-") }) { Text("VOL-") }
    }
}

/** Small key/value row (device info card). */
@Composable
private fun ConfigRowSmall(key: String, value: String) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(
            key,
            style = QuantumTypography.labelMedium,
            color = Muted,
            modifier = Modifier.width(72.dp),
        )
        Text(value, style = QuantumTypography.bodySmall, color = Softest)
    }
}
