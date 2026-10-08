package com.projectzerodays.quantumcli.ops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * DoctrineCodex contract: policy can narrow autonomy, never widen it;
 * unknown rules and evidence breaches are violations, never default-passes.
 */
class DoctrineCodexTest {

    @Before
    fun reset() = DoctrineCodex.resetTrail()

    @Test
    fun `every rule id referenced in MaintenanceScheduler exists`() {
        // The scheduler's jobs resolve through the codex — their rule ids
        // must be in the table or every nightly run would be a P1.
        val referenced = listOf(
            "maintenance.window",
            "content.hotfix",
        )
        referenced.forEach { assertTrue("missing rule $it", DoctrineCodex.rules.containsKey(it)) }
    }

    @Test
    fun `human gates stay human even when params ask for auto`() {
        for (ruleId in listOf("wipe.any", "payload.deploy", "killswitch.arm", "exfil.push")) {
            val v = DoctrineCodex.verdict(
                ruleId,
                DoctrineCodex.Params(strictest = true, armed = true, signatureVerified = true),
            )
            assertTrue("$ruleId must remain human-gated", v is DoctrineCodex.Verdict.PendingApproval)
        }
    }

    @Test
    fun `auto_if runs only inside its envelope`() {
        val inside = DoctrineCodex.verdict(
            "federation.consume",
            DoctrineCodex.Params(signatureVerified = true),
            DoctrineCodex.EvidenceClass.BUNDLE,
        )
        assertTrue(inside is DoctrineCodex.Verdict.AutoRun)

        val unsigned = DoctrineCodex.verdict(
            "federation.consume",
            DoctrineCodex.Params(signatureVerified = false),
        )
        // Out of envelope on an AUTO_IF rule escalates, never fails open.
        assertTrue(unsigned is DoctrineCodex.Verdict.PendingApproval)
    }

    @Test
    fun `evidence breach on an auto rule is a P1 violation`() {
        val v = DoctrineCodex.resolve(
            "content.hotfix",
            DoctrineCodex.Params(signatureVerified = true),
            DoctrineCodex.EvidenceClass.NONE, // LOG required
        )
        assertTrue(v is DoctrineCodex.Verdict.Violation)
        assertEquals("P1", (v as DoctrineCodex.Verdict.Violation).severity)
    }

    @Test
    fun `unknown rule id is a violation not a default pass`() {
        val v = DoctrineCodex.verdict("not.a.rule", DoctrineCodex.Params())
        assertTrue(v is DoctrineCodex.Verdict.Violation)
    }

    @Test
    fun `strictest envelope cannot be widened by params`() {
        // Claiming wipe.any with non-strictest params is a construction bug —
        // a violation, not a downgrade to approval.
        val v = DoctrineCodex.verdict("wipe.any", DoctrineCodex.Params(strictest = false))
        assertTrue(v is DoctrineCodex.Verdict.Violation)
        assertTrue((v as DoctrineCodex.Verdict.Violation).reason.contains("outside envelope"))
    }

    @Test
    fun `verdicts are recorded to the audit trail`() {
        DoctrineCodex.resolve("triage.p0", DoctrineCodex.Params())
        val trail = DoctrineCodex.auditTrail()
        assertEquals(1, trail.size)
        assertEquals("triage.p0", trail.first().ruleId)
        assertEquals("AutoRun", trail.first().kind)
        assertTrue(trail.first().detail.contains("automatically"))
    }
}
