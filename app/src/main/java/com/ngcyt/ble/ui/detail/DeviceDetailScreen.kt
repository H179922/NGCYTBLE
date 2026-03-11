package com.ngcyt.ble.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ngcyt.ble.domain.model.ThreatAssessment
import com.ngcyt.ble.domain.model.ThreatLevel
import com.ngcyt.ble.domain.model.ThreatSource

// -- Threat level colors --

private val ThreatMinimalColor = Color(0xFF4CAF50)
private val ThreatLowColor = Color(0xFF8BC34A)
private val ThreatMediumColor = Color(0xFFFF9800)
private val ThreatHighColor = Color(0xFFF44336)
private val ThreatCriticalColor = Color(0xFFB71C1C)

private fun ThreatLevel.color(): Color = when (this) {
    ThreatLevel.MINIMAL -> ThreatMinimalColor
    ThreatLevel.LOW -> ThreatLowColor
    ThreatLevel.MEDIUM -> ThreatMediumColor
    ThreatLevel.HIGH -> ThreatHighColor
    ThreatLevel.CRITICAL -> ThreatCriticalColor
}

private fun ThreatLevel.label(): String = when (this) {
    ThreatLevel.MINIMAL -> "Minimal"
    ThreatLevel.LOW -> "Low"
    ThreatLevel.MEDIUM -> "Medium"
    ThreatLevel.HIGH -> "High"
    ThreatLevel.CRITICAL -> "Critical"
}

