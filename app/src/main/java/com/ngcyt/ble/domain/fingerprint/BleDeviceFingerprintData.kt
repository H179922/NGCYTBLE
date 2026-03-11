package com.ngcyt.ble.domain.fingerprint

data class BleDeviceFingerprintData(
    var primaryId: String,
    val associatedMacs: MutableSet<String> = mutableSetOf(),
    val serviceUuids: MutableSet<String> = mutableSetOf(),
    val uniqueServiceUuids: MutableSet<String> = mutableSetOf(),
    val manufacturerData: MutableMap<Int, ByteArray> = mutableMapOf(),
    var deviceName: String? = null,
    var txPower: Int = 0,
    var connectable: Boolean = true,
    val timing: TimingProfile = TimingProfile(),
    val rssiHistory: MutableList<Int> = mutableListOf(),
    var firstSeen: Double = 0.0,
    var lastSeen: Double = 0.0,
    var totalAdvertisements: Int = 0,
    var clusterConfidence: Double = 0.0,
) {
    fun addServiceUuid(uuid: String) {
        serviceUuids.add(uuid)
        if (!isUbiquitousUuid(uuid)) {
            uniqueServiceUuids.add(uuid)
        }
    }

    fun getUuidFingerprintStrength(): Double {
        val count = uniqueServiceUuids.size
        return when {
            count >= 4 -> 0.9
            count == 3 -> 0.7
            count == 2 -> 0.5
            count == 1 -> 0.3
            else -> 0.0
        }
    }

    fun mergeFrom(other: BleDeviceFingerprintData) {
        associatedMacs.addAll(other.associatedMacs)
        serviceUuids.addAll(other.serviceUuids)
        uniqueServiceUuids.addAll(other.uniqueServiceUuids)
        other.manufacturerData.forEach { (k, v) -> manufacturerData.putIfAbsent(k, v) }
        if (other.firstSeen < firstSeen || firstSeen == 0.0) firstSeen = other.firstSeen
        if (other.lastSeen > lastSeen) lastSeen = other.lastSeen
        totalAdvertisements += other.totalAdvertisements
        other.timing // Timing merge would need probe_times exposed; skip for now
    }
}
