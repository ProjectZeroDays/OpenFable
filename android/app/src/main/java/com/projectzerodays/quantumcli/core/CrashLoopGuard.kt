package com.projectzerodays.quantumcli.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Boot journal + crash-loop safe-boot trigger.
 *
 * Appends one line per event to `<journalDir>/crash_journal.txt`:
 *   epochMillis|EVENT|detail
 *
 * Events: BOOTING (start of a launch), BOOTED (all phases finished),
 * CRASH (uncaught exception), BOOTFAIL (a BootPhase threw).
 *
 * `shouldSafeBoot()` returns true when the last 5 boot attempts contain
 * >= 3 BOOTING lines that never reached BOOTED (crash/boot-fail between
 * them). Once threshold trips, the operator sees [SafeModeActivity] on the
 * next launch; clearing the journal from there restores normal boot.
 *
 * Pure JVM — no Android imports, unit-testable with a temp dir.
 */
object CrashLoopGuard {

    enum class BootState { BOOTING, BOOTED }

    private const val MAX_LINES = 200
    private const val WINDOW = 5
    private const val SAFE_BOOT_THRESHOLD = 3

    private lateinit var journal: File

    @Synchronized
    fun init(journalDir: File) {
        journalDir.mkdirs()
        journal = File(journalDir, "crash_journal.txt")
    }

    @Synchronized
    fun markBootAttempt(state: BootState) {
        require(::journal.isInitialized) { "CrashLoopGuard.init not called" }
        append("BOOTING=$state")
        trim()
    }

    @Synchronized
    fun recordCrash(thread: Thread, e: Throwable) {
        append("CRASH|thread=${thread.name}|${e.javaClass.name}: ${e.message.orEmpty().take(160)}")
        trim()
    }

    @Synchronized
    fun recordBootFailure(phase: String, e: Throwable) {
        append("BOOTFAIL|phase=$phase|${e.javaClass.name}: ${e.message.orEmpty().take(160)}")
        trim()
    }

    @Synchronized
    fun resetJournal() {
        if (::journal.isInitialized) journal.delete()
    }

    /** True when the recent window shows >= threshold boots that never completed. */
    @Synchronized
    fun shouldSafeBoot(): Boolean {
        if (!::journal.isInitialized || !journal.exists()) return false
        // Newest-first: a boot "failed" iff a CRASH/BOOTFAIL line appears
        // between its BOOTING and the next newer BOOTING. The current run's
        // own BOOTING (newest, no crash after it yet) is never self-counted.
        // Journal lines are "epochMillis|EVENT|detail" - strip the timestamp
        // before matching. Only the last [WINDOW] boot attempts count.
        var incompleteBoot = false
        var failed = 0
        var bootsSeen = 0
        for (raw in journal.readLines().reversed()) {
            val line = raw.substringAfter('|')
            when {
                line.startsWith("BOOTING=BOOTED") -> bootsSeen++
                line.startsWith("BOOTING=BOOTING") -> {
                    bootsSeen++
                    if (incompleteBoot) failed++
                    incompleteBoot = false
                }
                line.startsWith("CRASH") || line.startsWith("BOOTFAIL") -> incompleteBoot = true
            }
            if (bootsSeen >= WINDOW) break
        }
        return failed >= SAFE_BOOT_THRESHOLD
    }

    /** Human-readable summary for the safe-mode screen. */
    fun summary(): String {
        if (!::journal.isInitialized || !journal.exists()) return "no boot journal"
        return journal.readLines().takeLast(20).joinToString("\n")
    }

    private fun append(line: String) {
        val ts = System.currentTimeMillis()
        journal.appendText("$ts|$line\n")
    }

    private fun trim() {
        val lines = journal.readLines()
        if (lines.size > MAX_LINES) journal.writeText(lines.takeLast(MAX_LINES).joinToString("\n") + "\n")
    }

    fun timestampOf(line: String): String = runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            .format(Date(line.substringBefore('|').toLong()))
    }.getOrDefault("?")
}
