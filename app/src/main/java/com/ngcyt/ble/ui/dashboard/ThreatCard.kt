package com.ngcyt.ble.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngcyt.ble.domain.model.ThreatAssessment
import com.ngcyt.ble.domain.model.ThreatLevel
import com.ngcyt.ble.domain.model.ThreatSource

private val ThreatLevelColors = mapOf(
    ThreatLevel.MINIMAL to Color.Gray,
    ThreatLevel.LOW to Color(0xFF2196F3),
    ThreatLevel.MEDIUM to Color(0xFFFFC107),
    ThreatLevel.HIGH to Color(0xFFFF9800),
    ThreatLevel.CRITICAL to Color(0xFFF44336),
)

@Composable
fun ThreatCard(
    threat: ThreatAssessment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = ThreatLevelColors[threat.threatLevel] ?: Color.Gray
    val isElevated = threat.threatLevel == ThreatLevel.HIGH || threat.threatLevel == ThreatLevel.CRITICAL
    val elevation = if (isElevated) 6.dp else 2.dp
    val borderWidth = if (isElevated) 3.dp else 2.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isElevated) {
                    Modifier.shadow(8.dp, RoundedCornerShape(12.dp), ambientColor = borderColor, spotColor = borderColor)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Color-coded left border
            Box(
                modifier = Modifier
                    .width(borderWidth)
                    .fillMaxHeight()
                    .background(borderColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Top row: MAC, score badge, level text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // MAC address (last 8 chars)
                    val displayMac = if (threat.mac.length > 8) {
                        threat.mac.takeLast(8)
                    } else {
                        threat.mac
                    }
                    Text(
                        text = displayMac,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Threat score badge
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = borderColor,
                        ) {
                            Text(
                                text = "${threat.threatScore}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                color = if (threat.threatLevel == ThreatLevel.MEDIUM) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }

                        // Threat level text
                        Text(
                            text = threat.threatLevel.value.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = borderColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Source badge and MAC randomization warning
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (threat.source == ThreatSource.WIFI_PI) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                text = "Via Pi",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }

                    if (threat.isMacRandomized) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = "\u26A0 MAC Randomized",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                // Details row: time windows, duration, service UUIDs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (threat.timeBucketsPresent.isNotEmpty()) {
                        Column {
                            Text(
                                text = "Time Windows",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = threat.timeBucketsPresent.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Duration",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${threat.durationMinutes.toInt()} min",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (threat.serviceUuids.isNotEmpty()) {
                        Column {
                            Text(
                                text = "Services",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${threat.serviceUuids.size} UUID(s)",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                // Location accuracy if available
                if (threat.locationAccuracy != null) {
                    Text(
                        text = "Location accuracy: \u00B1${threat.locationAccuracy.toInt()}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Reasoning
                Text(
                    text = threat.reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
