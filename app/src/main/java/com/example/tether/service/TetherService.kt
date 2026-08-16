package com.example.tether.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.tether.MainActivity
import com.example.tether.audio.TonePlayer

class TetherService : Service() {

    private lateinit var tonePlayer: TonePlayer
    private var isMonitoring = false
    private var targetMacAddress: String? = null
    private var targetItemName: String = "Tether Tracker"

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            when (action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                        if (isMonitoring) {
                            triggerDisconnectAlert("$targetItemName (Bluetooth Turned OFF)")
                        }
                    }
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val devAddress = device?.address
                    if (isMonitoring) {
                        if (targetMacAddress == null || targetMacAddress.equals(devAddress, ignoreCase = true)) {
                            triggerDisconnectAlert(targetItemName)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        tonePlayer = TonePlayer(this)
        createNotificationChannels()

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        try {
            registerReceiver(bluetoothStateReceiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> {
                targetItemName = intent.getStringExtra(EXTRA_ITEM_NAME) ?: "Tether Tracker"
                targetMacAddress = intent.getStringExtra(EXTRA_TARGET_MAC)
                startForeground(NOTIFICATION_ID, buildForegroundNotification(targetItemName))
                isMonitoring = true
            }
            ACTION_TRIGGER_ALERT -> {
                val itemName = intent.getStringExtra(EXTRA_ITEM_NAME) ?: targetItemName
                triggerDisconnectAlert(itemName)
            }
            ACTION_STOP -> {
                isMonitoring = false
                tonePlayer.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun triggerDisconnectAlert(itemName: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alertVibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500, 200, 1000)

        val alertNotification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("⚠️ TETHER ALERT: Item Left Behind!")
            .setContentText("Lost connection to $itemName. You may have left it behind!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(alertVibrationPattern)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(ALERT_NOTIFICATION_ID, alertNotification)
        tonePlayer.playEmergencyAlarm()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val monitoringChannel = NotificationChannel(
                CHANNEL_ID,
                "Tether Active Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors tethered Bluetooth items in Don't Forget Me mode"
            }

            val alertVibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500, 200, 1000)
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Tether Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Immediate alerts and vibrations when a tethered item disconnects"
                enableVibration(true)
                vibrationPattern = alertVibrationPattern
                enableLights(true)
            }

            notificationManager.createNotificationChannel(monitoringChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    private fun buildForegroundNotification(itemName: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔗 Tether Active Protection")
            .setContentText("Monitoring tether connection to $itemName")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        tonePlayer.stop()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "tether_protection_channel"
        const val ALERT_CHANNEL_ID = "tether_alert_channel"
        const val NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 1002

        const val ACTION_START = "com.example.tether.action.START"
        const val ACTION_STOP = "com.example.tether.action.STOP"
        const val ACTION_TRIGGER_ALERT = "com.example.tether.action.TRIGGER_ALERT"
        const val EXTRA_ITEM_NAME = "extra_item_name"
        const val EXTRA_TARGET_MAC = "extra_target_mac"
    }
}
