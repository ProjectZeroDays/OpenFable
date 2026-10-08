package com.projectzerodays.quantumcli.selfhealing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The eight failure classes the daemon recognizes (per the foundation spec). */
enum class FailureClass {
    WIDGET_CRASH,
    PROVIDER_OUTAGE,
    MODEL_CORRUPTION,
    DB_CORRUPTION,
    TOOLBRIDGE_MISSING,
    DOWNLOAD_STALL,
    NETWORK_FLAKY,
    CRASH_LOOP_BOOT,
}

data class HealResult(
    val succeeded: Boolean,
    val detail: String,
    val changedState: String? = null,
)

data class HealEvent(
    val failureClass: FailureClass,
    val result: HealResult,
    val ts: Long,
)

/**
 * One healer per failure class. [settingsKey] toggles auto-heal for this
 * healer (default on when unset by the settings snapshot).
 */
interface Healer {
    val failureClass: FailureClass
    val settingsKey: String
    suspend fun detect(): Boolean
    suspend fun heal(): HealResult
}

/**
 * Failure-class health map for the daemon's own dim/restore loop.
 * (Distinct from the app-wide selfhealing.FeatureRegistry health mesh,
 * which tracks registered modules with scores and kill-switch policy —
 * the daemon reports failure-class outcomes here.)
 */
object HealerHealth {

    enum class Health { OK, DIM }

    data class FeatureState(val id: String, val health: Health, val reason: String?)

    private val state = LinkedHashMap<String, FeatureState>()

    private val _killSwitchNotices = MutableStateFlow<List<String>>(emptyList())

    /** Channel kill-switch notices (loud banner feed for dashboards). */
    val killSwitchNotices: StateFlow<List<String>> = _killSwitchNotices.asStateFlow()

    @Synchronized
    fun register(id: String) {
        state.putIfAbsent(id, FeatureState(id, Health.OK, null))
    }

    @Synchronized
    fun dim(id: String, reason: String?) {
        state[id] = FeatureState(id, Health.DIM, reason)
    }

    @Synchronized
    fun restore(id: String) {
        state[id] = FeatureState(id, Health.OK, null)
    }

    @Synchronized
    fun postKillNotice(notice: String) {
        if (notice.isNotBlank()) {
            _killSwitchNotices.value = _killSwitchNotices.value + notice
        }
    }

    @Synchronized
    fun clearKillNotices() {
        _killSwitchNotices.value = emptyList()
    }

    @Synchronized
    fun stateOf(id: String): FeatureState? = state[id]

    @Synchronized
    fun health(): Map<String, FeatureState> = state.toMap()

    @Synchronized
    fun clear() {
        state.clear()
    }
}

/**
 * Skeleton healing daemon: one detect→heal pass per registered [Healer],
 * events surfaced on [events] and returned by [healOnce] (the deterministic,
 * unit-tested path). [start] runs the poll loop; per the ship order the loop
 * is opt-in — nothing polls until a caller starts it, and concrete healers
 * (provider failover, model re-download, DoH fallback…) attach per phase.
 */
class HealingDaemon(
    healers: List<Healer>,
    private val isAutoHealEnabled: (Healer) -> Boolean = { true },
) {
    private val registered: List<Healer> = healers.toList()

    private val _events = MutableSharedFlow<HealEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<HealEvent> = _events

    var loopJob: Job? = null
        private set

    /** Run one pass over every healer; returns the events it produced. */
    suspend fun healOnce(): List<HealEvent> {
        val produced = ArrayList<HealEvent>(registered.size)
        for (healer in registered) {
            val event: HealEvent? = try {
                if (!healer.detect()) {
                    null // healthy — no event
                } else if (!isAutoHealEnabled(healer)) {
                    HealEvent(
                        healer.failureClass,
                        HealResult(false, "auto-heal disabled by ${healer.settingsKey}"),
                        System.currentTimeMillis(),
                    )
                } else {
                    val result = healer.heal()
                    if (result.changedState != null && !result.succeeded) {
                        HealerHealth.dim(healer.failureClass.name, result.changedState)
                    } else if (result.succeeded) {
                        HealerHealth.restore(healer.failureClass.name)
                    }
                    HealEvent(healer.failureClass, result, System.currentTimeMillis())
                }
            } catch (e: Exception) {
                HealEvent(
                    healer.failureClass,
                    HealResult(false, "detector error: ${e.message ?: e.javaClass.simpleName}"),
                    System.currentTimeMillis(),
                )
            }
            if (event != null) {
                produced.add(event)
                _events.emit(event)
            }
        }
        return produced
    }

    /** Poll loop; stops when the scope is cancelled or [stop] is called. */
    fun start(scope: CoroutineScope, pollMs: Long = 30_000L): Job {
        stop()
        loopJob = scope.launch {
            while (isActive) {
                healOnce()
                delay(pollMs)
            }
        }
        return loopJob!!
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }
}
