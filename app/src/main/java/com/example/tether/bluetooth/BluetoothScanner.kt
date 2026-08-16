package com.example.tether.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.example.tether.audio.TonePlayer
import com.example.tether.data.ItemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sin
import kotlin.random.Random

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile

data class ScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isPaired: Boolean = false,
    val isConnected: Boolean = false
)

class BluetoothScanner(
    private val context: Context,
    private val repository: ItemRepository,
    private val tonePlayer: TonePlayer
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var a2dpProfile: BluetoothA2dp? = null
    private var headsetProfile: BluetoothHeadset? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<ScannedDevice>> = _discoveredDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val pairedDevices: StateFlow<List<ScannedDevice>> = _pairedDevices.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val connectedDevices: StateFlow<List<ScannedDevice>> = _connectedDevices.asStateFlow()

    private val _targetRssi = MutableStateFlow(-100)
    val targetRssi: StateFlow<Int> = _targetRssi.asStateFlow()

    private val _targetProximityAngle = MutableStateFlow(0f)
    val targetProximityAngle: StateFlow<Float> = _targetProximityAngle.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var simulationRunnable: Runnable? = null
    private var activeTargetMac: String? = null
    private var simulatedRssiBase = -70

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProfile = proxy as? BluetoothA2dp
            } else if (profile == BluetoothProfile.HEADSET) {
                headsetProfile = proxy as? BluetoothHeadset
            }
            refreshPairedDevices()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) a2dpProfile = null
            if (profile == BluetoothProfile.HEADSET) headsetProfile = null
            refreshPairedDevices()
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { res ->
                val device = res.device
                val rssi = res.rssi
                val name = device.name ?: "Unknown Tracker (${device.address.takeLast(5)})"
                addDiscoveredDevice(name, device.address, rssi, isPaired = false)

                activeTargetMac?.let { mac ->
                    if (device.address.equals(mac, ignoreCase = true)) {
                        onTargetFound(mac, rssi)
                    }
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    private val classicBtReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val rssi: Short = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (-100).toShort())
                    device?.let { dev ->
                        val name = dev.name ?: "Bluetooth Device (${dev.address.takeLast(5)})"
                        addDiscoveredDevice(name, dev.address, rssi.toInt(), isPaired = false)

                        activeTargetMac?.let { mac ->
                            if (dev.address.equals(mac, ignoreCase = true)) {
                                onTargetFound(mac, rssi.toInt())
                            }
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    refreshPairedDevices()
                }
            }
        }
    }

    init {
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            }
            context.registerReceiver(classicBtReceiver, filter)

            bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
            bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        refreshPairedDevices()
    }

    @SuppressLint("MissingPermission")
    fun isAnyDeviceConnected(): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return false
        }
        val items = repository.items.value
        if (items.any { it.isConnected }) {
            return true
        }
        try {
            if (_connectedDevices.value.isNotEmpty()) {
                return true
            }
            val connectedA2dp = bluetoothAdapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
            val connectedHeadset = bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
            if (connectedA2dp || connectedHeadset) {
                return true
            }
            val gattConnected = bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT)
            if (!gattConnected.isNullOrEmpty()) {
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        try {
            val connectedList = mutableListOf<ScannedDevice>()
            val pairedList = mutableListOf<ScannedDevice>()
            val systemConnectedMacs = mutableSetOf<String>()

            // 1. Query A2DP connected devices (Bluetooth audio, car stereo, earbuds)
            try {
                a2dpProfile?.connectedDevices?.forEach { dev ->
                    if (systemConnectedMacs.add(dev.address.uppercase())) {
                        val devName = dev.name ?: "Connected Audio Device (${dev.address.takeLast(5)})"
                        connectedList.add(ScannedDevice(devName, dev.address, rssi = -50, isPaired = true, isConnected = true))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Query HEADSET connected devices (Calls, mono earpiece, headsets)
            try {
                headsetProfile?.connectedDevices?.forEach { dev ->
                    if (systemConnectedMacs.add(dev.address.uppercase())) {
                        val devName = dev.name ?: "Connected Headset (${dev.address.takeLast(5)})"
                        connectedList.add(ScannedDevice(devName, dev.address, rssi = -50, isPaired = true, isConnected = true))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. Query system GATT connected devices (BLE wearables, smartwatches, trackers)
            try {
                bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT)?.forEach { dev ->
                    if (systemConnectedMacs.add(dev.address.uppercase())) {
                        val devName = dev.name ?: "Connected BLE Device (${dev.address.takeLast(5)})"
                        connectedList.add(ScannedDevice(devName, dev.address, rssi = -52, isPaired = true, isConnected = true))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 4. Synchronize connection status to repository items
            repository.items.value.forEach { item ->
                val isSysConnected = systemConnectedMacs.contains(item.macAddress.uppercase())
                if (isSysConnected != item.isConnected) {
                    repository.updateRssiAndConnection(item.macAddress, if (isSysConnected) -50 else -100, isConnected = isSysConnected)
                }
                if (isSysConnected && systemConnectedMacs.add(item.macAddress.uppercase())) {
                    connectedList.add(ScannedDevice(item.name, item.macAddress, rssi = item.rssi, isPaired = true, isConnected = true))
                }
            }

            // 5. Query bonded/paired devices
            bluetoothAdapter?.bondedDevices?.forEach { dev ->
                val macUpper = dev.address.uppercase()
                val devName = dev.name ?: "Paired Device (${dev.address.takeLast(5)})"
                if (!systemConnectedMacs.contains(macUpper)) {
                    pairedList.add(ScannedDevice(devName, dev.address, rssi = -65, isPaired = true, isConnected = false))
                }
            }

            _connectedDevices.value = connectedList
            _pairedDevices.value = pairedList
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning(targetMac: String? = null) {
        activeTargetMac = targetMac
        _isScanning.value = true
        _discoveredDevices.value = emptyList()
        refreshPairedDevices()

        try {
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                val scanner = bluetoothAdapter.bluetoothLeScanner
                if (scanner != null) {
                    val settings = ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()
                    scanner.startScan(null, settings, bleScanCallback)
                }

                if (!bluetoothAdapter.isDiscovering) {
                    bluetoothAdapter.startDiscovery()
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        startSimulationFeed(targetMac)
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        _isScanning.value = false
        stopSimulationFeed()

        try {
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                bluetoothAdapter.bluetoothLeScanner?.stopScan(bleScanCallback)
                if (bluetoothAdapter.isDiscovering) {
                    bluetoothAdapter.cancelDiscovery()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addDiscoveredDevice(name: String, address: String, rssi: Int, isPaired: Boolean) {
        val current = _discoveredDevices.value.toMutableList()
        val index = current.indexOfFirst { it.address.equals(address, ignoreCase = true) }
        if (index >= 0) {
            current[index] = current[index].copy(rssi = rssi)
        } else {
            current.add(ScannedDevice(name, address, rssi, isPaired = isPaired))
        }
        _discoveredDevices.value = current
    }

    private fun onTargetFound(mac: String, rssi: Int) {
        _targetRssi.value = rssi
        repository.updateRssiAndConnection(mac, rssi, isConnected = true)
    }

    private fun startSimulationFeed(targetMac: String?) {
        stopSimulationFeed()
        var stepCount = 0

        simulationRunnable = object : Runnable {
            override fun run() {
                if (!_isScanning.value) return
                stepCount++

                val noise = Random.nextInt(-4, 5)
                val cyclicDist = (sin(stepCount * 0.2) * 15).toInt()
                val currentRssi = (simulatedRssiBase + cyclicDist + noise).coerceIn(-95, -45)

                _targetRssi.value = currentRssi
                _targetProximityAngle.value = (stepCount * 12f) % 360f

                targetMac?.let { mac ->
                    repository.updateRssiAndConnection(mac, currentRssi, isConnected = true)
                }

                handler.postDelayed(this, 600)
            }
        }
        handler.post(simulationRunnable!!)
    }

    private fun stopSimulationFeed() {
        simulationRunnable?.let { handler.removeCallbacks(it) }
        simulationRunnable = null
    }

    fun cleanup() {
        stopScanning()
        try {
            context.unregisterReceiver(classicBtReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (a2dpProfile != null && bluetoothAdapter != null) {
                bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, a2dpProfile)
            }
            if (headsetProfile != null && bluetoothAdapter != null) {
                bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, headsetProfile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
