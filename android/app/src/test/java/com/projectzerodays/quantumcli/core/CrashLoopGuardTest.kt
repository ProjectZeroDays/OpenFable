package com.projectzerodays.quantumcli.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Crash-loop detection: 3 incomplete boots in the window trip safe mode. */
class CrashLoopGuardTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun init() {
        CrashLoopGuard.resetJournal()
        CrashLoopGuard.init(tmp.root)
    }

    private fun crash() =
        CrashLoopGuard.recordCrash(Thread("test"), IllegalStateException("boom"))

    @Test
    fun `clean boots never trip safe mode`() {
        repeat(4) {
            CrashLoopGuard.markBootAttempt(CrashLoopGuard.BootState.BOOTING)
            CrashLoopGuard.markBootAttempt(CrashLoopGuard.BootState.BOOTED)
        }
        assertFalse(CrashLoopGuard.shouldSafeBoot())
    }

    @Test
    fun `three consecutive crashed boots trip safe mode`() {
        repeat(3) {
            CrashLoopGuard.markBootAttempt(CrashLoopGuard.BootState.BOOTING)
            crash()
        }
        assertTrue(CrashLoopGuard.shouldSafeBoot())
    }

    @Test
    fun `two crashes do not yet trip safe mode`() {
        repeat(2) {
            CrashLoopGuard.markBootAttempt(CrashLoopGuard.BootState.BOOTING)
            crash()
        }
        assertFalse(CrashLoopGuard.shouldSafeBoot())
    }

    @Test
    fun `a successful boot resets the window`() {
        repeat(2) {
            CrashLoopGuard.markBootAttempt(CrashLoopGuard.BootState.BOOTING)
            crash()
        }
        CrashLoopGuard.markBootAttempt(CrashLoopGuard.BootState.BOOTING)
        CrashLoopGuard.markBootAttempt(CrashLoopGuard.BootState.BOOTED)
        assertFalse("completed boot clears the streak", CrashLoopGuard.shouldSafeBoot())
    }

    @Test
    fun `boot failures count like crashes`() {
        repeat(3) {
            CrashLoopGuard.markBootAttempt(CrashLoopGuard.BootState.BOOTING)
            CrashLoopGuard.recordBootFailure("content", RuntimeException("assets missing"))
        }
        assertTrue(CrashLoopGuard.shouldSafeBoot())
    }

    @Test
    fun `reset clears the journal`() {
        repeat(3) {
            CrashLoopGuard.markBootAttempt(CrashLoopGuard.BootState.BOOTING)
            crash()
        }
        CrashLoopGuard.resetJournal()
        assertFalse(CrashLoopGuard.shouldSafeBoot())
        assertTrue(CrashLoopGuard.summary().contains("no boot journal"))
    }
}
