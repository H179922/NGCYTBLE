package com.ngcyt.ble.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ngcyt.ble.data.settings.AppSettings
import com.ngcyt.ble.data.settings.LocationMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettings: AppSettings,
) : ViewModel() {

    val minAlertScore: StateFlow<Int> = appSettings.minAlertScore
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 40)

    val scanIntervalSeconds: StateFlow<Int> = appSettings.scanIntervalSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 60)

    val retentionDays: StateFlow<Int> = appSettings.retentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 30)

    val locationMode: StateFlow<LocationMode> = appSettings.locationMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationMode.WIFI_DERIVED)

    val externalApiUrl: StateFlow<String?> = appSettings.externalApiUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val externalApiKey: StateFlow<String?> = appSettings.externalApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val externalApiMode: StateFlow<String> = appSettings.externalApiMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "ALERTS_ONLY")

    val companionPiUrl: StateFlow<String?> = appSettings.companionPiUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val companionPollInterval: StateFlow<Int> = appSettings.companionPollInterval
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 30)

    val ignoreMacs: StateFlow<Set<String>> = appSettings.ignoreMacs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun updateMinAlertScore(score: Int) {
        viewModelScope.launch { appSettings.setMinAlertScore(score) }
    }

    fun updateScanInterval(seconds: Int) {
        viewModelScope.launch { appSettings.setScanInterval(seconds) }
    }

    fun updateRetentionDays(days: Int) {
        viewModelScope.launch { appSettings.setRetentionDays(days) }
    }

    fun updateLocationMode(mode: LocationMode) {
        viewModelScope.launch { appSettings.setLocationMode(mode) }
    }

    fun updateExternalApi(url: String?, key: String?, mode: String?) {
        viewModelScope.launch { appSettings.setExternalApi(url, key, mode) }
    }

    fun updateCompanionPi(url: String?, intervalSeconds: Int?) {
        viewModelScope.launch { appSettings.setCompanionPi(url, intervalSeconds) }
    }

    fun addIgnoreMac(mac: String) {
        val normalized = mac.trim().uppercase()
        if (normalized.isNotBlank()) {
            viewModelScope.launch { appSettings.addIgnoreMac(normalized) }
        }
    }

    fun removeIgnoreMac(mac: String) {
        viewModelScope.launch { appSettings.removeIgnoreMac(mac) }
    }

    fun clearAllData() {
        viewModelScope.launch {
            appSettings.setIgnoreMacs(emptySet())
        }
    }
}
