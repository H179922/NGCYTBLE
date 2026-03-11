package com.ngcyt.ble.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private enum class PermissionStep {
    BLUETOOTH,
    LOCATION,
    NOTIFICATIONS,
    COMPLETE,
}

private data class PermissionInfo(
    val step: PermissionStep,
    val title: String,
    val explanation: String,
    val permissions: List<String>,
)

private fun buildPermissionSteps(): List<PermissionInfo> {
    val steps = mutableListOf<PermissionInfo>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        steps.add(
            PermissionInfo(
                step = PermissionStep.BLUETOOTH,
                title = "Bluetooth Access",
                explanation = "NGCYT BLE needs Bluetooth permission to scan for nearby BLE devices and detect potential surveillance trackers. Without this permission, the app cannot function.",
                permissions = listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ),
            )
        )
    }

    steps.add(
        PermissionInfo(
            step = PermissionStep.LOCATION,
            title = "Location Access",
            explanation = "Location permission is required by Android to perform BLE scans. It also allows the app to tag device sightings with location data to help identify devices that follow you across locations.",
            permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION),
        )
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        steps.add(
            PermissionInfo(
                step = PermissionStep.NOTIFICATIONS,
                title = "Notifications",
                explanation = "Enable notifications to receive real-time alerts when a suspicious BLE device is detected near you. You can customize alert thresholds in Settings.",
                permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
            )
        )
    }

    return steps
}

@Composable
fun PermissionScreen(
    onAllGranted: () -> Unit,
) {
    val steps = remember { buildPermissionSteps() }
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    var denied by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            denied = false
            if (currentIndex < steps.size - 1) {
                currentIndex++
            } else {
                currentIndex = steps.size // mark complete
            }
        } else {
            denied = true
        }
    }

    // If no steps needed or all done, navigate
    LaunchedEffect(currentIndex, steps.size) {
        if (steps.isEmpty() || currentIndex >= steps.size) {
            onAllGranted()
        }
    }

    if (currentIndex < steps.size) {
        val current = steps[currentIndex]

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Step ${currentIndex + 1} of ${steps.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Permission Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = current.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (denied) {
                Text(
                    text = "Permission was denied. The app needs this permission to work properly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                OutlinedButton(
                    onClick = {
                        denied = false
                        launcher.launch(current.permissions.toTypedArray())
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Request Again")
                }
            } else {
                Button(
                    onClick = {
                        launcher.launch(current.permissions.toTypedArray())
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Grant ${current.title}")
                }
            }
        }
    }
}
