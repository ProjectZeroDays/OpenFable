package com.projectzerodays.quantumcli.docs

/**
 * The doc-that-cannot-lie: parses `docs/ledger.md` claim rows and checks
 * every FULL-grade claim against the set of live modules (FeatureRegistry).
 * A FULL claim naming a module that is not registered/live is a lint
 * failure — the ledger can only ever claim what actually exists.
 *
 * Pure JVM — used by unit tests (CI) and by the Diagnostics screen.
 */
object LedgerBindings {

    enum class Grade { FULL, BOUNDED, PLANNED }

    data class Binding(
        val requestKey: String,
        val moduleIds: List<String>,
        val grade: Grade,
    )

    sealed class LintResult {
        object Pass : LintResult()
        data class Fail(val failures: List<String>) : LintResult()
    }

    /**
     * Ledger row format (markdown table):
     * `| request-key | module-a, module-b | FULL | note |`
     * The note column is free text; grade defaults to PLANNED when blank.
     */
    fun parseClaimRows(ledgerMd: String): List<Binding> =
        ledgerMd.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("|") && !it.startsWith("|--") && !it.startsWith("| --") }
            .mapNotNull { line ->
                val cols = line.split("|").map { c -> c.trim() }.filter { it.isNotEmpty() }
                if (cols.size < 3 || cols[0] == "request-key") return@mapNotNull null
                Binding(
                    requestKey = cols[0],
                    moduleIds = cols[1].split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    grade = when (cols[2].uppercase()) {
                        "FULL" -> Grade.FULL
                        "BOUNDED" -> Grade.BOUNDED
                        else -> Grade.PLANNED
                    },
                )
            }
            .toList()

    /**
     * The CI lint: every FULL-grade claim must resolve to live modules.
     * BOUNDED claims name their live subset but may reference absent ones
     * (that's what bounded means); PLANNED claims prove nothing.
     */
    fun lintLedger(ledgerMd: String, liveModules: Set<String>): LintResult {
        val failures = parseClaimRows(ledgerMd).mapNotNull { claim ->
            if (claim.grade != Grade.FULL) return@mapNotNull null
            val missing = claim.moduleIds.filter { it !in liveModules }
            if (missing.isNotEmpty()) {
                "${claim.requestKey} claims FULL but modules not live: ${missing.joinToString()}"
            } else null
        }
        return if (failures.isEmpty()) LintResult.Pass else LintResult.Fail(failures)
    }
}
