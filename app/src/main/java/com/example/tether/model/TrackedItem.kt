package com.example.tether.model

enum class TrackerMode {
    IDLE,
    FIND_ME,
    DONT_FORGET_ME
}

enum class ItemIcon {
    KEYS,
    WALLET,
    BACKPACK,
    TOOLBOX,
    CAR_KEYS,
    HEADPHONES,
    GENERIC
}

data class TrackedItem(
    val id: String,
    val name: String,
    val macAddress: String,
    val icon: ItemIcon = ItemIcon.KEYS,
    val mode: TrackerMode = TrackerMode.IDLE,
    val isConnected: Boolean = false,
    val rssi: Int = -100, // dBm
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val isBeeping: Boolean = false
) {
    val proximityLabel: String
        get() = when {
            rssi > -60 -> "Immediate (< 2m)"
            rssi > -75 -> "Near (2 - 5m)"
            rssi > -90 -> "Far (5 - 15m)"
            else -> "Signal Weak / Out of Range"
        }

    val proximityRatio: Float
        get() = ((rssi + 100).coerceIn(0, 60)) / 60f
}
