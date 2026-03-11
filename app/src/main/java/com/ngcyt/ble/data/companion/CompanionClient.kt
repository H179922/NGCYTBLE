package com.ngcyt.ble.data.companion

import com.ngcyt.ble.domain.model.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*

enum class CompanionStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

class CompanionClient(
    private var piUrl: String = "",
    private var pollIntervalSeconds: Int = 30,
) {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _status = MutableStateFlow(CompanionStatus.DISCONNECTED)
    val status: StateFlow<CompanionStatus> = _status

    private val _wifiThreats = MutableStateFlow<List<ThreatAssessment>>(emptyList())
    val wifiThreats: StateFlow<List<ThreatAssessment>> = _wifiThreats

    private var pollJob: Job? = null

    fun configure(url: String, intervalSeconds: Int = 30) {
        piUrl = url.trimEnd('/')
        pollIntervalSeconds = intervalSeconds
    }

    fun startPolling(scope: CoroutineScope) {
        if (piUrl.isBlank()) return
        stopPolling()
        pollJob = scope.launch {
            while (isActive) {
                fetchThreats()
                delay(pollIntervalSeconds * 1000L)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        _status.value = CompanionStatus.DISCONNECTED
    }

    private suspend fun fetchThreats() {
        _status.value = CompanionStatus.CONNECTING
        try {
            val response = client.get("$piUrl/api/threats")
            if (response.status.value in 200..299) {
                val body = response.bodyAsText()
                val threats = parseThreats(body)
                _wifiThreats.value = threats
                _status.value = CompanionStatus.CONNECTED
            } else {
                _status.value = CompanionStatus.ERROR
            }
        } catch (_: Exception) {
            _status.value = CompanionStatus.ERROR
        }
    }

    private fun parseThreats(responseBody: String): List<ThreatAssessment> {
        return try {
            val jsonArray = json.parseToJsonElement(responseBody).jsonArray
            jsonArray.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    ThreatAssessment(
                        mac = obj["mac"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        deviceType = obj["device_type"]?.jsonPrimitive?.content ?: "WiFi",
                        threatScore = obj["threat_score"]?.jsonPrimitive?.int ?: 0,
                        threatLevel = ThreatLevel.fromScore(obj["threat_score"]?.jsonPrimitive?.int ?: 0),
                        timeBucketsPresent = obj["time_buckets"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        durationMinutes = obj["duration_minutes"]?.jsonPrimitive?.double ?: 0.0,
                        serviceUuids = obj["probed_ssids"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        reasoning = obj["reasoning"]?.jsonPrimitive?.content ?: "",
                        physicalDeviceId = obj["physical_device_id"]?.jsonPrimitive?.contentOrNull,
                        associatedMacs = obj["associated_macs"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        fingerprintConfidence = obj["fingerprint_confidence"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        isMacRandomized = obj["is_mac_randomized"]?.jsonPrimitive?.booleanOrNull ?: false,
                        source = ThreatSource.WIFI_PI,
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun close() {
        stopPolling()
        client.close()
    }
}
