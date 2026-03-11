package com.ngcyt.ble.domain.detection

// Forward declarations — implemented in fingerprint package (Task 4) and similarity package (Task 5)
interface BleFingerprintEngine {
    fun processAdvertisement(mac: String, scanRecord: ByteArray, timestamp: Double): String?
    fun getAllMacsForDevice(mac: String): Set<String>
    fun getDeviceForMac(mac: String): BleDeviceFingerprint?
}

interface BehaviorSimilarityEngine {
    fun recordDeviceBehavior(deviceId: String, timestamp: Double, serviceUuid: String?, usesRandomization: Boolean)
}

data class BleDeviceFingerprint(
    val primaryId: String,
    val associatedMacs: Set<String>,
    val clusterConfidence: Double,
)
