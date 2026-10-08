package com.projectzerodays.quantumcli.ops

/**
 * The one-table ruleset engine: every autonomy-sensitive call site in the
 * app resolves its action through [DoctrineCodex.verdict] — no ad-hoc
 * booleans at call sites.
 *
 * Gate classes:
 *  - HUMAN_GATE  — the operator must approve; the app prepares, never acts.
 *  - AUTO_IF     — runs automatically iff the action's params stay inside
 *                  the rule's envelope; anything outside escalates to the
 *                  HUMAN_GATE verdict, it never fails open.
 *  - NOTIFY      — automatic, but the operator sees a notice.
 *  - SILENT      — automatic, audit-only.
 *
 * Evidence classes: what must accompany a verdict for it to be consumable.
 * A verdict whose provided evidence is weaker than required is a P1
 * finding, recorded, not silently downgraded.
 *
 * Pure JVM — no Android imports — unit-testable.
 */
object DoctrineCodex {

    enum class GateClass { HUMAN_GATE, AUTO_IF, NOTIFY, SILENT }

    enum class EvidenceClass(val rank: Int) {
        NONE(0), LOG(1), BUNDLE(2), SIGNED_BUNDLE(3);

        fun satisfies(required: EvidenceClass): Boolean = rank >= required.rank
    }

    /**
     * What settings/policy may widen or narrow for this rule. Widening is
     * allowed only along these axes — policy can narrow autonomy, never
     * raise a gate class or invent a new rule.
     */
    data class Envelope(
        val strictest: Boolean = false,        // wipe-class ops: no widening, ever
        val armedOnly: Boolean = false,        // only valid when the kill-switch is armed
        val quietHoursExempt: Boolean = false, // may run during quiet hours
        val requiresSignature: Boolean = false,// artifact must be signature-verified
        val requiresTests: String? = null,     // named test gate that must have passed
        val cadence: String? = null,           // e.g. "nightly", "weekly"
    )

    data class Rule(
        val id: String,
        val gate: GateClass,
        val envelope: Envelope,
        val evidenceReq: EvidenceClass,
    )

    /** Params a call site proposes for its action. */
    data class Params(
        val strictest: Boolean = false,
        val armed: Boolean = false,
        val quietHours: Boolean = false,
        val signatureVerified: Boolean = false,
        val testsPassed: String? = null,
        val cadence: String? = null,
    )

    /** THE table. docs/ledger.md and docs/HOWTOs.md render from this map. */
    val rules: Map<String, Rule> = mapOf(
        "killswitch.arm" to Rule(
            "killswitch.arm", GateClass.HUMAN_GATE,
            Envelope(strictest = true), EvidenceClass.SIGNED_BUNDLE,
        ),
        "wipe.any" to Rule(
            "wipe.any", GateClass.HUMAN_GATE,
            Envelope(strictest = true), EvidenceClass.SIGNED_BUNDLE,
        ),
        "payload.deploy" to Rule(
            "payload.deploy", GateClass.HUMAN_GATE,
            Envelope(strictest = true), EvidenceClass.SIGNED_BUNDLE,
        ),
        "exfil.push" to Rule(
            "exfil.push", GateClass.HUMAN_GATE,
            Envelope(strictest = true), EvidenceClass.BUNDLE,
        ),
        "remediation.apply" to Rule(
            "remediation.apply", GateClass.AUTO_IF,
            Envelope(requiresTests = "bench-review+gates"), EvidenceClass.BUNDLE,
        ),
        "federation.consume" to Rule(
            "federation.consume", GateClass.AUTO_IF,
            Envelope(requiresSignature = true), EvidenceClass.BUNDLE,
        ),
        "content.hotfix" to Rule(
            "content.hotfix", GateClass.AUTO_IF,
            Envelope(requiresSignature = true, quietHoursExempt = true), EvidenceClass.LOG,
        ),
        "maintenance.window" to Rule(
            "maintenance.window", GateClass.AUTO_IF,
            Envelope(cadence = "nightly", quietHoursExempt = true), EvidenceClass.LOG,
        ),
        "backlog.reconcile" to Rule(
            "backlog.reconcile", GateClass.AUTO_IF,
            Envelope(cadence = "weekly"), EvidenceClass.LOG,
        ),
        "triage.p0" to Rule(
            "triage.p0", GateClass.NOTIFY,
            Envelope(quietHoursExempt = true), EvidenceClass.NONE,
        ),
        "c2.server.start" to Rule(
            "c2.server.start", GateClass.NOTIFY,
            Envelope(), EvidenceClass.LOG,
        ),
        "overlord.cycle.start" to Rule(
            "overlord.cycle.start", GateClass.NOTIFY,
            Envelope(quietHoursExempt = true), EvidenceClass.LOG,
        ),
        "provider.registry.patch" to Rule(
            "provider.registry.patch", GateClass.AUTO_IF,
            Envelope(requiresTests = "self-test"), EvidenceClass.LOG,
        ),
    )

