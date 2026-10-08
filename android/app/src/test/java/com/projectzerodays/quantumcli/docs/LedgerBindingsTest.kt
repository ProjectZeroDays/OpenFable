package com.projectzerodays.quantumcli.docs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class LedgerBindingsTest {

    @Test
    fun `parses claim rows and ignores header and separator`() {
        val md = """
            | request-key | modules | grade | note |
            |---|---|---|---|
            | recon.sweep | c2.server, network | FULL | core |
            | hotfix.channel | education | BOUNDED | opt-in |
            | exfil.push | loot | PLANNED | human-gated |
        """.trimIndent()
        val claims = LedgerBindings.parseClaimRows(md)
        assertEquals(3, claims.size)
        assertEquals("recon.sweep", claims[0].requestKey)
        assertEquals(listOf("c2.server", "network"), claims[0].moduleIds)
        assertEquals(LedgerBindings.Grade.FULL, claims[0].grade)
        assertEquals(LedgerBindings.Grade.PLANNED, claims[2].grade)
    }

    @Test
    fun `full claim with missing module fails lint`() {
        val md = """
            | request-key | modules | grade | note |
            |---|---|---|---|
            | recon.sweep | c2.server, ghost.module | FULL | |
        """.trimIndent()
        val result = LedgerBindings.lintLedger(md, setOf("c2.server"))
        assertTrue(result is LedgerBindings.LintResult.Fail)
        val fail = result as LedgerBindings.LintResult.Fail
        assertTrue(fail.failures.single().contains("ghost.module"))
    }

    @Test
    fun `full claim with all modules live passes`() {
        val md = """
            | request-key | modules | grade | note |
            |---|---|---|---|
            | ai.deterministic | ai.client | FULL | offline path |
        """.trimIndent()
        assertEquals(
            LedgerBindings.LintResult.Pass,
            LedgerBindings.lintLedger(md, setOf("ai.client")),
        )
    }

    @Test
    fun `bounded and planned claims never fail lint`() {
        val md = """
            | request-key | modules | grade | note |
            |---|---|---|---|
            | hotfix.channel | education, not.registered.yet | BOUNDED | |
            | exfil.push | loot | PLANNED | |
        """.trimIndent()
        assertEquals(LedgerBindings.LintResult.Pass, LedgerBindings.lintLedger(md, emptySet()))
    }

    /**
     * The real ledger in the repo lints against the module ids the app
     * registers at boot. Skipped (not failed) when the checkout layout
     * hides docs/ledger.md from the test working directory.
     */
    @Test
    fun `repo ledger is consistent with the health-mesh registry`() {
        val candidates = listOf(
            File("../../docs/ledger.md"),
            File("../docs/ledger.md"),
            File("docs/ledger.md"),
        )
        val ledger = candidates.firstOrNull { it.isFile }
        assumeTrue("docs/ledger.md not reachable from test cwd", ledger != null)

        // Module ids QuantumApp.initHealthMesh() registers.
        val live = setOf(
            "c2.server", "overlord", "cameras", "network", "ai.client",
            "loot", "federation", "foxacid", "widgets", "education",
        )
        val result = LedgerBindings.lintLedger(ledger!!.readText(), live)
        assertTrue(
            "ledger lint failures: ${(result as? LedgerBindings.LintResult.Fail)?.failures}",
            result is LedgerBindings.LintResult.Pass,
        )
    }
}
