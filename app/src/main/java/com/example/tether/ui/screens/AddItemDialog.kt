package com.example.tether.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tether.bluetooth.ScannedDevice
import com.example.tether.model.ItemIcon
import com.example.tether.ui.theme.*

@Composable
fun AddItemDialog(
    connectedDevices: List<ScannedDevice> = emptyList(),
    pairedDevices: List<ScannedDevice> = emptyList(),
    scannedDevices: List<ScannedDevice> = emptyList(),
    existingMacs: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: (name: String, macAddress: String, icon: ItemIcon) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedDevice by remember { mutableStateOf<ScannedDevice?>(null) }
    var selectedIcon by remember { mutableStateOf(ItemIcon.KEYS) }

    val isDuplicate = selectedDevice?.let { dev ->
        existingMacs.any { it.equals(dev.address, ignoreCase = true) }
    } ?: false

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Add New Tracker",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tracker Custom Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tracker Name (e.g. Work Keys)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Category Icon",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ItemIcon.values().take(6).forEach { icon ->
                        val isSelected = selectedIcon == icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedIcon = icon },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getIconVector(icon),
                                contentDescription = icon.name,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Bluetooth Device",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )

                selectedDevice?.let { dev ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 10.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDuplicate) Crimson400.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isDuplicate) "Tracker already added" else dev.name,
                                    color = if (isDuplicate) Crimson400 else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = dev.address,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            Icon(
                                imageVector = if (isDuplicate) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isDuplicate) Crimson400 else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .fillMaxWidth()
                ) {
                    if (connectedDevices.isNotEmpty()) {
                        item {
                            Text(
                                text = "CONNECTED DEVICES",
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                        items(connectedDevices) { dev ->
                            DeviceSelectionCard(
                                device = dev,
                                isSelected = selectedDevice?.address == dev.address,
                                onSelect = {
                                    selectedDevice = dev
                                    if (name.isEmpty()) name = dev.name
                                }
                            )
                        }
                    }

                    if (pairedDevices.isNotEmpty()) {
                        item {
                            Text(
                                text = "PAIRED DEVICES",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                            )
                        }
                        items(pairedDevices) { dev ->
                            DeviceSelectionCard(
                                device = dev,
                                isSelected = selectedDevice?.address == dev.address,
                                onSelect = {
                                    selectedDevice = dev
                                    if (name.isEmpty()) name = dev.name
                                }
                            )
                        }
                    }

                    if (scannedDevices.isNotEmpty()) {
                        item {
                            Text(
                                text = "DISCOVERED NEARBY",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                            )
                        }
                        items(scannedDevices) { dev ->
                            DeviceSelectionCard(
                                device = dev,
                                isSelected = selectedDevice?.address == dev.address,
                                onSelect = {
                                    selectedDevice = dev
                                    if (name.isEmpty()) name = dev.name
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetDev = selectedDevice
                    if (name.isNotBlank() && targetDev != null && !isDuplicate) {
                        onConfirm(name, targetDev.address, selectedIcon)
                    }
                },
                enabled = name.isNotBlank() && selectedDevice != null && !isDuplicate,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(text = "Add Device", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun DeviceSelectionCard(
    device: ScannedDevice,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onSelect() },
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = device.address,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (device.isConnected) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = when {
                        device.isConnected -> "Connected"
                        device.isPaired -> "Paired"
                        else -> "${device.rssi} dBm"
                    },
                    color = if (device.isConnected) MaterialTheme.colorScheme.tertiary else if (device.isPaired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

fun getIconVector(icon: ItemIcon): ImageVector {
    return when (icon) {
        ItemIcon.KEYS -> Icons.Default.VpnKey
        ItemIcon.WALLET -> Icons.Default.AccountBalanceWallet
        ItemIcon.BACKPACK -> Icons.Default.Backpack
        ItemIcon.TOOLBOX -> Icons.Default.Build
        ItemIcon.CAR_KEYS -> Icons.Default.DirectionsCar
        ItemIcon.HEADPHONES -> Icons.Default.Headphones
        ItemIcon.GENERIC -> Icons.Default.Bluetooth
    }
}

