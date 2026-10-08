package com.projectzerodays.quantumcli.selfhealing

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HealingDaemonTest {

    private class FakeHealer(
        override val failureClass: FailureClass,
        detect: Boolean,
        private val result: HealResult = HealResult(true, "healed"),
    ) : Healer {
        override val settingsKey: String = "heal.test.${failureClass.name.lowercase()}"
        var detectAnswer = detect
        var detectCalls = 0
        var healCalls = 0
        var throwOnDetect = false

        override suspend fun detect(): Boolean {
            detectCalls++
            if (throwOnDetect) error("detector exploded")
            return detectAnswer
        }

        override suspend fun heal(): HealResult {
            healCalls++
            return result
        }
    }

    @After
    fun cleanup() {
        HealerHealth.clear()
    }

    @Test
    fun healthyHealersProduceNoEvents() = runBlocking {
        val h = FakeHealer(FailureClass.NETWORK_FLAKY, detect = false)
        val events = HealingDaemon(listOf(h)).healOnce()
        assertTrue(events.isEmpty())
        assertEquals(1, h.detectCalls)
        assertEquals(0, h.healCalls)
    }

    @Test
    fun unhealthyHealerIsDetectedAndHealed() = runBlocking {
        val h = FakeHealer(FailureClass.PROVIDER_OUTAGE, detect = true)
        val events = HealingDaemon(listOf(h)).healOnce()
        assertEquals(1, events.size)
        assertEquals(FailureClass.PROVIDER_OUTAGE, events[0].failureClass)
        assertTrue(events[0].result.succeeded)
        assertEquals(1, h.healCalls)
        // success restores any dim
        HealerHealth.register(FailureClass.PROVIDER_OUTAGE.name)
        HealerHealth.dim(FailureClass.PROVIDER_OUTAGE.name, "flapping")
        HealingDaemon(listOf(h)).healOnce()
        assertEquals(
            HealerHealth.Health.OK,
            HealerHealth.stateOf(FailureClass.PROVIDER_OUTAGE.name)!!.health,
        )
    }

    @Test
    fun failedHealDimsFeatureWithReason() = runBlocking {
        val h = FakeHealer(
            FailureClass.MODEL_CORRUPTION,
            detect = true,
            result = HealResult(false, "still corrupt", changedState = "model quarantined"),
        )
        HealingDaemon(listOf(h)).healOnce()
        val state = HealerHealth.stateOf(FailureClass.MODEL_CORRUPTION.name)
        assertNotNull(state)
        assertEquals(HealerHealth.Health.DIM, state!!.health)
        assertEquals("model quarantined", state.reason)
    }

    @Test
    fun disabledAutoHealSkipsHealAndReports() = runBlocking {
        val h = FakeHealer(FailureClass.DOWNLOAD_STALL, detect = true)
        val daemon = HealingDaemon(listOf(h)) { false } // auto-heal off
        val events = daemon.healOnce()
        assertEquals(1, events.size)
        assertFalse(events[0].result.succeeded)
        assertTrue(events[0].result.detail.contains("auto-heal disabled"))
        assertEquals(0, h.healCalls)
    }

    @Test
    fun detectorErrorBecomesFailureEventNotACrash() = runBlocking {
        val h = FakeHealer(FailureClass.WIDGET_CRASH, detect = true)
        h.throwOnDetect = true
        val events = HealingDaemon(listOf(h)).healOnce()
        assertEquals(1, events.size)
        assertFalse(events[0].result.succeeded)
        assertTrue(events[0].result.detail.contains("detector error"))
    }

    @Test
    fun eventsFlowReceivesEmittedEvents() = runBlocking {
        val h = FakeHealer(FailureClass.CRASH_LOOP_BOOT, detect = true)
        val daemon = HealingDaemon(listOf(h))
        val seen = mutableListOf<HealEvent>()
        val job = launch {
            daemon.events.collect { seen.add(it) }
        }
        delay(50) // let the collector subscribe
        daemon.healOnce()
        delay(50)
        job.cancel()
        assertEquals(1, seen.size)
        assertEquals(FailureClass.CRASH_LOOP_BOOT, seen[0].failureClass)
    }
}

class CrashLoopGuardTest {

    @Test
    fun threeFailedBootsEnterSafeMode() {
        val guard = CrashLoopGuard(CrashLoopGuard.InMemoryStrikeStore())
        assertFalse(guard.shouldEnterSafeMode())
        assertEquals(1, guard.recordBoot(bootSucceeded = false))
        assertEquals(2, guard.recordBoot(bootSucceeded = false))
        assertEquals(3, guard.recordBoot(bootSucceeded = false))
        assertTrue(guard.shouldEnterSafeMode())
        // capped at the limit
        assertEquals(3, guard.recordBoot(bootSucceeded = false))
    }

    @Test
    fun successfulBootResetsStrikes() {
        val guard = CrashLoopGuard(CrashLoopGuard.InMemoryStrikeStore())
        guard.recordBoot(false)
        guard.recordBoot(false)
        assertEquals(0, guard.recordBoot(true))
        assertFalse(guard.shouldEnterSafeMode())
    }

    @Test
    fun strikesPersistThroughFileStore() {
        val dir = File(System.getProperty("java.io.tmpdir"), "qcli-cl-guard-test")
        dir.mkdirs()
        val f = File(dir, "strikes.txt")
        f.delete()
        val g1 = CrashLoopGuard(CrashLoopGuard.FileStrikeStore(f))
        g1.recordBoot(false)
        g1.recordBoot(false)
        // simulated process restart — fresh guard, same file
        val g2 = CrashLoopGuard(CrashLoopGuard.FileStrikeStore(f))
        assertEquals(2, g2.storeStrikes())
        assertEquals(3, g2.recordBoot(false))
        assertTrue(g2.shouldEnterSafeMode())
        f.delete()
    }

    private fun CrashLoopGuard.storeStrikes(): Int =
        // test-visible peek through a fresh read
        CrashLoopGuard.FileStrikeStore(File(System.getProperty("java.io.tmpdir"), "qcli-cl-guard-test/strikes.txt")).strikes()

    @Test
    fun corruptStrikeFileFailsOpen() {
        val dir = File(System.getProperty("java.io.tmpdir"), "qcli-cl-guard-test")
        dir.mkdirs()
        val f = File(dir, "corrupt.txt")
        f.writeText("not-a-number")
        val guard = CrashLoopGuard(CrashLoopGuard.FileStrikeStore(f))
        assertFalse(guard.shouldEnterSafeMode())
        assertEquals(1, guard.recordBoot(false))
        f.delete()
    }
}
