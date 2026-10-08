package com.projectzerodays.quantumcli.ops

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import com.projectzerodays.quantumcli.core.CrashLoopGuard
import com.projectzerodays.quantumcli.education.ContentRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * One unit of nightly maintenance. Families run inside a scored window
 * (quiet hours + charging + idle); a live Overlord cycle always preempts —
 * maintenance yields to operations, never the reverse.
 */
interface MaintenanceJob {
    val family: String
    /** DoctrineCodex rule this job's execution resolves under. */
    val codexRule: String
    fun preconditions(): Boolean
    fun run(budgetMs: Long): String
}

/** Cap the heal ledger + crash journal so diagnostics files stay bounded. */
object LogRotationJob : MaintenanceJob {
    override val family = "log-rotation"
    override val codexRule = "maintenance.window"
    override fun preconditions() = true
    override fun run(budgetMs: Long): String {
        var trimmed = 0
        val cap = 200
        val roots = listOf(
            com.projectzerodays.quantumcli.QuantumApp.ctx.let {
                File(it.filesDir, "healing/heals.log")
            },
        )
        for (f in roots) {
            if (f.isFile) {
                val lines = f.readLines()
                if (lines.size > cap) {
                    f.writeText(lines.takeLast(cap).joinToString("\n") + "\n")
                    trimmed++
                }
            }
        }
        return if (trimmed > 0) "rotated $trimmed file(s)" else "nothing to rotate"
    }
}

/** Abandoned partial hot-fix staging (>1 day old) is wreckage; sweep it. */
object HotFixStagingJob : MaintenanceJob {
    override val family = "content-hotfix"
    override val codexRule = "content.hotfix"
    override fun preconditions() = true
    override fun run(budgetMs: Long): String {
        val staged = File(
            com.projectzerodays.quantumcli.QuantumApp.ctx.filesDir,
            "content_hotfix/staged",
        )
        if (!staged.isDirectory) return "no staging dir"
        if (System.currentTimeMillis() - staged.lastModified() > 24L * 60 * 60 * 1000) {
            staged.deleteRecursively()
            return "swept stale staging"
        }
        return "staging clean"
    }
}

/**
 * The signed hot-fix channel check itself (a no-op unless the operator
 * configured a channel URL — fail closed by default).
 */
object ContentHotFixJob : MaintenanceJob {
    override val family = "content-channel"
    override val codexRule = "content.hotfix"
    override fun preconditions() = ContentRegistry.channelConfigured()
    override fun run(budgetMs: Long): String =
        kotlinx.coroutines.runBlocking { ContentRegistry.checkHotFix() }
}

data class HorizonEntry(
    val family: String,
    val path: String,
    val ageDays: Int,
    val warnDays: Int,
)

/**
 * Hourly catalog refresh (v4.4): pulls the live Exploit-DB CSV and the
 * CISA KEV json into filesDir/catalog/. Failures are silent-by-design —
 * the bundled seed stays authoritative until a fresh copy lands.
 */
object CatalogSync {
    internal const val EDB_URL =
        "https://gitlab.com/exploit-database/exploitdb/-/raw/main/files_exploits.csv"
    internal const val KEV_URL =
        "https://raw.githubusercontent.com/cisagov/kev-data/main/known_exploited_vulnerabilities.json"

    fun syncOnce(ctx: Context): String {
        var synced = 0
        var failed = 0
        val dir = File(ctx.filesDir, "catalog").apply { mkdirs() }
        // Exploit-DB: stream -> gzip -> file
        try {
            val r = Net.httpGet(EDB_URL, timeoutMs = 120_000)
            if (r.error == null && r.body.size > 1_000_000) {
                val gz = File(dir, "files_exploits.csv.gz")
                val tmp = File(dir, "files_exploits.csv.gz.tmp")
                java.util.zip.GZIPOutputStream(tmp.outputStream()).use { it.write(r.body) }
                if (tmp.length() > 100_000) {
                    tmp.renameTo(gz)
                    synced++
                } else {
                    tmp.delete()
                }
            } else failed++
        } catch (e: Exception) {
            failed++
        }
        // KEV: straight JSON download
        try {
            val r = Net.httpGet(KEV_URL, timeoutMs = 60_000)
            if (r.error == null && r.body.size > 50_000) {
                File(dir, "kev.json").writeBytes(r.body)
                synced++
            } else failed++
        } catch (e: Exception) {
            failed++
        }
        return "catalog sync: $synced ok, $failed failed"
    }
}

/**
 * Pattern-observant nightly maintenance.
 *
 * Windows are scored over the next 24h in 30-minute slots: quiet hours
 * (22:00–07:00 local) +2, device charging +2, OS idle +1. The first slot
 * scoring >= 3 wins; with nothing qualifying the run lands at 03:30. Every
 * job resolves its DoctrineCodex rule before executing; any P1 finding is
 * recorded rather than swallowed.
 */
object MaintenanceScheduler {

    private lateinit var appCtx: Context
    private lateinit var scope: CoroutineScope
    private var jobs: List<MaintenanceJob> = emptyList()

