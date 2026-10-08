package com.projectzerodays.quantumcli.ops

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.ParcelUuid
import com.projectzerodays.quantumcli.c2.C2State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BLE recon — real Android BluetoothLeScanner sweep: device inventory with
 * OUI vendor, service-UUID decode, and tracker detection (advertising
 * identity cluster seen across MULTIPLE different MACs = rotating-address
 * tracker heuristic, honestly labeled as heuristic).
 */
object BleRecon {

    data class Device(
        val mac: String,
        val name: String?,
        val rssi: Int,
        val vendor: String,
        val services: List<String>,
        val lastSeen: Long,
    )

    data class TrackerAlert(val reason: String, val macs: List<String>, val detail: String)

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _devices = MutableStateFlow<Map<String, Device>>(emptyMap())
    val devices: StateFlow<Map<String, Device>> = _devices.asStateFlow()

    private val _alerts = MutableStateFlow<List<TrackerAlert>>(emptyList())
    val alerts: StateFlow<List<TrackerAlert>> = _alerts.asStateFlow()

    // name/identity cluster -> MACs it appeared on (tracker evidence)
    private val identityMacs = HashMap<String, MutableSet<String>>()
    private var callback: ScanCallback? = null

    fun adapter(ctx: Context): BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun ready(ctx: Context): Boolean = adapter(ctx)?.let { it.isEnabled && it.bluetoothLeScanner != null } == true

    @SuppressLint("MissingPermission")
    fun start(ctx: Context) {
        val scanner = adapter(ctx)?.bluetoothLeScanner ?: return
        if (_scanning.value) return
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val mac = result.device.address
                val name = result.device.name ?: result.scanRecord?.deviceName
                val svcs = result.scanRecord?.serviceUuids?.map { u: ParcelUuid -> u.uuid.toString().take(8) } ?: emptyList()
                val dev = Device(
                    mac = mac,
                    name = name,
                    rssi = result.rssi,
                    vendor = Oui.lookupLocal(mac) ?: if (Oui.isLocallyAdministered(mac)) "randomized" else "unknown",
                    services = svcs,
                    lastSeen = System.currentTimeMillis(),
                )
                _devices.value = _devices.value + (mac to dev)
                // tracker evidence: a stable advertised NAME (or service set) hopping MACs
                val identity = name?.takeIf { it.isNotBlank() && it.length >= 4 }
                if (identity != null) {
                    val set = identityMacs.getOrPut(identity.lowercase()) { mutableSetOf() }
                    if (!set.contains(mac)) {
                        set.add(mac)
                        if (set.size >= 3) {
                            val alert = TrackerAlert(
                                "ROTATING IDENTITY",
                                set.toList(),
                                "'$identity' advertised by ${set.size}+ different MACs — tracker fingerprint (heuristic)",
                            )
                            _alerts.value = (_alerts.value.filter { it.reason != alert.reason || it.detail != alert.detail } + alert).takeLast(50)
                            C2State.audit("BLE", "tracker heuristic: '$identity' seen on ${set.size} MACs")
                        }
                    }
                }
            }
        }
        callback = cb
        scanner.startScan(cb)
        _scanning.value = true
        C2State.audit("BLE", "scan started")
    }

    @SuppressLint("MissingPermission")
    fun stop(ctx: Context) {
        val c = callback ?: return
        runCatching { adapter(ctx)?.bluetoothLeScanner?.stopScan(c) }
        callback = null
        _scanning.value = false
        C2State.audit("BLE", "scan stopped — ${_devices.value.size} device(s), ${_alerts.value.size} alert(s)")
    }

    fun clear() {
        _devices.value = emptyMap()
        _alerts.value = emptyList()
        identityMacs.clear()
    }
}