// -- Screen --

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: DeviceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            else -> {
                DeviceDetailContent(
                    mac = viewModel.mac,
                    state = state,
                    onToggleIgnore = viewModel::toggleIgnore,
                    onFindSimilar = viewModel::findSimilarDevices,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun DeviceDetailContent(
    mac: String,
    state: DeviceDetailUiState,
    onToggleIgnore: () -> Unit,
    onFindSimilar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val threat = state.threat

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // -- Header --
        item {
            DeviceHeader(mac = mac, threat = threat)
        }

        // -- Threat Assessment --
        if (threat != null) {
            item {
                ThreatAssessmentSection(threat = threat, showSource = state.hasMultipleSources)
            }
        }

        // -- Fingerprint (only show when device is correlated) --
        if (state.fingerprint != null && state.fingerprint.clusterConfidence > 0.0) {
            item {
                FingerprintSection(fingerprint = state.fingerprint)
            }
        }

        // -- Behavioral Similarity --
        item {
            BehavioralSimilaritySection(
                similarDevices = state.similarDevices,
                isLoading = state.isSimilarityLoading,
                onFindSimilar = onFindSimilar,
            )
        }

        // -- Location --
        if (threat?.latitude != null && threat.longitude != null) {
            item {
                LocationSection(threat = threat)
            }
        }

        // -- Actions --
        item {
            ActionsSection(
                isIgnored = state.isIgnored,
                onToggleIgnore = onToggleIgnore,
            )
        }
    }
}

// -- Header --

@Composable
private fun DeviceHeader(mac: String, threat: ThreatAssessment?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Device label if available
            val label = threat?.deviceLabel ?: threat?.deviceName
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = mac,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (threat != null) {
                Spacer(modifier = Modifier.height(12.dp))

                // Threat score as large number
                Text(
                    text = "${threat.threatScore}",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 64.sp,
                    ),
                    color = threat.threatLevel.color(),
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Threat level badge
                ThreatLevelBadge(level = threat.threatLevel)

                if (threat.isMacRandomized) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text("MAC Randomized") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No threat data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThreatLevelBadge(level: ThreatLevel) {
    val bgColor = level.color()
    val textColor = if (level == ThreatLevel.CRITICAL || level == ThreatLevel.HIGH) {
        Color.White
    } else {
        Color.Black
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = level.label(),
            color = textColor,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
    }
}

// -- Threat Assessment Section --

@Composable
private fun ThreatAssessmentSection(threat: ThreatAssessment, showSource: Boolean = false) {
    SectionCard(title = "Threat Assessment") {
        // Reasoning
        Text(
            text = threat.reasoning,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Duration
        LabeledValue(
            label = "Duration",
            value = formatDuration(threat.durationMinutes),
        )

        // Source — only shown when threats come from multiple sources
        if (showSource) {
            Spacer(modifier = Modifier.height(8.dp))
            LabeledValue(
                label = "Source",
                value = when (threat.source) {
                    ThreatSource.BLE_LOCAL -> "BLE Local"
                    ThreatSource.WIFI_PI -> "WiFi Pi"
                },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Time windows summary
        if (threat.timeBucketsPresent.isNotEmpty()) {
            LabeledValue(
                label = "Time Windows",
                value = "${threat.timeBucketsPresent.size} period(s)",
            )
        }

        // Service UUIDs in threat
        if (threat.serviceUuids.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Service UUIDs",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            threat.serviceUuids.forEach { uuid ->
                Text(
                    text = uuid,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

// -- Fingerprint Section --

@Composable
private fun FingerprintSection(fingerprint: FingerprintInfo) {
    SectionCard(title = "Device Fingerprint") {
        // Cluster confidence bar
        Text(
            text = "Cluster Confidence",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LinearProgressIndicator(
                progress = { (fingerprint.clusterConfidence / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${fingerprint.clusterConfidence.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Associated MACs
        if (fingerprint.associatedMacs.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Associated MACs (${fingerprint.associatedMacs.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            fingerprint.associatedMacs.sorted().forEach { assocMac ->
                Text(
                    text = assocMac,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }

        // Service UUIDs from fingerprint
        if (fingerprint.serviceUuids.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Fingerprint Service UUIDs (${fingerprint.serviceUuids.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            fingerprint.serviceUuids.sorted().forEach { uuid ->
                Text(
                    text = uuid,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

// -- Behavioral Similarity Section --

@Composable
private fun BehavioralSimilaritySection(
    similarDevices: List<SimilarDevice>,
    isLoading: Boolean,
    onFindSimilar: () -> Unit,
) {
    SectionCard(title = "Behavioral Similarity") {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else if (similarDevices.isEmpty()) {
            Text(
                text = "No behaviorally similar devices found.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onFindSimilar) {
                Text("Find Similar Devices")
            }
        } else {
            similarDevices.forEach { device ->
                SimilarDeviceRow(device = device)
                if (device != similarDevices.last()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onFindSimilar) {
                Text("Refresh Similar Devices")
            }
        }
    }
}

@Composable
private fun SimilarDeviceRow(device: SimilarDevice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.deviceId,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${device.adCount} ads",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (device.usesRandomization) {
                    Text(
                        text = "randomized",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }

        // Similarity score
        val pct = (device.similarity * 100).toInt()
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = "$pct%",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// -- Location Section --

@Composable
private fun LocationSection(threat: ThreatAssessment) {
    SectionCard(title = "Location") {
        LabeledValue(label = "Latitude", value = "%.6f".format(threat.latitude))
        Spacer(modifier = Modifier.height(4.dp))
        LabeledValue(label = "Longitude", value = "%.6f".format(threat.longitude))
        if (threat.locationAccuracy != null) {
            Spacer(modifier = Modifier.height(4.dp))
            LabeledValue(label = "Accuracy", value = "%.1f m".format(threat.locationAccuracy))
        }
    }
}

// -- Actions Section --

@Composable
private fun ActionsSection(
    isIgnored: Boolean,
    onToggleIgnore: () -> Unit,
) {
    SectionCard(title = "Actions") {
        Button(
            onClick = onToggleIgnore,
            modifier = Modifier.fillMaxWidth(),
            colors = if (isIgnored) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            },
        ) {
            Text(if (isIgnored) "Remove from Ignore List" else "Add to Ignore List")
        }
    }
}

// -- Shared composables --

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatDuration(minutes: Double): String {
    return when {
        minutes < 1.0 -> "< 1 min"
        minutes < 60.0 -> "${minutes.toInt()} min"
        else -> {
            val hours = (minutes / 60).toInt()
            val remaining = (minutes % 60).toInt()
            if (remaining > 0) "${hours}h ${remaining}m" else "${hours}h"
        }
    }
}
