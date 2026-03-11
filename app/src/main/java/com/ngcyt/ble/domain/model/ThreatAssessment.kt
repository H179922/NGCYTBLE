package com.ngcyt.ble.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ThreatAssessment(
    val mac: String,
    val deviceType: String,
    val threatScore: Int,
    val threatLevel: ThreatLevel,
    val timeBucketsPresent: List<String>,
    val durationMinutes: Double,
    val serviceUuids: List<String>,
    val reasoning: String,
    val physicalDeviceId: String? = null,
    val associatedMacs: List<String> = emptyList(),
    val fingerprintConfidence: Double = 0.0,
    val isMacRandomized: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null,
    val source: ThreatSource = ThreatSource.BLE_LOCAL,
) {
    fun toMap(): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>(
            "mac" to mac,
            "device_type" to deviceType,
            "threat_score" to threatScore,
            "threat_level" to threatLevel.value,
            "time_buckets" to timeBucketsPresent,
            "duration_minutes" to durationMinutes,
            "service_uuids" to serviceUuids,
            "reasoning" to reasoning,
            "source" to source.value,
        )
        if (latitude != null) {
            result["latitude"] = latitude
            result["longitude"] = longitude
            result["location_accuracy"] = locationAccuracy
        }
        if (physicalDeviceId != null) {
            result["physical_device_id"] = physicalDeviceId
            result["associated_macs"] = associatedMacs
            result["fingerprint_confidence"] = fingerprintConfidence
            result["is_mac_randomized"] = isMacRandomized
        }
        return result
    }
}

enum class ThreatSource(val value: String) {
    BLE_LOCAL("ble_local"),
    WIFI_PI("wifi_pi"),
}
