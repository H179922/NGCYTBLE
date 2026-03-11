package com.ngcyt.ble.ui.dashboard

import android.content.Context
import android.content.Intent
import com.ngcyt.ble.data.settings.AppSettings
import com.ngcyt.ble.domain.detection.DetectionEngine
import com.ngcyt.ble.domain.model.ThreatAssessment
import com.ngcyt.ble.scanner.BleScanService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

@HiltViewModel
class ThreatDashboardViewModel @Inject constructor(
    private val detectionEngine: DetectionEngine,
    private val appSettings: AppSettings,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _threats = MutableStateFlow<List<ThreatAssessment>>(emptyList())
    val threats: StateFlow<List<ThreatAssessment>> = _threats.asStateFlow()

    val isScanning: StateFlow<Boolean> = BleScanService.isScanning

    val deviceCount: StateFlow<Int> = BleScanService.threatCount

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val minScore: StateFlow<Int> = appSettings.minAlertScore
        .stateIn(viewModelScope, SharingStarted.Eagerly, 40)

    init {
        // Refresh threats whenever scan state or threat count changes
        viewModelScope.launch {
            combine(
                BleScanService.isScanning,
                BleScanService.threatCount,
                minScore,
            ) { _, _, score -> score }
                .collect { score ->
                    loadThreats(score)
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadThreats(minScore.value)
            _isRefreshing.value = false
        }
    }

    fun startScan() {
        val intent = Intent(appContext, BleScanService::class.java).apply {
            action = BleScanService.ACTION_START_SCAN
        }
        appContext.startForegroundService(intent)
    }

    fun stopScan() {
        val intent = Intent(appContext, BleScanService::class.java).apply {
            action = BleScanService.ACTION_STOP_SCAN
        }
        appContext.startService(intent)
    }

    private fun loadThreats(minScore: Int) {
        _threats.value = detectionEngine.getAllThreats(minScore)
            .sortedByDescending { it.threatScore }
    }
}
