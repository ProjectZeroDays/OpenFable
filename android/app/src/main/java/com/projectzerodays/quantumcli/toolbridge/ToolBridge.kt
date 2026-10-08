package com.projectzerodays.quantumcli.toolbridge

/**
 * Uniform tool execution with pre-flight — the health gate every module's
 * status light reads before shelling out to nmap/hashcat/Termux tools/etc.
 *
 * Honest scope: [preFlight] is fully implemented against the injectable
 * [Probes]; [exec] runs a bound executor (Termux session/streaming lands with
 * the JobCard UX in a later sprint) and fails honestly with
 * [PreFlightFailure.NO_EXECUTOR] when no executor is bound — it never fakes
 * a success.
 */
enum class PreFlightFailure { NO_ROOT, DRIVER, HW_ABSENT, PKG_MISSING, NO_EXECUTOR }

sealed interface FixAction {
    data class InstallTermuxPackage(val pkg: String) : FixAction
    data object RequestRoot : FixAction
    data object AttachHardware : FixAction
    data object OpenDriverDiag : FixAction
    data object OpenDiagnostics : FixAction
}

sealed interface ToolExecution {
    data class Success(val stdout: String, val stderr: String, val exitCode: Int) : ToolExecution
    data class Failure(val reason: PreFlightFailure, val fixAction: FixAction) : ToolExecution
}

data class Tool(
    val id: String,
    val termuxPackages: List<String> = emptyList(),
    val requiresRoot: Boolean = false,
    val requiresHardware: String? = null,
    val driverSo: String? = null,
)

class ToolBridge(
    private val probes: Probes,
    private val executor: Executor? = null,
) {

    /** Environment probes — injected so the logic is JVM-testable. */
    interface Probes {
        fun rootAvailable(): Boolean
        fun loadDriver(soName: String): Boolean
        fun hardwarePresent(hardwareId: String): Boolean
        fun missingPackages(packages: List<String>): List<String>
    }

    /** Bound execution backend (Termux session / local runner). */
    interface Executor {
        fun run(tool: Tool, args: List<String>): ToolExecution
    }

    private val verified = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** Pre-flight check before any module uses a tool; drives status lights. */
    fun preFlight(tool: Tool): ToolExecution {
        if (tool.requiresRoot && !probes.rootAvailable()) {
            return ToolExecution.Failure(PreFlightFailure.NO_ROOT, FixAction.RequestRoot)
        }
        tool.driverSo?.let { so ->
            if (!probes.loadDriver(so)) {
                return ToolExecution.Failure(PreFlightFailure.DRIVER, FixAction.OpenDriverDiag)
            }
        }
        tool.requiresHardware?.let { hw ->
            if (!probes.hardwarePresent(hw)) {
                return ToolExecution.Failure(PreFlightFailure.HW_ABSENT, FixAction.AttachHardware)
            }
        }
        val missing = probes.missingPackages(tool.termuxPackages)
        if (missing.isNotEmpty()) {
            return ToolExecution.Failure(
                PreFlightFailure.PKG_MISSING,
                FixAction.InstallTermuxPackage(missing.first()),
            )
        }
        verified[tool.id] = true
        return ToolExecution.Success("", "", 0)
    }

    /** Pre-flight then execute via the bound executor (or fail honestly). */
    fun exec(tool: Tool, args: List<String> = emptyList()): ToolExecution {
        val gate = preFlight(tool)
        if (gate is ToolExecution.Failure) return gate
        val ex = executor
            ?: return ToolExecution.Failure(
                PreFlightFailure.NO_EXECUTOR,
                FixAction.OpenDiagnostics,
            )
        return ex.run(tool, args)
    }

    fun wasVerified(toolId: String): Boolean = verified[toolId] == true
}
