package com.projectzerodays.quantumcli

import android.app.Application
import android.content.Context
import android.content.Intent
import com.projectzerodays.quantumcli.c2.C2State
import com.projectzerodays.quantumcli.c2.QuantServerManager
import com.projectzerodays.quantumcli.core.BootPhase
import com.projectzerodays.quantumcli.core.CrashLoopGuard
import com.projectzerodays.quantumcli.core.HealingDaemon
import com.projectzerodays.quantumcli.core.SafeModeActivity
import com.projectzerodays.quantumcli.data.QuantSettings
import com.projectzerodays.quantumcli.education.ContentRegistry
import com.projectzerodays.quantumcli.education.WalkthroughEngine
import com.projectzerodays.quantumcli.ops.ContentHotFixJob
import com.projectzerodays.quantumcli.ops.DoctrineCodex
import com.projectzerodays.quantumcli.ops.HotFixStagingJob
import com.projectzerodays.quantumcli.ops.LogRotationJob
import com.projectzerodays.quantumcli.ops.MaintenanceScheduler
import com.projectzerodays.quantumcli.selfhealing.FeatureRegistry
import com.projectzerodays.quantumcli.ui.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Boot spine. Governing order — every phase is isolated so a half-boot
 * escalates to safe mode on the NEXT launch instead of a crash loop now:
 *
 *   guard journal -> uncaught handler -> safe-boot gate ->
 *   [1 c2-state, 2 settings, 3 notifications, 4 content, 5 health-mesh,
 *    6 walkthroughs, 7 beacon, 8 schedulers] -> heal pending -> BOOTED
 */
class QuantumApp : Application() {

    companion object {
        lateinit var ctx: Context
            private set

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onCreate() {
        super.onCreate()
        ctx = this

        CrashLoopGuard.init(filesDir)
        HealingDaemon.init(filesDir)
        registerRepairs()

        CrashLoopGuard.markBootAttempt(CrashLoopGuard.BootState.BOOTING)
        val prior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            CrashLoopGuard.recordCrash(t, e)
            HealingDaemon.onUncaught(e)
            prior?.uncaughtException(t, e)
        }

        // Safe mode: core diagnostics only. No subsystem boot phases run.
        if (CrashLoopGuard.shouldSafeBoot()) {
            startActivity(
                Intent(this, SafeModeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }

        val phases: List<BootPhase> = listOf(
            phase(1, "c2-state") { C2State.init(filesDir) },
            phase(2, "settings") { QuantSettings.init(this) },
            phase(3, "notifications") { Notifications.createChannel(this) },
            phase(4, "content") {
                ContentRegistry.init(this, QuantSettings.state.value.hotFixChannel)
            },
            phase(5, "health-mesh") { initHealthMesh() },
            phase(6, "walkthroughs") { WalkthroughEngine.init(this) },
            phase(7, "beacon") {
                if (QuantSettings.state.value.autostart) {
                    val port = QuantSettings.state.value.c2Port
                    if (QuantServerManager.start(port)) {
                        Notifications.serverRunning(this, port)
                        WalkthroughEngine.triggered("c2.server.started")
                    }
                }
            },
            phase(8, "schedulers") {
                MaintenanceScheduler.init(
                    this,
                    appScope,
                    listOf(LogRotationJob, HotFixStagingJob, ContentHotFixJob),
                )
                MaintenanceScheduler.armNightly()
                MaintenanceScheduler.armHourlyCatalogSync()
            },
        )
        for (bp in phases) {
            runCatching { bp.run(this) }
                .onFailure {
                    CrashLoopGuard.recordBootFailure(bp.name, it)
                    android.util.Log.e("QuantumApp", "boot phase '${bp.name}' failed", it)
                }
        }
        HealingDaemon.healPending()
        CrashLoopGuard.markBootAttempt(CrashLoopGuard.BootState.BOOTED)

        // Codex verdicts land in the OPLOG once the audit sink exists.
        DoctrineCodex.sink = { rec ->
            runCatching { C2State.audit("Codex", "${rec.ruleId} -> ${rec.kind} ${rec.detail}") }
        }
        C2State.audit("C2", "QUANTUM-CLI ${C2State.VERSION} (android) initialized — flavor ${BuildConfig.FLAVOR}")
    }

    private fun phase(n: Int, name: String, block: (Context) -> Unit): BootPhase =
        object : BootPhase {
            override val phase = n
            override val name = name
            override fun run(ctx: Context) = block(ctx)
        }

    /**
     * Health-mesh registration: every module here matches a row in
     * docs/ledger.md (the ledger lint checks the FULL ones against this
     * registry — module ids are the ledger's contract).
     */
    private fun initHealthMesh() {
        FeatureRegistry.init(this)
        FeatureRegistry.register("c2.server", listOf("c2", "dashboard"))
        FeatureRegistry.register("overlord", listOf("autorun"))
        FeatureRegistry.register("cameras", listOf("cameras"))
        FeatureRegistry.register("network", listOf("network", "brute"))
        FeatureRegistry.register("ai.client", listOf("chat"))
        FeatureRegistry.register("loot", listOf("loot"))
        FeatureRegistry.register("federation", listOf("federation"))
        FeatureRegistry.register("foxacid", listOf("foxacid"))
        FeatureRegistry.register("widgets", listOf("dashboard"))
        FeatureRegistry.register("education", listOf("walkthroughs"))
    }

    /** Idempotent boot-time repairs — bounded, never journal-resetting. */
    private fun registerRepairs() {
        HealingDaemon.registerRepair("sweep-staged-content") {
            val staged = File(filesDir, "content_hotfix/staged")
            if (staged.isDirectory && staged.deleteRecursively()) "staged pack swept" else "staging clean"
        }
    }
}
