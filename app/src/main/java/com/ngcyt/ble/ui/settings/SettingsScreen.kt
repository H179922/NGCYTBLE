package com.ngcyt.ble.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngcyt.ble.data.settings.LocationMode

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val minAlertScore by viewModel.minAlertScore.collectAsStateWithLifecycle()
    val scanInterval by viewModel.scanIntervalSeconds.collectAsStateWithLifecycle()
    val retentionDays by viewModel.retentionDays.collectAsStateWithLifecycle()
    val locationMode by viewModel.locationMode.collectAsStateWithLifecycle()
    val externalApiUrl by viewModel.externalApiUrl.collectAsStateWithLifecycle()
    val externalApiKey by viewModel.externalApiKey.collectAsStateWithLifecycle()
    val externalApiMode by viewModel.externalApiMode.collectAsStateWithLifecycle()
    val companionPiUrl by viewModel.companionPiUrl.collectAsStateWithLifecycle()
    val companionPollInterval by viewModel.companionPollInterval.collectAsStateWithLifecycle()
    val ignoreMacs by viewModel.ignoreMacs.collectAsStateWithLifecycle()

    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Data") },
            text = { Text("This will clear the ignore list. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        // -- Detection --
        item { SectionHeader("Detection") }
        item {
            SettingsCard {
                Text(
                    text = "Minimum Alert Score: $minAlertScore",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = minAlertScore.toFloat(),
                    onValueChange = { viewModel.updateMinAlertScore(it.toInt()) },
                    valueRange = 0f..100f,
                    steps = 19,
                )

                Spacer(modifier = Modifier.height(8.dp))
                IntField(
                    label = "Scan Interval (seconds)",
                    value = scanInterval,
                    onValueChange = { viewModel.updateScanInterval(it) },
                )
            }
        }

        // -- Location --
        item { SectionHeader("Location") }
        item {
            SettingsCard {
                Text(
                    text = "Location Mode",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(modifier = Modifier.selectableGroup()) {
                    LocationMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = locationMode == mode,
                                    onClick = { viewModel.updateLocationMode(mode) },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = locationMode == mode,
                                onClick = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = mode.name.replace('_', ' '),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        // -- External API --
        item { SectionHeader("External API") }
        item {
            ExternalApiSection(
                url = externalApiUrl,
                key = externalApiKey,
                mode = externalApiMode,
                onUpdate = viewModel::updateExternalApi,
            )
        }

        // -- Pi Companion --
        item { SectionHeader("Pi Companion") }
        item {
            CompanionSection(
                url = companionPiUrl,
                pollInterval = companionPollInterval,
                onUpdate = viewModel::updateCompanionPi,
            )
        }

        // -- Ignore List --
        item { SectionHeader("Ignore List") }
        item {
            IgnoreListSection(
                macs = ignoreMacs,
                onAdd = viewModel::addIgnoreMac,
                onRemove = viewModel::removeIgnoreMac,
            )
        }

        // -- Data --
        item { SectionHeader("Data") }
        item {
            SettingsCard {
                IntField(
                    label = "Retention Days",
                    value = retentionDays,
                    onValueChange = { viewModel.updateRetentionDays(it) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showClearDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear All Data")
                }
            }
        }

        // -- About --
        item { SectionHeader("About") }
        item {
            SettingsCard {
                Text(
                    text = "NGCYT BLE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "BLE surveillance detection for Android",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Part of the Chasing Your Tail project",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Version 1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun IntField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    var text by rememberSaveable(value) { mutableStateOf(value.toString()) }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExternalApiSection(
    url: String?,
    key: String?,
    mode: String,
    onUpdate: (String?, String?, String?) -> Unit,
) {
    var urlText by rememberSaveable(url) { mutableStateOf(url ?: "") }
    var keyText by rememberSaveable(key) { mutableStateOf(key ?: "") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }
    val apiModes = listOf("ALERTS_ONLY", "FULL_TELEMETRY")

    SettingsCard {
        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            label = { Text("API URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = keyText,
            onValueChange = { keyText = it },
            label = { Text("API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide key" else "Show key",
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = modeExpanded,
            onExpandedChange = { modeExpanded = it },
        ) {
            OutlinedTextField(
                value = mode.replace('_', ' '),
                onValueChange = {},
                readOnly = true,
                label = { Text("Mode") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = modeExpanded,
                onDismissRequest = { modeExpanded = false },
            ) {
                apiModes.forEach { apiMode ->
                    DropdownMenuItem(
                        text = { Text(apiMode.replace('_', ' ')) },
                        onClick = {
                            onUpdate(
                                urlText.ifBlank { null },
                                keyText.ifBlank { null },
                                apiMode,
                            )
                            modeExpanded = false
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                onUpdate(
                    urlText.ifBlank { null },
                    keyText.ifBlank { null },
                    mode,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save API Settings")
        }
    }
}

@Composable
private fun CompanionSection(
    url: String?,
    pollInterval: Int,
    onUpdate: (String?, Int?) -> Unit,
) {
    var urlText by rememberSaveable(url) { mutableStateOf(url ?: "") }
    var intervalText by rememberSaveable(pollInterval) { mutableStateOf(pollInterval.toString()) }

    SettingsCard {
        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            label = { Text("Pi URL") },
            placeholder = { Text("http://192.168.1.100:5000") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = intervalText,
            onValueChange = { intervalText = it },
            label = { Text("Poll Interval (seconds)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                onUpdate(
                    urlText.ifBlank { null },
                    intervalText.toIntOrNull(),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Companion Settings")
        }
    }
}

@Composable
private fun IgnoreListSection(
    macs: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var newMac by rememberSaveable { mutableStateOf("") }

    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newMac,
                onValueChange = { newMac = it },
                label = { Text("Add MAC") },
                placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    onAdd(newMac)
                    newMac = ""
                },
                enabled = newMac.isNotBlank(),
            ) {
                Text("Add")
            }
        }

        if (macs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            macs.sorted().forEach { mac ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = mac,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(onClick = { onRemove(mac) }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove $mac",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No ignored MACs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
