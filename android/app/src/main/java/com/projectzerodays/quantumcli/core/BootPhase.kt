package com.projectzerodays.quantumcli.core

import android.content.Context

/**
 * One ordered step of the boot spine (QuantumApp.onCreate). Lower [phase]
 * numbers run first; every phase is isolated — a thrown failure is recorded
 * by [CrashLoopGuard.recordBootFailure] and never takes the launch down.
 */
interface BootPhase {
    val phase: Int
    val name: String
    fun run(ctx: Context)
}
