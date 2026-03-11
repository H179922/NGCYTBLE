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

@Composable
fun ThreatDashboardScreen(
    onThreatClick: (mac: String) -> Unit,
    viewModel: ThreatDashboardViewModel = hiltViewModel(),
) {
    val threats by viewModel.threats.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val deviceCount by viewModel.deviceCount.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top status bar
        ScanStatusHeader(
            isScanning = isScanning,
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
                EmptyState(isScanning = isScanning)
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
    deviceCount: Int,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
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
                // Scan status dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isScanning) Color(0xFF4CAF50) else Color.Gray)
                )

                Column {
                    Text(
                        text = if (isScanning) "Scanning" else "Paused",
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
    }
}

@Composable
private fun EmptyState(isScanning: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isScanning) "Scanning for devices..." else "No threats detected",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (isScanning) {
                    "Threats will appear here as they are detected."
                } else {
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
