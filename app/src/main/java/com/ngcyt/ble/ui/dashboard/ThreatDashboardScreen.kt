@file:OptIn(ExperimentalMaterial3Api::class)

package com.ngcyt.ble.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ngcyt.ble.scanner.ScanStatus

@Composable
fun ThreatDashboardScreen(
    onThreatClick: (mac: String) -> Unit,
    viewModel: ThreatDashboardViewModel = hiltViewModel(),
) {
    val threats by viewModel.threats.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanStatus by viewModel.scanStatus.collectAsState()
    val deviceCount by viewModel.deviceCount.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top status bar
        ScanStatusHeader(
            isScanning = isScanning,
            scanStatus = scanStatus,
            deviceCount = deviceCount,
            onStartScan = viewModel::startScan,
            onStopScan = viewModel::stopScan,
        )

        // Threat list with pull-to-refresh
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (threats.isEmpty()) {
                EmptyState(isScanning = isScanning, scanStatus = scanStatus)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = threats,
                        key = { it.mac },
                    ) { threat ->
                        ThreatCard(
                            threat = threat,
                            onClick = { onThreatClick(threat.mac) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanStatusHeader(
    isScanning: Boolean,
    scanStatus: ScanStatus,
    deviceCount: Int,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Status indicator and device count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Scan status dot — color reflects status
                    val dotColor = when (scanStatus) {
                        ScanStatus.SCANNING -> Color(0xFF4CAF50) // green
                        ScanStatus.BLUETOOTH_OFF -> Color(0xFFFF9800) // orange
                        ScanStatus.LOCATION_REQUIRED -> Color(0xFFFF9800) // orange
                        ScanStatus.SCAN_FAILED -> Color(0xFFF44336) // red
                        ScanStatus.PERMISSION_DENIED -> Color(0xFFF44336) // red
                        ScanStatus.IDLE -> Color.Gray
                    }

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )

                    Column {
                        Text(
                            text = statusLabel(scanStatus),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "$deviceCount threat(s) detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Start/Stop button
                FilledTonalButton(
                    onClick = if (isScanning) onStopScan else onStartScan,
                ) {
                    Text(text = if (isScanning) "Stop Scan" else "Start Scan")
                }
            }

            // Status warning banners
            when (scanStatus) {
                ScanStatus.BLUETOOTH_OFF -> {
                    StatusBanner(
                        text = "Bluetooth is off. Enable Bluetooth to resume scanning.",
                        color = Color(0xFFFFF3E0),
                        textColor = Color(0xFFE65100),
                    )
                }
                ScanStatus.LOCATION_REQUIRED -> {
                    StatusBanner(
                        text = "Location services are required for BLE scanning on this Android version.",
                        color = Color(0xFFFFF3E0),
                        textColor = Color(0xFFE65100),
                    )
                }
                ScanStatus.SCAN_FAILED -> {
                    StatusBanner(
                        text = "Scan failed. Try stopping and restarting the scan.",
                        color = Color(0xFFFFEBEE),
                        textColor = Color(0xFFC62828),
                    )
                }
                ScanStatus.PERMISSION_DENIED -> {
                    StatusBanner(
                        text = "Bluetooth permission denied. Grant permission in app settings.",
                        color = Color(0xFFFFEBEE),
                        textColor = Color(0xFFC62828),
                    )
                }
                else -> { /* no banner */ }
            }
        }
    }
}

@Composable
private fun StatusBanner(text: String, color: Color, textColor: Color) {
    Surface(
        color = color,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

private fun statusLabel(status: ScanStatus): String = when (status) {
    ScanStatus.SCANNING -> "Scanning"
    ScanStatus.IDLE -> "Paused"
    ScanStatus.BLUETOOTH_OFF -> "Bluetooth Off"
    ScanStatus.LOCATION_REQUIRED -> "Location Required"
    ScanStatus.SCAN_FAILED -> "Scan Failed"
    ScanStatus.PERMISSION_DENIED -> "Permission Denied"
}

@Composable
private fun EmptyState(isScanning: Boolean, scanStatus: ScanStatus) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when {
                    scanStatus == ScanStatus.BLUETOOTH_OFF -> "Bluetooth is off"
                    scanStatus == ScanStatus.LOCATION_REQUIRED -> "Location services required"
                    scanStatus == ScanStatus.SCAN_FAILED -> "Scan failed"
                    scanStatus == ScanStatus.PERMISSION_DENIED -> "Permission denied"
                    isScanning -> "Scanning for devices..."
                    else -> "No threats detected"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = when {
                    scanStatus == ScanStatus.BLUETOOTH_OFF ->
                        "Enable Bluetooth in your device settings to start scanning."
                    scanStatus == ScanStatus.LOCATION_REQUIRED ->
                        "On this Android version, location must be enabled for BLE scanning."
                    scanStatus == ScanStatus.SCAN_FAILED ->
                        "An error occurred. Try stopping and restarting the scan."
                    scanStatus == ScanStatus.PERMISSION_DENIED ->
                        "Go to app settings to grant the required Bluetooth permission."
                    isScanning ->
                        "Threats will appear here as they are detected."
                    else ->
                        "Start a scan to begin monitoring for BLE surveillance devices."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
        }
    }
}
