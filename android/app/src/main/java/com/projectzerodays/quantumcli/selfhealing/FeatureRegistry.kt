package com.projectzerodays.quantumcli.selfhealing

import android.content.Context
import android.content.SharedPreferences
import com.projectzerodays.quantumcli.education.ContentSigning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Feature health mesh.
 *
 * Every registered module reports crashes/heals; a time-decayed score drives
 * the widget verdict (GREEN > 80, AMBER > 50 with a heal hint, RED below).
 * Channel kill switches are fail-closed on signature but user-sovereign in
 * effect: a remote kill is applied AND surfaced to the operator, who can
 * always override it locally — it is never silent and never irreversible.
 */
object FeatureRegistry {

    class ModuleHealth(
        val moduleId: String,
        val widgetIds: List<String>,
    ) {
        @Volatile var enabled: Boolean = true          // user policy
        @Volatile var remoteKilled: Boolean = false    // channel kill switch (overridable)
        @Volatile var dimReason: String? = null
        @Volatile var crashes: Int = 0
        @Volatile var heals: Int = 0
        @Volatile var lastCrashAtMs: Long = 0L
    }

    private val modules = ConcurrentHashMap<String, ModuleHealth>()
    private lateinit var prefs: SharedPreferences

    private val _killSwitchNotices = MutableStateFlow<List<String>>(emptyList())
    val killSwitchNotices: StateFlow<List<String>> = _killSwitchNotices.asStateFlow()

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("feature_registry", Context.MODE_PRIVATE)
    }

    /** Register a module with the widget ids it powers; persists user policy. */
    fun register(moduleId: String, widgetIds: List<String>): ModuleHealth {
        val h = ModuleHealth(moduleId, widgetIds)
        if (::prefs.isInitialized) {
            h.enabled = prefs.getBoolean("enabled_$moduleId", true)
        }
        modules[moduleId] = h
        return h
    }

    fun healthOf(moduleId: String): WidgetStatus {
        val f = modules[moduleId] ?: return WidgetStatus.GREY("unregistered module $moduleId")
        if (f.remoteKilled) return WidgetStatus.GREY("remote kill switch — override available in Diagnostics")
        if (!f.enabled) return WidgetStatus.GREY("off in settings")
        if (f.dimReason != null) return WidgetStatus.GREY(f.dimReason ?: "")
        val s = score(f)
        return when {
            s > 80 -> WidgetStatus.GREEN
            s > 50 -> WidgetStatus.AMBER(
                if (f.crashes > 0) "crashed ${f.crashes}× — heal hook will retry at next boot" else "degraded — check logs"
            )
            else -> WidgetStatus.RED(if (f.crashes > 0) "unstable (${f.crashes} crashes)" else "unhealthy")
        }
    }

    /** Exponential time-decayed crash score: 5-minute half-life per crash. */
    fun score(f: ModuleHealth): Int {
        val minutesSince = if (f.lastCrashAtMs == 0L) Double.MAX_VALUE
        else (System.currentTimeMillis() - f.lastCrashAtMs) / 60000.0
        val decay = 0.5.pow(minutesSince / 5.0)
        val penalty = min(60.0, f.crashes * 20.0 * decay)
        val bonus = min(10, f.heals * 2)
        return max(0, min(100, (100.0 - penalty + bonus).roundToInt()))
    }

    fun onWidgetCrash(moduleId: String) {
        val f = modules[moduleId] ?: return
        f.crashes += 1
        f.lastCrashAtMs = System.currentTimeMillis()
    }

    fun onHealSuccess(moduleId: String) {
        modules[moduleId]?.let { it.heals += 1; it.crashes = max(0, it.crashes - 1) }
    }

    /** Dim a module's UI with the reason surfaced (Sprint-2 dim doctrine). */
    fun dim(moduleId: String, reason: String) {
        modules[moduleId]?.dimReason = reason.takeIf { it.isNotBlank() }
    }

    fun setEnabled(moduleId: String, enabled: Boolean) {
        val f = modules[moduleId] ?: return
        f.enabled = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean("enabled_$moduleId", enabled).apply()
    }

    /** Operator override of a remote kill — always allowed, always logged. */
    fun overrideRemoteKill(moduleId: String, allow: Boolean) {
        val f = modules[moduleId] ?: return
        f.remoteKilled = false
        audit("kill-switch override by operator: $moduleId (allow=$allow)")
    }

    /**
     * Apply a channel kill-switch pack. Signature is mandatory (fail closed);
     * application is loud: the operator sees every killed module and how to
     * override. Pack shape: {"modules":["m1","m2"],"note":"reason"}.
     */
    fun receiveKillSwitch(packJson: String, signatureB64: String): Boolean {
        if (!ContentSigning.verify(packJson.toByteArray(), signatureB64)) {
            audit("kill-switch pack rejected: bad signature")
            return false
        }
        val pack = runCatching { JSONObject(packJson) }.getOrElse {
            audit("kill-switch pack rejected: unparseable")
            return false
        }
        val killed = mutableListOf<String>()
        val arr = pack.optJSONArray("modules") ?: return false.also { audit("kill-switch pack has no modules") }
        for (i in 0 until arr.length()) {
            val id = arr.optString(i)
            if (id.isEmpty()) continue
            val f = modules[id] ?: continue
            f.remoteKilled = true
            killed += id
        }
        val note = pack.optString("note").takeIf { it.isNotEmpty() } ?: "no reason given"
        if (killed.isNotEmpty()) {
            _killSwitchNotices.value = killed.map { "$it disabled by channel kill switch ($note) — override available in Diagnostics" }
            audit("kill-switch applied: ${killed.joinToString()}")
        }
        return true
    }

    fun registered(): List<ModuleHealth> = modules.values.sortedBy { it.moduleId }

    private fun audit(msg: String) {
        runCatching {
            com.projectzerodays.quantumcli.c2.C2State.audit("Health", msg)
        }
    }
}