    fun init(context: Context, scope: CoroutineScope, jobs: List<MaintenanceJob>) {
        appCtx = context.applicationContext
        this.scope = scope
        this.jobs = jobs
    }

    fun armNightly() {
        if (!::scope.isInitialized) return
        scope.launch {
            while (isActive) {
                val window = pickWindow()
                delay(window.delayMs)
                runWindow(budgetMs = window.durationMs)
                // Sleep through the rest of tonight so one check-in == one run.
                delay(22L * 60 * 60 * 1000)
            }
        }
    }

    /** Hourly catalog refresh (Exploit-DB + KEV) — seed stays authoritative
     *  on failure, so a dead network degrades gracefully. */
    fun armHourlyCatalogSync() {
        if (!::scope.isInitialized) return
        scope.launch {
            while (isActive) {
                runCatching { CatalogSync.syncOnce(appCtx) }
                    .onSuccess { audit("hourly $it") }
                    .onFailure { audit("hourly catalog sync failed: ${it.message?.take(80)}") }
                delay(60L * 60 * 1000)
            }
        }
    }

    data class Window(val delayMs: Long, val durationMs: Long, val score: Int, val startsAt: String)

    fun pickWindow(): Window {
        val bm = appCtx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pm = appCtx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val now = System.currentTimeMillis()
        var best: Window? = null
        for (slot in 0 until 48) { // next 24h, 30-minute slots
            val start = now + slot * 30L * 60 * 1000
            val cal = Calendar.getInstance().apply { timeInMillis = start }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val quiet = hour >= 22 || hour < 7
            // Charging/idle are sampled now; the battery gate re-checks at execution.
            val charging = bm.isCharging
            val idle = pm.isDeviceIdleMode
            var score = 0
            if (quiet) score += 2
            if (charging) score += 2
            if (idle) score += 1
            if (score >= 3 && best == null) {
                best = Window(
                    delayMs = start - now,
                    durationMs = 25L * 60 * 1000,
                    score = score,
                    startsAt = SimpleDateFormat("HH:mm", Locale.US).format(cal.time),
                )
            }
        }
        // Fallback: tonight at 03:30 regardless — maintenance still happens.
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }
        val fallback = Window(
            delayMs = cal.timeInMillis - now,
            durationMs = 25L * 60 * 1000,
            score = if (isQuietHour()) 2 else 0,
            startsAt = SimpleDateFormat("HH:mm", Locale.US).format(cal.time),
        )
        return best ?: fallback
    }

    /** Execute all eligible jobs in window-budget order. Returns summaries. */
    fun runWindow(budgetMs: Long = 25L * 60 * 1000): List<String> {
        if (!::appCtx.isInitialized) return emptyList()
        val summaries = mutableListOf<String>()
        val deadline = System.currentTimeMillis() + budgetMs
        for (job in jobs) {
            if (System.currentTimeMillis() > deadline) break
            // Live operations always win: an Overlord cycle preempts everything.
            if (com.projectzerodays.quantumcli.ops.Overlord.running.value) {
                summaries += "${job.family}: skipped — live Overlord cycle owns the device"
                continue
            }
            if (!job.preconditions()) {
                summaries += "${job.family}: preconditions unmet"
                continue
            }
            val verdict = DoctrineCodex.resolve(
                job.codexRule,
                DoctrineCodex.Params(cadence = "nightly", quietHours = true),
                DoctrineCodex.EvidenceClass.LOG,
            )
            if (verdict !is DoctrineCodex.Verdict.AutoRun) {
                summaries += "${job.family}: withheld — ${verdict::class.simpleName}"
                continue
            }
            val outcome = runCatching { job.run(deadline - System.currentTimeMillis()) }
                .getOrElse { "failed: ${it.message.orEmpty().take(120)}" }
            summaries += "${job.family}: $outcome"
        }
        audit("maintenance window: ${summaries.joinToString(" | ")}")
        return summaries
    }

    /**
     * Horizons board source: long-stale loot artifacts (56+ days old,
     * 7× the 8-day warn lead) — report-only, never a delete.
     */
    fun horizon(warnDays: Int = 7): List<HorizonEntry> {
        if (!::appCtx.isInitialized) return emptyList()
        val lootDir = File(appCtx.filesDir, "loot")
        if (!lootDir.isDirectory) return emptyList()
        val cutoff = warnDays * 24L * 60 * 60 * 1000
        return lootDir.walkTopDown().filter { it.isFile }
            .filter { System.currentTimeMillis() - it.lastModified() >= cutoff * 8 } // 56d+: aging
            .map {
                HorizonEntry(
                    family = "loot",
                    path = it.relativeTo(appCtx.filesDir).path,
                    ageDays = ((System.currentTimeMillis() - it.lastModified()) / 86_400_000L).toInt(),
                    warnDays = warnDays,
                )
            }
            .toList()
    }

    private fun isQuietHour(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 22 || hour < 7
    }

    private fun audit(msg: String) {
        runCatching { com.projectzerodays.quantumcli.c2.C2State.audit("Maintenance", msg) }
    }
}
