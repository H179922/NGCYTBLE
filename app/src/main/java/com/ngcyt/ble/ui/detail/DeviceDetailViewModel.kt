package com.ngcyt.ble.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ngcyt.ble.data.settings.AppSettings
import com.ngcyt.ble.domain.detection.DetectionEngine
import com.ngcyt.ble.domain.fingerprint.BleFingerprintEngineImpl
import com.ngcyt.ble.domain.model.ThreatAssessment
import com.ngcyt.ble.domain.similarity.BehaviorSimilarityEngineImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SimilarDevice(
    val deviceId: String,
    val similarity: Float,
    val adCount: Int,
    val usesRandomization: Boolean,
)

data class FingerprintInfo(
    val associatedMacs: Set<String>,
    val clusterConfidence: Double,
    val serviceUuids: Set<String>,
)

data class DeviceDetailUiState(
    val isLoading: Boolean = true,
    val threat: ThreatAssessment? = null,
    val fingerprint: FingerprintInfo? = null,
    val similarDevices: List<SimilarDevice> = emptyList(),
    val isSimilarityLoading: Boolean = false,
    val isIgnored: Boolean = false,
    val hasMultipleSources: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val detectionEngine: DetectionEngine,
    private val fingerprintEngine: BleFingerprintEngineImpl,
    private val similarityEngine: BehaviorSimilarityEngineImpl,
    private val appSettings: AppSettings,
) : ViewModel() {

    val mac: String = checkNotNull(savedStateHandle["mac"]) { "Missing 'mac' argument" }

    private val _uiState = MutableStateFlow(DeviceDetailUiState())
    val uiState: StateFlow<DeviceDetailUiState> = _uiState.asStateFlow()

    init {
        loadDeviceDetails()
    }

    private fun loadDeviceDetails() {
        viewModelScope.launch {
            try {
                // Find threat assessment for this MAC
                val allThreats = detectionEngine.getAllThreats(minScore = 0)
                val threat = allThreats.find { it.mac == mac }

                // Load fingerprint data
                val fpData = fingerprintEngine.getDeviceForMac(mac)
                val fingerprintInfo = fpData?.let {
                    val allMacs = fingerprintEngine.getAllMacsForDevice(mac)
                    val fpDetail = fingerprintEngine.getFingerprint(mac)
                    FingerprintInfo(
                        associatedMacs = allMacs,
                        clusterConfidence = it.clusterConfidence,
                        serviceUuids = fpDetail?.serviceUuids ?: emptySet(),
                    )
                }

                // Check ignore status
                val ignoredMacs = appSettings.ignoreMacs.first()
                val isIgnored = mac in ignoredMacs

                // Check if threats come from multiple sources
                val sources = allThreats.map { it.source }.toSet()
                val hasMultipleSources = sources.size > 1

                // Load similar devices
                val similar = loadSimilarDevices()

                _uiState.value = DeviceDetailUiState(
                    isLoading = false,
                    threat = threat,
                    fingerprint = fingerprintInfo,
                    similarDevices = similar,
                    isIgnored = isIgnored,
                    hasMultipleSources = hasMultipleSources,
                )
            } catch (e: Exception) {
                _uiState.value = DeviceDetailUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load device details",
                )
            }
        }
    }

    private fun loadSimilarDevices(): List<SimilarDevice> {
        return try {
            val effectiveId = _uiState.value.threat?.physicalDeviceId ?: mac
            similarityEngine.findSimilarDevices(
                deviceId = effectiveId,
                nResults = 10,
                minSimilarity = 0.7f,
            ).map { result ->
                SimilarDevice(
                    deviceId = result["device_id"] as String,
                    similarity = (result["similarity"] as? Float) ?: 0f,
                    adCount = (result["ad_count"] as? Int) ?: 0,
                    usesRandomization = (result["uses_randomization"] as? Boolean) ?: false,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun findSimilarDevices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSimilarityLoading = true)
            val similar = loadSimilarDevices()
            _uiState.value = _uiState.value.copy(
                similarDevices = similar,
                isSimilarityLoading = false,
            )
        }
    }

    fun toggleIgnore() {
        viewModelScope.launch {
            val currentlyIgnored = _uiState.value.isIgnored
            if (currentlyIgnored) {
                appSettings.removeIgnoreMac(mac)
            } else {
                appSettings.addIgnoreMac(mac)
            }
            _uiState.value = _uiState.value.copy(isIgnored = !currentlyIgnored)
        }
    }
}
