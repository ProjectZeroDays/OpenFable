package com.projectzerodays.quantumcli.education

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tooltips + walkthroughs store.
 *
 * Boot: bundled, channel-signed assets load first (phase 3 of the boot
 * spine). A signed hot-fix channel — if configured in settings — is checked
 * at boot and nightly by the [com.projectzerodays.quantumcli.ops.MaintenanceScheduler].
 *
 * Hot-fix doctrine (fail closed): the channel manifest is verified against
 * the pinned ECDSA key before anything is applied; each file is then
 * SHA-256-checked against the manifest. Packs are staged, swapped
 * atomically, and the previous pack is retained for 7 days as rollback.
 */
object ContentRegistry {

    data class State(
        val tooltips: Int = 0,
        val walkthroughs: Int = 0,
        val hotFixVersion: Int = 0,
        val lastResult: String = "loaded",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val tooltips = LinkedHashMap<String, ContentTip>()
    private val walks = LinkedHashMap<String, Walkthrough>()
    private lateinit var filesRoot: File
    private var channelUrl: String = ""

    fun init(ctx: Context, hotFixChannel: String = "") {
        filesRoot = File(ctx.filesDir, "content_hotfix")
        channelUrl = hotFixChannel
        runCatching {
            ContentSigning.init(
                ctx.assets.open("content/content_pub.b64")
                    .bufferedReader().readText()
            )
        }.onFailure { Log.w("ContentRegistry", "content signing key missing — hot-fix stays disabled", it) }
        loadFromAssets(ctx)
        applyHotFixIfPresent()
        publish("loaded")
    }

    fun get(id: String): ContentTip? = tooltips[id]
    fun walkthrough(route: String): Walkthrough? = walks[route]

    /** Hot-fix channel is opt-in; unset means the check is a documented no-op. */
    fun channelConfigured(): Boolean = channelUrl.isNotBlank()

    private fun loadFromAssets(ctx: Context) {
        for (name in ctx.assets.list("content/tooltips").orEmpty()) {
            runCatching {
                val json = ctx.assets.open("content/tooltips/$name").bufferedReader().readText()
                parseTooltipFile(json)
            }.onFailure { Log.w("ContentRegistry", "skipping asset tooltips/$name", it) }
        }
        for (name in ctx.assets.list("content/walkthroughs").orEmpty()) {
            runCatching {
                val json = ctx.assets.open("content/walkthroughs/$name").bufferedReader().readText()
                parseWalkthroughFile(json)
            }.onFailure { Log.w("ContentRegistry", "skipping asset walkthroughs/$name", it) }
        }
    }

    /** Hot-fix packs overlay the bundled content: same files, later version. */
    private fun applyHotFixIfPresent() {
        val current = hotFixDir("current")
        if (!current.isDirectory) return
        for (f in current.listFiles().orEmpty()) {
            runCatching {
                when {
                    f.name == "manifest.json" -> Unit
                    f.name.startsWith("tooltips_") -> parseTooltipFile(f.readText())
                    f.name.startsWith("walkthroughs_") -> parseWalkthroughFile(f.readText())
                    else -> Unit
                }
            }.onFailure { Log.w("ContentRegistry", "skipping pack file ${f.name}", it) }
        }
    }

    private fun parseTooltipFile(json: String) {
        val o = JSONObject(json)
        // Legacy array format: {"tooltips": [{"id","title","body","anchor?"}]}.
        val arr = o.optJSONArray("tooltips")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                tooltips[t.getString("id")] = ContentTip(
                    id = t.getString("id"),
                    title = t.getString("title"),
                    body = t.getString("body"),
                    anchor = t.optString("anchor").takeIf { it.isNotEmpty() },
                )
            }
            return
        }
        // Content-pipeline map format: {"tooltips": {"id": {"title","howTo",...}}}.
        // howTo carries the guidance; troubleshooting rows are folded in.
        val map = o.optJSONObject("tooltips") ?: return
        for (key in map.keys()) {
            val t = map.getJSONObject(key)
            val howTo = t.optString("howTo")
            val fixes = buildString {
                val tr = t.optJSONArray("troubleshooting")
                if (tr != null) {
                    for (i in 0 until tr.length()) {
                        val s = tr.getJSONObject(i)
                        append("\n- ")
                        append(s.optString("symptom"))
                        append(" / fix: ")
                        append(s.optString("fix"))
                    }
                }
            }
            tooltips[key] = ContentTip(
                id = key,
                title = t.optString("title", key),
                body = (howTo + fixes).trim(),
            )
        }
    }

    private fun parseWalkthroughFile(json: String) {
        val o = JSONObject(json)
        // Legacy array format: {"walkthroughs": [{"route","steps":[WalkStep]}]}.
        val arr = o.optJSONArray("walkthroughs")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val w = arr.getJSONObject(i)
                val route = w.getString("route")
                walks[route] = Walkthrough.fromJson(route, w)
            }
            return
        }
        // Content-pipeline single-tour format: {"route","steps":[{"targetTooltipId",
        // "title","description","gesture","advanceOn"}]} — mapped onto WalkStep.
        val steps = o.optJSONArray("steps") ?: return
        val route = o.optString("route", "unknown")
        val mapped = org.json.JSONArray()
        for (i in 0 until steps.length()) {
            val s = steps.getJSONObject(i)
            mapped.put(
                org.json.JSONObject()
                    .put("id", s.optString("targetTooltipId"))
                    .put("title", s.optString("title"))
                    .put("body", s.optString("description"))
                    .put("advanceOn", s.optString("advanceOn")),
            )
        }
        walks[route] = Walkthrough.fromJson(route, org.json.JSONObject().put("steps", mapped))
    }

    /**
     * Check the hot-fix channel. Returns a human-readable outcome; on any
     * verification failure the staged pack is wiped and nothing is applied.
     */
    suspend fun checkHotFix(): String = withContext(Dispatchers.IO) {
        if (channelUrl.isBlank() || !ContentSigning.isReady()) return@withContext "hot-fix channel disabled"
        try {
            val manifestBytes = fetchBytes("$channelUrl/manifest.json")
            val manifest = JSONObject(String(manifestBytes))
            // Signature lives in a sidecar so the manifest never has to sign
            // itself: sig covers the exact manifest.json bytes, fail closed.
            val signature = String(fetchBytes("$channelUrl/manifest.sig")).trim()
            if (signature.isBlank() || !ContentSigning.verify(manifestBytes, signature)) {
                wipeStaged()
                return@withContext "hot-fix rejected: bad signature"
            }
            val version = manifest.optInt("version", 0)
            if (version <= _state.value.hotFixVersion) return@withContext "hot-fix up to date (v$version)"

            val files = manifest.getJSONObject("files")
            val staged = hotFixDir("staged").apply { mkdirs() }
            val keys = files.keys()
            for (path in keys.asSequence()) {
                val expected = files.getString(path)
                val bytes = fetchBytes("$channelUrl/$path")
                val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                if (!actual.equals(expected, ignoreCase = true)) {
                    wipeStaged()
                    return@withContext "hot-fix rejected: $path hash mismatch"
                }
                File(staged, path.substringAfterLast('/')).writeBytes(bytes)
            }
            // Atomic swap: current -> previous-<ts> (rollback), staged -> current.
            val cur = hotFixDir("current")
            if (cur.isDirectory) {
                val previous = hotFixDir("previous-" +
                    SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date()))
                cur.renameTo(previous)
            }
            if (!staged.renameTo(hotFixDir("current"))) {
                return@withContext "hot-fix failed: swap error"
            }
            prunePreviousPacks(days = 7)
            applyHotFixIfPresent()
            _state.value = _state.value.copy(hotFixVersion = version)
            publish("hot-fix v$version applied")
        } catch (e: Exception) {
            wipeStaged()
            publish("hot-fix check failed: ${e.message.orEmpty().take(120)}")
        }
    }

    private fun publish(result: String): String {
        _state.value = _state.value.copy(
            tooltips = tooltips.size,
            walkthroughs = walks.size,
            lastResult = result,
        )
        return result
    }

    private fun hotFixDir(name: String) = File(filesRoot, name)

    private fun wipeStaged() {
        File(filesRoot, "staged").deleteRecursively()
    }

    private fun prunePreviousPacks(days: Int) {
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        filesRoot.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("previous-") }
            .forEach { if (it.lastModified() < cutoff) it.deleteRecursively() }
    }

    private fun fetchBytes(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}
