package com.projectzerodays.quantumcli.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash bookkeeping + self-repair hooks.
 *
 * [onUncaught] is invoked from the app's global uncaught-exception handler
 * BEFORE the platform handler: it persists the crash and marks repair work.
 * [heal] is invoked at the next boot for each named repair — a repair hook
 * must be idempotent and bounded (it runs inline during boot phases).
 */
object HealingDaemon {

    data class HealEvent(val ts: String, val name: String, val outcome: String)

    private lateinit var dir: File
    private val hooks = LinkedHashMap<String, () -> String>()

    private val _events = MutableStateFlow<List<HealEvent>>(emptyList())
    val events: StateFlow<List<HealEvent>> = _events.asStateFlow()

    fun init(baseDir: File) {
        dir = File(baseDir, "healing").apply { mkdirs() }
    }

    /** Register a named repair; runs at next boot in registration order. */
    fun registerRepair(name: String, hook: () -> String) {
        hooks[name] = hook
    }

    fun onUncaught(t: Throwable) {
        if (!::dir.isInitialized) return
        runCatching {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            File(dir, "last_crash.txt").writeText(
                "$stamp  ${t.javaClass.name}: ${t.message.orEmpty()}\n" +
                    t.stackTraceToString().take(8000)
            )
        }
    }

    /** Run pending repairs at boot; every outcome lands in the heal ledger. */
    fun healPending() {
        if (!::dir.isInitialized) return
        val ledger = mutableListOf<HealEvent>()
        val stamp: () -> String = {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        }
        for ((name, hook) in hooks) {
            val outcome = runCatching { hook() }
                .getOrElse { "repair '$name' itself failed: ${it.message.orEmpty()}" }
            ledger += HealEvent(stamp(), name, outcome)
        }
        if (ledger.isNotEmpty()) {
            val log = File(dir, "heals.log")
            log.appendText(ledger.joinToString("") { "${it.ts}|${it.name}|${it.outcome}\n" })
            _events.value = ledger
        }
    }
}
