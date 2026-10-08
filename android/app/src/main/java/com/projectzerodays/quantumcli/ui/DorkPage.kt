package com.projectzerodays.quantumcli.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.projectzerodays.quantumcli.c2.C2State
import com.projectzerodays.quantumcli.ops.GoogleDork
import com.projectzerodays.quantumcli.ui.theme.Cyan
import com.projectzerodays.quantumcli.ui.theme.Muted
import com.projectzerodays.quantumcli.ui.theme.QCard
import com.projectzerodays.quantumcli.ui.theme.QuantumTypography
import com.projectzerodays.quantumcli.ui.theme.SoftCyan
import com.projectzerodays.quantumcli.ui.theme.Softest
import com.projectzerodays.quantumcli.ui.theme.Warn

/**
 * Google Dorking — preset query packs by objective, operator reference,
 * analyst tradecraft, and an in-app browser that renders live results.
 *
 * Queries are built locally and loaded as plain search URLs; nothing is
 * scraped and no automation fights CAPTCHAs — what the engine serves is
 * what you see. Dorks locate EXPOSED data; anything beyond read-only
 * retrieval needs written authorization (stated in-page, in Methods).
 */
@Composable
fun DorkPage() {
    var query by rememberSaveable { mutableStateOf("") }
    var engine by rememberSaveable { mutableStateOf(GoogleDork.Engine.GOOGLE) }
    var browserUrl by remember { mutableStateOf<String?>(null) }
    var expandedOp by remember { mutableStateOf<String?>(null) }
    var expandedMethod by remember { mutableStateOf<String?>(null) }

    fun runSearch(q: String) {
        if (q.isBlank()) return
        browserUrl = GoogleDork.buildSearchUrl(q.trim(), engine)
        C2State.audit("DORK", "search [${engine.label}]: ${q.trim().take(80)}")
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, null, tint = Cyan)
            Spacer(Modifier.width(8.dp))
            Text("Google Dorking", style = QuantumTypography.displaySmall, color = Cyan)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Advanced-operator reconnaissance: curated query packs, operator " +
                "reference, analyst tradecraft — results render in the in-app browser.",
            style = QuantumTypography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(12.dp))

        // ------------------------------------------------------------ run
        QCard(Modifier.fillMaxWidth(), title = "SEARCH") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                placeholder = {
                    Text(
                        "dork query — e.g. site:target.tld filetype:pdf",
                        style = QuantumTypography.bodySmall,
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                EnginePicker(engine) { engine = it }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { runSearch(query) },
                enabled = query.isNotBlank(),
            ) {
                Icon(Icons.Outlined.PlayArrow, null)
                Spacer(Modifier.width(4.dp))
                Text("SEARCH IN APP")
            }
            Text(
                "Google rate-limits automated-looking traffic; if it shows a " +
                    "consent or CAPTCHA page, answer it in the browser or " +
                    "switch engine — that is the engine's behavior, shown as-is.",
                style = QuantumTypography.labelSmall,
                color = Muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        // ---------------------------------------------------------- presets
        QCard(Modifier.fillMaxWidth(), title = "QUERY PACKS (${GoogleDork.PRESETS.size})") {
            GoogleDork.presetsByCategory().forEach { (category, presets) ->
                if (presets.isEmpty()) return@forEach
                Text(
                    category.uppercase().replace("-", " "),
                    style = QuantumTypography.labelMedium,
                    color = SoftCyan,
                    modifier = Modifier.padding(top = 6.dp),
                )
                presets.forEach { preset ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                preset.title,
                                style = QuantumTypography.labelLarge,
                                color = Softest,
                            )
                            Text(
                                preset.query,
                                style = QuantumTypography.labelSmall,
                                color = Muted,
                            )
                        }
                        TextButton(onClick = {
                            query = preset.query
                            runSearch(preset.query)
                        }) {
                            Text("RUN", color = Cyan)
                        }
                    }
                    if (preset.note.isNotBlank()) {
                        Text(
                            preset.note,
                            style = QuantumTypography.labelSmall,
                            color = Warn,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // -------------------------------------------------------- operators
        QCard(Modifier.fillMaxWidth(), title = "OPERATOR REFERENCE") {
            GoogleDork.OPERATORS.forEach { op ->
                val open = expandedOp == op.op
                TextButton(onClick = { expandedOp = if (open) null else op.op }) {
                    Text(
                        op.op,
                        color = if (open) Cyan else Softest,
                        style = QuantumTypography.labelLarge,
                    )
                }
                if (open) {
                    Text(op.desc, style = QuantumTypography.bodySmall, color = Softest)
                    Text(
                        "try: ${op.example}",
                        style = QuantumTypography.labelSmall,
                        color = SoftCyan,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    TextButton(onClick = {
                        query = op.example
                        runSearch(op.example)
                    }) {
                        Text("TRY EXAMPLE", color = Cyan)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // ---------------------------------------------------------- methods
        QCard(Modifier.fillMaxWidth(), title = "ANALYST METHODS") {
            Text(
                "Public OSINT tradecraft — how professionals work a dork list " +
                    "methodically. Generic methodology; no agency attribution.",
                style = QuantumTypography.labelSmall,
                color = Muted,
            )
            Spacer(Modifier.height(6.dp))
            GoogleDork.METHODS.forEach { method ->
                val open = expandedMethod == method.id
                TextButton(onClick = { expandedMethod = if (open) null else method.id }) {
                    Text(
                        method.title.uppercase(),
                        color = if (open) Cyan else Softest,
                        style = QuantumTypography.labelMedium,
                    )
                }
                if (open) {
                    Text(
                        method.body,
                        style = QuantumTypography.bodySmall,
                        color = Softest,
                    )
                    method.steps.forEachIndexed { i, step ->
                        Text(
                            "${i + 1}. $step",
                            style = QuantumTypography.bodySmall,
                            color = SoftCyan,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // ---------------------------------------------------------- browser
        browserUrl?.let { url ->
            DorkBrowserDialog(url = url, onClose = { browserUrl = null })
        }
    }
}

/** Engine selector row. */
@Composable
private fun EnginePicker(current: GoogleDork.Engine, onPick: (GoogleDork.Engine) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GoogleDork.Engine.entries.forEach { e ->
            val active = e == current
            if (active) {
                Button(onClick = { onPick(e) }) {
                    Text(e.label.uppercase(), style = QuantumTypography.labelSmall)
                }
            } else {
                OutlinedButton(onClick = { onPick(e) }) {
                    Text(e.label.uppercase(), style = QuantumTypography.labelSmall)
                }
            }
            Spacer(Modifier.width(6.dp))
        }
    }
}

/**
 * Full-screen in-app browser: back/forward/reload, live URL, close.
 * WebViewClient keeps every navigation inside the app (no external
 * browser hand-off). JavaScript is on (search engines need it); no JS
 * bridge is exposed.
 */
@Composable
private fun DorkBrowserDialog(url: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var currentUrl by remember { mutableStateOf(url) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { webView?.goBack() }) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Cyan)
                }
                IconButton(onClick = { webView?.goForward() }) {
                    Icon(Icons.Outlined.ArrowForward, contentDescription = "Forward", tint = Cyan)
                }
                IconButton(onClick = { webView?.reload() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Reload", tint = Cyan)
                }
                Text(
                    currentUrl.take(60),
                    style = QuantumTypography.labelSmall,
                    color = Muted,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Cyan)
                }
            }
            AndroidView(
                factory = { _ ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                if (finishedUrl != null) currentUrl = finishedUrl
                            }
                        }
                        loadUrl(url)
                        webView = this
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
            webView = null
        }
    }
}
