package com.example.tether

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tether.audio.TonePlayer
import com.example.tether.bluetooth.BluetoothScanner
import com.example.tether.data.ItemRepository
import com.example.tether.model.TrackerMode
import com.example.tether.service.TetherService
import com.example.tether.ui.screens.AddItemDialog
import com.example.tether.ui.screens.DashboardScreen
import com.example.tether.ui.theme.DarkBackground
import com.example.tether.ui.theme.TetherTheme
import android.widget.Toast

import com.example.tether.model.TrackedItem
import com.example.tether.ui.screens.EditItemDialog

class MainActivity : ComponentActivity() {

    private lateinit var repository: ItemRepository
    private lateinit var tonePlayer: TonePlayer
    private lateinit var bluetoothScanner: BluetoothScanner

    private var activeTetherId by mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions result handler
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = ItemRepository(this)
        tonePlayer = TonePlayer(this)
        bluetoothScanner = BluetoothScanner(this, repository, tonePlayer)

        requestRequiredPermissions()

        setContent {
            TetherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    val items by repository.items.collectAsStateWithLifecycle()
                    val scannedDevices by bluetoothScanner.discoveredDevices.collectAsStateWithLifecycle()
                    val pairedDevices by bluetoothScanner.pairedDevices.collectAsStateWithLifecycle()
                    val connectedDevices by bluetoothScanner.connectedDevices.collectAsStateWithLifecycle()
                    val isBeeping by tonePlayer.isPlaying.collectAsStateWithLifecycle()
                    var showAddItemDialog by remember { mutableStateOf(false) }
                    var itemToEdit by remember { mutableStateOf<TrackedItem?>(null) }
                    val existingMacs = remember(items) { items.map { it.macAddress }.toSet() }
                    val hasConnectedDevice = remember(items) { bluetoothScanner.isAnyDeviceConnected() }

                    DashboardScreen(
                        items = items,
                        activeTetherId = activeTetherId,
                        isBeeping = isBeeping,
                        hasConnectedDevice = hasConnectedDevice,
                        onToggleTetherGuard = { item, enable ->
                            if (enable) {
                                activeTetherId = item.id
                                repository.updateItemMode(item.id, TrackerMode.DONT_FORGET_ME)
                                startTetherForegroundService(item.name, item.macAddress)
                            } else {
                                activeTetherId = null
                                repository.updateItemMode(item.id, TrackerMode.IDLE)
                                stopTetherForegroundService()
                            }
                        },
                        onTriggerBeep = {
                            if (!isBeeping) {
                                val hasConnectedDevice = bluetoothScanner.isAnyDeviceConnected()
                                if (!hasConnectedDevice) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Cannot play sound: No Bluetooth tracker connected",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    tonePlayer.playLocatorBeepSequence()
                                }
                            } else {
                                tonePlayer.stop()
                            }
                        },
                        onAddNewItem = {
                            bluetoothScanner.startScanning()
                            showAddItemDialog = true
                        },
                        onEditItem = { item ->
                            itemToEdit = item
                        },
                        onRemoveItem = { id ->
                            if (activeTetherId == id) {
                                activeTetherId = null
                                stopTetherForegroundService()
                            }
                            repository.removeItem(id)
                        }
                    )

                    if (showAddItemDialog) {
                        AddItemDialog(
                            connectedDevices = connectedDevices,
                            pairedDevices = pairedDevices,
                            scannedDevices = scannedDevices,
                            existingMacs = existingMacs,
                            onDismiss = {
                                bluetoothScanner.stopScanning()
                                showAddItemDialog = false
                            },
                            onConfirm = { name, mac, icon ->
                                repository.addItem(name, mac, icon)
                                bluetoothScanner.stopScanning()
                                showAddItemDialog = false
                            }
                        )
                    }

                    itemToEdit?.let { target ->
                        EditItemDialog(
                            item = target,
                            onDismiss = { itemToEdit = null },
                            onConfirm = { newName, newIcon ->
                                repository.updateItemDetails(target.id, newName, newIcon)
                                itemToEdit = null
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startTetherForegroundService(itemName: String, targetMac: String) {
        val intent = Intent(this, TetherService::class.java).apply {
            action = TetherService.ACTION_START
            putExtra(TetherService.EXTRA_ITEM_NAME, itemName)
            putExtra(TetherService.EXTRA_TARGET_MAC, targetMac)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopTetherForegroundService() {
        val intent = Intent(this, TetherService::class.java).apply {
            action = TetherService.ACTION_STOP
        }
        startService(intent)
    }

    override fun onDestroy() {
        bluetoothScanner.cleanup()
        tonePlayer.stop()
        super.onDestroy()
    }
}