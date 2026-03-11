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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngcyt.ble.domain.model.ThreatAssessment
import com.ngcyt.ble.domain.model.ThreatLevel
import com.ngcyt.ble.domain.model.ThreatSource

private val ThreatLevelColors = mapOf(
    ThreatLevel.MINIMAL to Color(0xFF78909C),
    ThreatLevel.LOW to Color(0xFF42A5F5),
    ThreatLevel.MEDIUM to Color(0xFFFFB300),
    ThreatLevel.HIGH to Color(0xFFFF7043),
    ThreatLevel.CRITICAL to Color(0xFFEF5350),
)

@Composable
fun ThreatCard(
    threat: ThreatAssessment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = ThreatLevelColors[threat.threatLevel] ?: Color.Gray
    val isElevated = threat.threatLevel == ThreatLevel.HIGH || threat.threatLevel == ThreatLevel.CRITICAL

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isElevated) {
                    Modifier.shadow(8.dp, RoundedCornerShape(12.dp), ambientColor = accentColor, spotColor = accentColor)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isElevated) 4.dp else 1.dp),
    ) {
        // Use IntrinsicSize.Min so the left accent bar matches the content height
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Color-coded left accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Top row: device label + score badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Device label (name or MAC)
                    Column(modifier = Modifier.weight(1f)) {
                        val displayName = threat.deviceLabel
                            ?: threat.deviceName
                            ?: threat.mac.takeLast(8)

                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Show MAC below name if we have a label
                        if (threat.deviceLabel != null || threat.deviceName != null) {
                            Text(
                                text = threat.mac,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Score badge + level
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = accentColor,
                        ) {
                            Text(
                                text = "${threat.threatScore}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                color = if (threat.threatLevel == ThreatLevel.MEDIUM) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }

                        Text(
                            text = threat.threatLevel.value.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Badge row: source, MAC randomized
                val hasBadges = threat.source == ThreatSource.WIFI_PI || threat.isMacRandomized
                if (hasBadges) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (threat.source == ThreatSource.WIFI_PI) {
                            CompactBadge(
                                text = "Via Pi",
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        if (threat.isMacRandomized) {
                            CompactBadge(
                                text = "\u26A0 MAC Randomized",
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatItem("Windows", "${threat.timeBucketsPresent.size}")

                    val duration = threat.durationMinutes
                    StatItem("Duration", when {
                        duration < 1.0 -> "< 1m"
                        duration < 60.0 -> "${duration.toInt()}m"
                        else -> "${(duration / 60).toInt()}h ${(duration % 60).toInt()}m"
                    })

                    if (threat.serviceUuids.isNotEmpty()) {
                        StatItem("Services", "${threat.serviceUuids.size}")
                    }

                    if (threat.fingerprintConfidence > 0.0) {
                        StatItem("Fingerprint", "${threat.fingerprintConfidence.toInt()}%")
                    }
                }

                // Reasoning
                Text(
                    text = threat.reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CompactBadge(text: String, containerColor: Color, contentColor: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontSize = 10.sp,
        )
    }
}
