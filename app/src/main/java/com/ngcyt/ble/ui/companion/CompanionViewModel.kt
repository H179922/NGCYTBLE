package com.ngcyt.ble.ui.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ngcyt.ble.data.companion.CompanionClient
import com.ngcyt.ble.data.companion.CompanionStatus
import com.ngcyt.ble.data.settings.AppSettings
import com.ngcyt.ble.domain.model.ThreatAssessment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompanionViewModel @Inject constructor(
    private val companionClient: CompanionClient,
    private val appSettings: AppSettings,
) : ViewModel() {

    private val _piUrl = MutableStateFlow("")
    val piUrl: StateFlow<String> = _piUrl.asStateFlow()

    val connectionStatus: StateFlow<CompanionStatus> = companionClient.status

    val wifiThreats: StateFlow<List<ThreatAssessment>> = companionClient.wifiThreats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val savedUrl = appSettings.companionPiUrl.first()
            _piUrl.value = savedUrl ?: ""
        }
    }

    fun updatePiUrl(url: String) {
        _piUrl.value = url
    }

    fun connect() {
        val url = _piUrl.value.trim()
        if (url.isBlank()) return
        viewModelScope.launch {
            val pollInterval = appSettings.companionPollInterval.first()
            appSettings.setCompanionPi(url, pollInterval)
            companionClient.configure(url, pollInterval)
            companionClient.startPolling(viewModelScope)
        }
    }

    fun disconnect() {
        companionClient.stopPolling()
    }

    override fun onCleared() {
        super.onCleared()
        companionClient.close()
    }
}
