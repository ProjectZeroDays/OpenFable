package com.projectzerodays.quantumcli.ai

import com.projectzerodays.quantumcli.QuantumApp
import com.projectzerodays.quantumcli.c2.C2State
import com.projectzerodays.quantumcli.data.QuantSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Local AI fallback downloader — pulls a MediaPipe LLM Inference-compatible
 * model (Gemma-2B int4, the model documented for the MediaPipe LLM API) into
 * the app files dir so on-device inference works without an API key.
 *
 * Auto-download runs once per launch when `autoDownload` is enabled, the
 * local mode is selected (or no model exists yet) and nothing is downloading
 * already. Progress is surfaced to the UI as 0f..1f and mirrored to the audit
 * log, so the Dashboard console shows it live.
 */
object AiModelDownloader {

    /**
     * Known-good mirrors for the Gemma-2B int4 tflite weights — tried in
     * order. Google retired the old mediapipe-assets bucket path and every
     * official litert-community HuggingFace repo is now auto-gated (anonymous
     * downloads get HTTP 401), so all mirrors are verified ungated
     * HuggingFace re-uploads of the same artifact (TFLite flatbuffer,
     * ~1.3 GB): mirror 1 is the GPU int4 build, mirrors 2-3 the CPU int4
     * builds (gemma 2b / gemma 1.1 2b) that run on any device.
     */
    val MODEL_URLS = listOf(
        "https://huggingface.co/autoocrat0413/gemma-2b-it-gpu-int4-mediapipe/resolve/main/gemma-2b-it-gpu-int4.bin",
        "https://huggingface.co/autoocrat0413/gemma-2b-it-gpu-int4-mediapipe/resolve/main/gemma-2b-it-cpu-int4.bin",
        "https://huggingface.co/innermost47/gemma-2b-it-int4-mediapipe/resolve/main/gemma-1.1-2b-it-cpu-int4.bin",
    )

    /** Kept for compatibility — the primary mirror (index 0 of MODEL_URLS). */
    const val MODEL_URL =
        "https://huggingface.co/autoocrat0413/gemma-2b-it-gpu-int4-mediapipe/resolve/main/gemma-2b-it-gpu-int4.bin"

    const val MODEL_NAME = "gemma-2b-it-gpu-int4.bin"

    /** A model smaller than this is a corrupt/truncated mirror, not weights. */
    const val MIN_MODEL_BYTES = 10L * 1024 * 1024

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Kick off the fallback-model download when it makes sense. Returns true
     * when a download was actually started.
     */
    fun maybeAutoDownload(scope: CoroutineScope = QuantumApp.appScope): Boolean {
        val s = QuantSettings.state.value
        if (_downloading.value || AiClient.localAvailable()) return false
        if (!s.autoDownload) return false
        if (s.wizardDone && s.aiMode != AiClient.MODE_LOCAL) return false
        return start(scope)
    }

    /** Explicit download (setup wizard). Returns false when already running. */
    fun start(scope: CoroutineScope = QuantumApp.appScope): Boolean {
        if (_downloading.value) return false
        _downloading.value = true
        _progress.value = 0f
        _error.value = null
        C2State.audit("AI", "local fallback model download started ($MODEL_NAME)")
        scope.launch(Dispatchers.IO) {
            try {
                download()
                C2State.audit("AI", "local fallback model ready — on-device AI enabled")
            } catch (e: Exception) {
                _error.value = e.message ?: e.javaClass.simpleName
                C2State.audit("AI", "fallback model download failed: ${e.message}")
                File(C2State.filesRoot, "$MODEL_NAME.tmp").delete()
            } finally {
                _downloading.value = false
            }
        }
        return true
    }

    /**
     * Try every mirror in order; a failed mirror falls back to the next.
     * Partial files resume via the Range header and the final size must pass
     * the sanity check before the download is accepted.
     */
    private fun download() {
        val tmp = File(C2State.filesRoot, "$MODEL_NAME.tmp")
        val final = File(C2State.filesRoot, MODEL_NAME)
        tmp.parentFile?.mkdirs()
        var lastError: Exception? = null
        var ok = false
        for (url in MODEL_URLS) {
            try {
                downloadFrom(url, tmp)
                if (tmp.length() <= MIN_MODEL_BYTES) {
                    throw java.io.IOException(
                        "model too small (${tmp.length()} B) — corrupt mirror?"
                    )
                }
                ok = true
                break
            } catch (e: Exception) {
                lastError = e
                tmp.delete()
                C2State.audit(
                    "AI",
                    "mirror failed (${e.message}) — ${MODEL_URLS.indexOf(url) + 1}/" +
                        "${MODEL_URLS.size} exhausted, trying next"
                )
            }
        }
        if (!ok) {
            throw lastError ?: java.io.IOException("all model mirrors failed")
        }
        if (final.isFile) final.delete()
        if (!tmp.renameTo(final)) {
            tmp.copyTo(final, overwrite = true)
            tmp.delete()
        }
    }

    /** Stream one mirror into [tmp], resuming a partial file via Range. */
    private fun downloadFrom(url: String, tmp: File) {
        val resumeFrom = if (tmp.isFile) tmp.length() else 0L
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        // HuggingFace mirrors reject or throttle default Java UAs — identify.
        conn.setRequestProperty("User-Agent", "QuantumCLI/${com.projectzerodays.quantumcli.BuildConfig.VERSION_NAME} (Android)")
        if (resumeFrom > 0) {
            conn.setRequestProperty("Range", "bytes=$resumeFrom-")
        }
        var lastReported = 0f
        var lastSizeLog = resumeFrom
        try {
            val status = conn.responseCode
            if (status !in 200..299) throw java.io.IOException("HTTP $status")
            // 206 = server honored the Range resume; 200 = full restart.
            val resumed = status == 206
            if (!resumed && resumeFrom > 0) {
                C2State.audit("AI", "mirror does not support resume — restarting from 0")
            }
            val total = conn.contentLengthLong.takeIf { it > 0 }
                ?.let { if (resumed) it + resumeFrom else it } ?: -1L
            conn.inputStream.use { input ->
                java.io.FileOutputStream(tmp, resumed).use { out ->
                    val buf = ByteArray(256 * 1024)
                    var read = resumeFrom
                    if (resumed) {
                        C2State.audit("AI", "resuming model download at ${read / (1024 * 1024)} MB")
                    }
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val p = (read.toFloat() / total).coerceIn(0f, 1f)
                            _progress.value = p
                            if (p - lastReported >= 0.1f) {
                                lastReported = p
                                C2State.audit(
                                    "AI",
                                    "fallback model download ${(p * 100).toInt()}% " +
                                        "(${read / (1024 * 1024)} MB)"
                                )
                            }
                        } else if (read - lastSizeLog >= 200L * 1024 * 1024) {
                            // No Content-Length: report pace by bytes only.
                            lastSizeLog = read
                            C2State.audit(
                                "AI",
                                "fallback model download ${read / (1024 * 1024)} MB…"
                            )
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