    sealed class Verdict {
        abstract val ruleId: String

        data class AutoRun(
            override val ruleId: String,
            val notice: String? = null,
        ) : Verdict()

        data class PendingApproval(override val ruleId: String) : Verdict()

        data class Violation(
            override val ruleId: String,
            val reason: String,
            val severity: String = "P1",
        ) : Verdict()
    }

    /**
     * Resolve one action. Unknown rule = violation (a bug by construction),
     * out-of-envelope params escalate to PendingApproval, insufficient
     * evidence downgrades AutoRun to a Violation — never the other way.
     */
    fun verdict(ruleId: String, params: Params, providedEvidence: EvidenceClass = EvidenceClass.NONE): Verdict {
        val rule = rules[ruleId]
            ?: return Verdict.Violation(ruleId, "unknown rule '$ruleId' — a bug by construction")

        if (!inEnvelope(rule, params)) {
            return if (rule.gate == GateClass.AUTO_IF) Verdict.PendingApproval(ruleId)
            else Verdict.Violation(ruleId, "params outside envelope for '$ruleId'")
        }

        val base: Verdict = when (rule.gate) {
            GateClass.HUMAN_GATE -> Verdict.PendingApproval(ruleId)
            GateClass.AUTO_IF -> Verdict.AutoRun(ruleId)
            GateClass.NOTIFY -> Verdict.AutoRun(ruleId, notice = "$ruleId ran automatically")
            GateClass.SILENT -> Verdict.AutoRun(ruleId)
        }

        if (base is Verdict.AutoRun && !providedEvidence.satisfies(rule.evidenceReq)) {
            return Verdict.Violation(
                ruleId,
                "evidence breach: ${rule.evidenceReq} required, $providedEvidence provided",
            )
        }
        return base
    }

    private fun inEnvelope(rule: Rule, p: Params): Boolean {
        val e = rule.envelope
        if (e.strictest && !p.strictest) return false
        if (e.armedOnly && !p.armed) return false
        if (!e.quietHoursExempt && p.quietHours) return false
        if (e.requiresSignature && !p.signatureVerified) return false
        if (e.requiresTests != null && p.testsPassed != e.requiresTests) return false
        if (e.cadence != null && p.cadence != e.cadence) return false
        return true
    }

    // ---- audit trail ------------------------------------------------------

    data class VerdictRecord(
        val ts: String,
        val ruleId: String,
        val kind: String,
        val detail: String,
    )

    private val trail = ArrayDeque<VerdictRecord>()

    /** Sink wired at boot to C2State.audit so verdicts land in the OPLOG. */
    @Volatile var sink: ((VerdictRecord) -> Unit)? = null

    @Synchronized
    fun record(v: Verdict): VerdictRecord {
        val rec = VerdictRecord(
            ts = java.time.Instant.now().toString(),
            ruleId = v.ruleId,
            kind = v::class.simpleName.orEmpty(),
            detail = when (v) {
                is Verdict.AutoRun -> v.notice ?: "auto"
                is Verdict.PendingApproval -> "awaiting operator"
                is Verdict.Violation -> "${v.severity}: ${v.reason}"
            },
        )
        trail.addLast(rec)
        while (trail.size > 500) trail.removeFirst()
        sink?.invoke(rec)
        return rec
    }

    @Synchronized
    fun auditTrail(): List<VerdictRecord> = trail.toList()

    @Synchronized
    fun resetTrail() = trail.clear()

    /** Convenience: resolve AND record in one call. */
    fun resolve(ruleId: String, params: Params, evidence: EvidenceClass = EvidenceClass.NONE): Verdict {
        val v = verdict(ruleId, params, evidence)
        record(v)
        return v
    }
}
