package com.example.tether.data

import android.content.Context
import android.content.SharedPreferences
import com.example.tether.model.ItemIcon
import com.example.tether.model.TrackedItem
import com.example.tether.model.TrackerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ItemRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("tether_items_prefs", Context.MODE_PRIVATE)

    private val _items = MutableStateFlow<List<TrackedItem>>(emptyList())
    val items: StateFlow<List<TrackedItem>> = _items.asStateFlow()

    init {
        loadItems()
        // Clean out legacy sample placeholder items if present
        val filtered = _items.value.filterNot { it.id.startsWith("sample_") }
        if (filtered.size != _items.value.size) {
            _items.value = filtered
            saveItems(filtered)
        }
    }

    fun isDuplicateMac(macAddress: String): Boolean {
        val formattedMac = macAddress.uppercase().trim()
        return _items.value.any { it.macAddress.equals(formattedMac, ignoreCase = true) }
    }

    fun addItem(name: String, macAddress: String, icon: ItemIcon): TrackedItem? {
        val formattedMac = macAddress.uppercase().trim()
        if (isDuplicateMac(formattedMac)) {
            return null
        }
        val newItem = TrackedItem(
            id = UUID.randomUUID().toString(),
            name = name,
            macAddress = formattedMac,
            icon = icon,
            mode = TrackerMode.IDLE
        )
        val updated = _items.value + newItem
        _items.value = updated
        saveItems(updated)
        return newItem
    }

    fun removeItem(id: String) {
        val updated = _items.value.filterNot { it.id == id }
        _items.value = updated
        saveItems(updated)
    }

    fun updateItemDetails(id: String, newName: String, newIcon: ItemIcon) {
        val updated = _items.value.map { item ->
            if (item.id == id) {
                item.copy(name = newName, icon = newIcon)
            } else item
        }
        _items.value = updated
        saveItems(updated)
    }

    fun updateItemMode(id: String, mode: TrackerMode) {
        val updated = _items.value.map { item ->
            if (item.id == id) {
                // If setting this item to active mode, set others to idle for clarity or keep independent
                item.copy(mode = mode)
            } else item
        }
        _items.value = updated
        saveItems(updated)
    }

    fun updateRssiAndConnection(macAddress: String, rssi: Int, isConnected: Boolean) {
        val now = System.currentTimeMillis()
        val updated = _items.value.map { item ->
            if (item.macAddress.equals(macAddress, ignoreCase = true)) {
                item.copy(
                    rssi = rssi,
                    isConnected = isConnected,
                    lastSeenTimestamp = now
                )
            } else item
        }
        _items.value = updated
    }

    fun setBeeping(id: String, isBeeping: Boolean) {
        val updated = _items.value.map { item ->
            if (item.id == id) item.copy(isBeeping = isBeeping) else item
        }
        _items.value = updated
    }

    private fun loadItems() {
        val jsonString = prefs.getString("items_json", null) ?: return
        try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<TrackedItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    TrackedItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        macAddress = obj.getString("macAddress"),
                        icon = ItemIcon.valueOf(obj.optString("icon", ItemIcon.KEYS.name)),
                        mode = TrackerMode.valueOf(obj.optString("mode", TrackerMode.IDLE.name)),
                        rssi = obj.optInt("rssi", -100)
                    )
                )
            }
            _items.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveItems(list: List<TrackedItem>) {
        try {
            val jsonArray = JSONArray()
            list.forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("name", item.name)
                obj.put("macAddress", item.macAddress)
                obj.put("icon", item.icon.name)
                obj.put("mode", item.mode.name)
                obj.put("rssi", item.rssi)
                jsonArray.put(obj)
            }
            prefs.edit().putString("items_json", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
