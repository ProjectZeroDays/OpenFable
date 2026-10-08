package com.projectzerodays.quantumcli.toolbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolBridgeTest {

    private class FakeProbes(
        var root: Boolean = true,
        var driver: Boolean = true,
        var hardware: Boolean = true,
        var missingPkgs: List<String> = emptyList(),
    ) : ToolBridge.Probes {
        override fun rootAvailable(): Boolean = root
        override fun loadDriver(soName: String): Boolean = driver
        override fun hardwarePresent(hardwareId: String): Boolean = hardware
        override fun missingPackages(packages: List<String>): List<String> =
            missingPkgs.filter { it in packages }
    }

    private val cleanTool = Tool(
        id = "nmap",
        termuxPackages = listOf("nmap"),
        requiresRoot = false,
    )

    @Test
    fun cleanPreflightSucceedsAndMarksVerified() {
        val bridge = ToolBridge(FakeProbes())
        val res = bridge.preFlight(cleanTool)
        assertTrue(res is ToolExecution.Success)
        assertTrue(bridge.wasVerified("nmap"))
        assertFalse(bridge.wasVerified("hashcat"))
    }

    @Test
    fun missingRootFailsWithRequestRootFix() {
        val bridge = ToolBridge(FakeProbes(root = false))
        val res = bridge.preFlight(cleanTool.copy(id = "tcpdump", requiresRoot = true))
        assertTrue(res is ToolExecution.Failure)
        res as ToolExecution.Failure
        assertEquals(PreFlightFailure.NO_ROOT, res.reason)
        assertEquals(FixAction.RequestRoot, res.fixAction)
        assertFalse(bridge.wasVerified("tcpdump"))
    }

    @Test
    fun driverLoadFailureMapsToDriverDiag() {
        val bridge = ToolBridge(FakeProbes(driver = false))
        val res = bridge.preFlight(cleanTool.copy(id = "rtl", driverSo = "librtlsdr.so"))
        assertTrue(res is ToolExecution.Failure)
        assertEquals(PreFlightFailure.DRIVER, (res as ToolExecution.Failure).reason)
        assertEquals(FixAction.OpenDriverDiag, res.fixAction)
    }

    @Test
    fun absentHardwareMapsToAttachHardware() {
        val bridge = ToolBridge(FakeProbes(hardware = false))
        val res = bridge.preFlight(cleanTool.copy(id = "sdr", requiresHardware = "rtl-sdr"))
        assertTrue(res is ToolExecution.Failure)
        assertEquals(PreFlightFailure.HW_ABSENT, (res as ToolExecution.Failure).reason)
        assertEquals(FixAction.AttachHardware, res.fixAction)
    }

    @Test
    fun missingTermuxPackageMapsToInstallAction() {
        val bridge = ToolBridge(FakeProbes(missingPkgs = listOf("hydra")))
        val res = bridge.preFlight(cleanTool.copy(id = "hydra", termuxPackages = listOf("hydra")))
        assertTrue(res is ToolExecution.Failure)
        res as ToolExecution.Failure
        assertEquals(PreFlightFailure.PKG_MISSING, res.reason)
        assertEquals(FixAction.InstallTermuxPackage("hydra"), res.fixAction)
    }

    @Test
    fun execWithoutExecutorFailsHonestly() {
        val bridge = ToolBridge(FakeProbes())
        val res = bridge.exec(cleanTool, listOf("-sV", "10.0.0.1"))
        assertTrue(res is ToolExecution.Failure)
        assertEquals(PreFlightFailure.NO_EXECUTOR, (res as ToolExecution.Failure).reason)
    }

    @Test
    fun execDelegatesToBoundExecutorAfterPreflight() {
        var seenArgs: List<String>? = null
        val executor = object : ToolBridge.Executor {
            override fun run(tool: Tool, args: List<String>): ToolExecution {
                seenArgs = args
                return ToolExecution.Success("PORT 22/tcp open", "", 0)
            }
        }
        val bridge = ToolBridge(FakeProbes(), executor)
        val res = bridge.exec(cleanTool, listOf("-p", "1-1024"))
        assertTrue(res is ToolExecution.Success)
        assertEquals(listOf("-p", "1-1024"), seenArgs)
    }

    @Test
    fun preflightFailureShortCircuitsExecutor() {
        var executed = false
        val executor = object : ToolBridge.Executor {
            override fun run(tool: Tool, args: List<String>): ToolExecution {
                executed = true
                return ToolExecution.Success("", "", 0)
            }
        }
        val bridge = ToolBridge(FakeProbes(root = false), executor)
        val res = bridge.exec(cleanTool.copy(requiresRoot = true))
        assertTrue(res is ToolExecution.Failure)
        assertEquals(PreFlightFailure.NO_ROOT, (res as ToolExecution.Failure).reason)
        assertFalse("executor must not run when pre-flight fails", executed)
    }
}
