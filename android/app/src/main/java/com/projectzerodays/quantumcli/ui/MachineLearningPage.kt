package com.projectzerodays.quantumcli.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzerodays.quantumcli.ai.AiClient
import com.projectzerodays.quantumcli.ai.AiModelDownloader
import com.projectzerodays.quantumcli.ai.MlTelemetry
import com.projectzerodays.quantumcli.ai.Providers
import com.projectzerodays.quantumcli.c2.C2State
import com.projectzerodays.quantumcli.data.QuantSettings
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Machine Learning dashboard — local/remote inference controls, engine
 * configuration, live telemetry charts and model management.
 *
 * Honest scopes:
 *  - Sampling params (temperature/top-K/max-tokens) drive the MediaPipe
 *    LlmInferenceOptions of the on-device engine; Apply unloads the engine
 *    so the next call rebuilds it with the new values.
 *  - Runtime delegate (CPU/GPU/NNAPI) is chosen by MediaPipe per device and
 *    is NOT user-selectable in this build — the widget says so plainly.
 *  - Throughput/latency charts come from MlTelemetry samples recorded around
 *    real inference calls only (deterministic replies are never counted).
 */
@Composable
fun MachineLearningPage() {
    val settings by QuantSettings.state.collectAsState()
    val samples by MlTelemetry.samples.collectAsState()
    val stats = remember(samples) { MlTelemetry.stats(samples) }
    val downloading by AiModelDownloader.downloading.collectAsState()
    val dlProgress by AiModelDownloader.progress.collectAsState()
    val dlError by AiModelDownloader.error.collectAsState()
    val scope = rememberCoroutineScope()

    var temp by remember(settings.mlTemperature) { mutableStateOf(settings.mlTemperature) }
    var topK by remember(settings.mlTopK) { mutableStateOf(settings.mlTopK.toFloat()) }
    var maxTok by remember(settings.mlMaxTokens) { mutableStateOf(settings.mlMaxTokens.toFloat()) }
    var tele by remember(settings.mlTelemetry) { mutableStateOf(settings.mlTelemetry) }

    var prompt by rememberSaveable {
        mutableStateOf("Summarize the current threat surface in one line.")
    }
    var testOut by remember { mutableStateOf<String?>(null) }
    var testBusy by remember { mutableStateOf(false) }

    // Recompute the model file each time download progress or settings move.
    val modelFile = remember(dlProgress, settings.mlMaxTokens) { AiClient.localModelFile() }

    fun applyParams() {
        QuantSettings.update {
            it.copy(
                mlTemperature = temp,
                mlTopK = topK.toInt(),
                mlMaxTokens = maxTok.toInt(),
                mlTelemetry = tele,
            )
        }
        AiClient.closeLocal() // rebuild with new options on next inference
        C2State.audit(
            "AI",
            "ML params applied — temp=$temp topK=${topK.toInt()} " +
                "maxTokens=${maxTok.toInt()} telemetry=$tele (engine reload on next call)",
        )
    }

    fun runTest() {
        if (testBusy) return
        testBusy = true
        testOut = null
        val mode = settings.aiMode
        scope.launch {
            val t0 = System.currentTimeMillis()
            val res = withContext(Dispatchers.IO) {
                if (mode == AiClient.MODE_ONLINE) {
                    AiClient.onlineChat(listOf("user" to prompt))
                } else {
                    AiClient.localChat(prompt)
                }
            }
            val dt = System.currentTimeMillis() - t0
            testOut = if (res != null) {
                "[${dt} ms · $mode]\n$res"
            } else {
                "[failed after ${dt} ms · $mode] — check model/provider in Settings; " +
                    "details are in the Dashboard console"
            }
            testBusy = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = Cyan)
            Spacer(Modifier.width(8.dp))
            Text("Machine Learning", style = QuantumTypography.displaySmall, color = Cyan)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "On-device (MediaPipe) + online provider inference, sampling controls, " +
                "model management and live telemetry.",
            style = QuantumTypography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(12.dp))

        // ---------------------------------------------------- telemetry tiles
        Row(Modifier.fillMaxWidth()) {
            StatTile(Modifier.weight(1f), "requests", stats.count.toString())
            Spacer(Modifier.width(8.dp))
            StatTile(Modifier.weight(1f), "avg latency", "${stats.avgLatencyMs} ms")
            Spacer(Modifier.width(8.dp))
            StatTile(
                Modifier.weight(1f), "success",
                if (stats.count == 0) "—" else "${stats.okCount * 100 / stats.count}%",
                color = if (stats.failCount == 0) Ok else Warn,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            StatTile(Modifier.weight(1f), "p95 latency", "${stats.p95LatencyMs} ms")
            Spacer(Modifier.width(8.dp))
            StatTile(
                Modifier.weight(1f), "throughput",
                if (stats.count == 0) "—" else "${stats.charsPerSec.toInt()} ch/s",
            )
            Spacer(Modifier.width(8.dp))
            StatTile(
                Modifier.weight(1f), "tokens (est)",
                if (stats.count == 0) "—" else stats.estTokens.toString(),
                color = SoftCyan,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (stats.failCount > 0) {
            Text(
                "${stats.failCount} failed inference(s) this session — see Dashboard console.",
                style = QuantumTypography.bodySmall,
                color = Danger,
            )
            Spacer(Modifier.height(8.dp))
        }

        // ----------------------------------------------------------- charts
        Row(Modifier.fillMaxWidth()) {
            QCard(Modifier.weight(1f), title = "LATENCY (MS)") {
                Sparkline(
                    values = samples.takeLast(40).map { it.latencyMs.toFloat() },
                    color = Cyan,
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            QCard(Modifier.weight(1f), title = "THROUGHPUT (CH/S)") {
                Sparkline(
                    values = samples.takeLast(40).map {
                        if (it.latencyMs > 0) it.outChars / (it.latencyMs / 1000f) else 0f
                    },
                    color = Ok,
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // ------------------------------------------------------ model widget
        QCard(Modifier.fillMaxWidth(), title = "LOCAL MODEL") {
            if (modelFile != null) {
                Text(
                    modelFile.name,
                    style = QuantumTypography.titleSmall,
                    color = Cyan,
                )
                Text(
                    "${modelFile.length() / (1024 * 1024)} MB · files dir · " +
                        if (AiClient.localAvailable()) "available" else "unavailable",
                    style = QuantumTypography.bodySmall,
                    color = Muted,
                )
            } else {
                Text("No local model installed", style = QuantumTypography.titleSmall, color = Warn)
                Text(
                    AiClient.localHint(),
                    style = QuantumTypography.bodySmall,
                    color = Muted,
                )
            }
            if (downloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = dlProgress,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "downloading… ${(dlProgress * 100).toInt()}% — resumes automatically " +
                        "if interrupted",
                    style = QuantumTypography.labelSmall,
                    color = SoftCyan,
                )
            }
            dlError?.let {
                Spacer(Modifier.height(4.dp))
                Text("download error: $it", style = QuantumTypography.bodySmall, color = Danger)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { AiModelDownloader.start() },
                    enabled = !downloading && modelFile == null,
                ) {
                    Icon(Icons.Outlined.Download, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (downloading) "DOWNLOADING…" else "DOWNLOAD")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        AiClient.closeLocal()
                        C2State.audit("AI", "ML dashboard: local engine unloaded")
                    },
                    enabled = modelFile != null,
                ) { Text("UNLOAD ENGINE") }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        AiClient.closeLocal()
                        val f = AiClient.localModelFile()
                        val ok = f?.delete() == true
                        C2State.audit(
                            "AI",
                            if (ok) "ML dashboard: model deleted" else "ML dashboard: delete failed",
                        )
                    },
                    enabled = modelFile != null,
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete model", tint = Danger)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // ------------------------------------------------- inference controls
        QCard(Modifier.fillMaxWidth(), title = "INFERENCE CONTROLS") {
            ParamSlider(
                label = "temperature",
                value = temp,
                range = 0f..2f,
                steps = 19,
                display = "%.2f".format(temp),
                onChange = { temp = it },
            )
            ParamSlider(
                label = "top-k",
                value = topK,
                range = 1f..100f,
                steps = 98,
                display = topK.toInt().toString(),
                onChange = { topK = it },
            )
            ParamSlider(
                label = "max tokens",
                value = maxTok,
                range = 64f..4096f,
                steps = 63,
                display = maxTok.toInt().toString(),
                onChange = { maxTok = it },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("telemetry", style = QuantumTypography.labelLarge, color = Softest)
                    Text(
                        "record latency/throughput samples (session only, never leaves the device)",
                        style = QuantumTypography.labelSmall,
                        color = Muted,
                    )
                }
                Switch(checked = tele, onCheckedChange = { tele = it })
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { applyParams() }) { Text("APPLY & RELOAD ENGINE") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    temp = 0.8f
                    topK = 40f
                    maxTok = 1024f
                    tele = true
                }) { Text("RESET DEFAULTS") }
            }
            Text(
                "Sampling params apply to the on-device engine (online providers use their " +
                    "server defaults). Runtime delegate (CPU/GPU) is auto-selected by MediaPipe " +
                    "per device — not user-configurable in this build.",
                style = QuantumTypography.labelSmall,
                color = Muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        // ------------------------------------------------------- test run
        QCard(Modifier.fillMaxWidth(), title = "TEST INFERENCE") {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = QuantumTypography.bodySmall.copy(color = Softest),
                minLines = 2,
                maxLines = 4,
                placeholder = {
                    Text("prompt to time…", style = QuantumTypography.bodySmall, color = Muted)
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { runTest() }, enabled = !testBusy) {
                    Icon(Icons.Outlined.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (testBusy) "RUNNING…" else "RUN")
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "mode: ${settings.aiMode}",
                    style = QuantumTypography.labelMedium,
                    color = SoftCyan,
                )
            }
            testOut?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = QuantumTypography.bodySmall,
                    color = if (it.startsWith("[")) Warn else Softest,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // --------------------------------------------------- config summary
        QCard(Modifier.fillMaxWidth(), title = "ENGINE CONFIGURATION") {
            ConfigRow("mode", settings.aiMode)
            ConfigRow(
                "provider",
                Providers.byId(settings.aiProvider).label + " · " +
                    Providers.byId(settings.aiProvider).model,
            )
            ConfigRow("endpoint", settings.apiUrl)
            ConfigRow(
                "api key",
                if (settings.aiApiKey.isNotBlank() || settings.apiKey.isNotBlank()) {
                    "configured ✓ (stored encrypted)"
                } else {
                    "missing — online mode will fall back to deterministic"
                },
            )
            ConfigRow(
                "local engine",
                modelFile?.absolutePath ?: "not installed",
            )
            ConfigRow(
                "sampling",
                "temp=${settings.mlTemperature} topK=${settings.mlTopK} " +
                    "maxTokens=${settings.mlMaxTokens}",
            )
            ConfigRow("telemetry", if (settings.mlTelemetry) "on" else "off")
            Text(
                "Full provider/endpoint editing lives in Settings; changes here only affect " +
                    "the on-device engine.",
                style = QuantumTypography.labelSmall,
                color = Muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ---------------------------------------------------------------------------
// widgets
// ---------------------------------------------------------------------------

/** One KPI tile: big value over a small label, centered on a glass panel. */
@Composable
fun StatTile(modifier: Modifier = Modifier, label: String, value: String, color: Color = Cyan) {
    Column(
        modifier.quantumPanel().padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = QuantumTypography.titleMedium, color = color)
        Text(label, style = QuantumTypography.labelSmall, color = Muted)
    }
}

/** Label + slider + live value row used by the inference controls card. */
@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = QuantumTypography.labelLarge,
            color = SoftCyan,
            modifier = Modifier.width(92.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.weight(1f),
        )
        Text(
            display,
            style = QuantumTypography.labelMedium,
            color = Softest,
            modifier = Modifier.width(54.dp),
        )
    }
}

/** Small key/value row in the configuration summary. */
@Composable
private fun ConfigRow(key: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            key,
            style = QuantumTypography.labelMedium,
            color = Muted,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            style = QuantumTypography.bodySmall,
            color = Softest,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Canvas sparkline — gradient area fill under a stroked line plus a dot on
 * the newest sample. Empty state shows an honest "no data yet".
 */
@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    if (values.isEmpty()) {
        Box_placeholder(modifier)
        return
    }
    Canvas(modifier) {
        val maxV = values.max().coerceAtLeast(0.0001f)
        val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f
        fun y(v: Float): Float {
            val frac = (v / maxV).coerceIn(0f, 1f)
            return size.height * (0.96f - frac * 0.90f)
        }
        val pts = values.mapIndexed { i, v -> androidx.compose.ui.geometry.Offset(i * stepX, y(v)) }
        val line = Path().apply {
            pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        val area = Path().apply {
            pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            lineTo(pts.last().x, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            area,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f)),
            ),
        )
        drawPath(
            line,
            color = color,
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(color, radius = 3.dp.toPx(), center = pts.last())
    }
}

/** Empty-state placeholder matching the sparkline bounds. */
@Composable
private fun Box_placeholder(modifier: Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text("no data yet", style = QuantumTypography.labelSmall, color = Muted)
    }
}
