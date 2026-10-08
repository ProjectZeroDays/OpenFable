package com.projectzerodays.quantumcli.education

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hands-on walkthrough engine.
 *
 * Steps advance on REAL user actions, not taps on "next": any call site
 * fires `WalkthroughEngine.triggered("<semantic event>")` when something
 * genuinely happens (server started, first scan run, grid locked…), and a
 * step whose `advanceOn` matches that event completes. Skip is always
 * available and permanently dismisses the route until a reset.
 */
object WalkthroughEngine {

    data class StepRef(val route: String, val index: Int)

    private val _active = MutableStateFlow<StepRef?>(null)
    val active: StateFlow<StepRef?> = _active.asStateFlow()

    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("walkthroughs", Context.MODE_PRIVATE)
    }

    fun enabled(): Boolean = ::prefs.isInitialized && prefs.getBoolean("walkthroughs_enabled", true)

    fun setEnabled(value: Boolean) {
        if (::prefs.isInitialized) prefs.edit().putBoolean("walkthroughs_enabled", value).apply()
    }

    private fun isDone(route: String): Boolean =
        ::prefs.isInitialized && prefs.getBoolean("done_$route", false)

    private fun markDone(route: String) {
        if (::prefs.isInitialized) prefs.edit().putBoolean("done_$route", true).apply()
    }

    /** Start automatically on first visit of a route, if enabled. */
    fun maybeStart(route: String) {
        if (!::prefs.isInitialized || isDone(route) || !enabled()) return
        _active.value = StepRef(route, 0)
    }

    fun startManually(route: String) {
        if (ContentRegistry.walkthrough(route) != null) {
            _active.value = StepRef(route, 0)
        }
    }

    /**
     * Semantic event bus: fire from wherever the real action happened.
     * Completes the active step iff its `advanceOn` matches.
     */
    fun triggered(key: String) {
        val ref = _active.value ?: return
        val walk = ContentRegistry.walkthrough(ref.route) ?: return
        val step = walk.steps.getOrNull(ref.index) ?: return
        if (step.advanceOn == key) {
            if (ref.index + 1 >= walk.steps.size) {
                markDone(ref.route)
                _active.value = null
            } else {
                _active.value = ref.copy(index = ref.index + 1)
            }
        }
    }

    /** Current step the overlay should render (null when inactive/finished). */
    fun currentStep(): WalkStep? {
        val ref = _active.value ?: return null
        return ContentRegistry.walkthrough(ref.route)?.steps?.getOrNull(ref.index)
    }

    fun next() {
        val ref = _active.value ?: return
        val walk = ContentRegistry.walkthrough(ref.route) ?: return
        if (ref.index + 1 >= walk.steps.size) {
            markDone(ref.route)
            _active.value = null
        } else {
            _active.value = ref.copy(index = ref.index + 1)
        }
    }

    fun skip() {
        _active.value?.let { markDone(it.route) }
        _active.value = null
    }

    /** Reset one route so its walkthrough can run again (used by Settings). */
    fun resetRoute(route: String) {
        if (::prefs.isInitialized) prefs.edit().putBoolean("done_$route", false).apply()
    }
}
