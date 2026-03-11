package com.ngcyt.ble.ui.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngcyt.ble.data.companion.CompanionStatus
import com.ngcyt.ble.domain.model.ThreatAssessment
import com.ngcyt.ble.domain.model.ThreatLevel

@Composable
fun CompanionScreen(
    viewModel: CompanionViewModel = hiltViewModel(),
) {
    val piUrl by viewModel.piUrl.collectAsStateWithLifecycle()
    val status by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val threats by viewModel.wifiThreats.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Pi Companion",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Connect to a Raspberry Pi running CYT WiFi scanner",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            ConnectionCard(
                piUrl = piUrl,
                status = status,
                onUrlChange = viewModel::updatePiUrl,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect,
            )
        }

        if (status == CompanionStatus.DISCONNECTED && threats.isEmpty()) {
            item {
                EmptyState()
            }
        } else if (threats.isNotEmpty()) {
            item {
                Text(
                    text = "WiFi Threats (${threats.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(threats, key = { it.mac }) { threat ->
                WifiThreatCard(threat = threat)
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    piUrl: String,
    status: CompanionStatus,
    onUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = piUrl,
                onValueChange = onUrlChange,
                label = { Text("Pi URL") },
                placeholder = { Text("http://192.168.1.100:5000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = status != CompanionStatus.CONNECTING,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusIndicator(status = status)

                if (status == CompanionStatus.CONNECTED || status == CompanionStatus.CONNECTING) {
                    OutlinedButton(onClick = onDisconnect) {
                        Text("Disconnect")
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        enabled = piUrl.isNotBlank(),
                    ) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(status: CompanionStatus) {
    val (color, label) = when (status) {
        CompanionStatus.CONNECTED -> Color(0xFF4CAF50) to "Connected"
        CompanionStatus.CONNECTING -> Color(0xFFFFC107) to "Connecting..."
        CompanionStatus.ERROR -> Color(0xFFF44336) to "Error"
        CompanionStatus.DISCONNECTED -> Color(0xFF9E9E9E) to "Disconnected"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "No Pi Connected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter the URL of your Raspberry Pi running the CYT WiFi scanner and tap Connect to start receiving WiFi threat data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WifiThreatCard(threat: ThreatAssessment) {
    val threatColor = when (threat.threatLevel) {
        ThreatLevel.CRITICAL -> Color(0xFFF44336)
        ThreatLevel.HIGH -> Color(0xFFFF5722)
        ThreatLevel.MEDIUM -> Color(0xFFFFC107)
        ThreatLevel.LOW -> Color(0xFF8BC34A)
        ThreatLevel.MINIMAL -> Color(0xFF4CAF50)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = threat.mac,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = threat.deviceType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = threatColor),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "${threat.threatScore}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }

            if (threat.reasoning.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = threat.reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (threat.serviceUuids.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "SSIDs: ${threat.serviceUuids.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Duration: ${"%.1f".format(threat.durationMinutes)} min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
