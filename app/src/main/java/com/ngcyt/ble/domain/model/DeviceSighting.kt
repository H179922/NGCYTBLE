package com.ngcyt.ble.domain.model

data class DeviceSighting(
    val mac: String,
    val deviceType: String,
    val firstSeen: Double,
    var lastSeen: Double,
    val timeBuckets: MutableSet<String> = mutableSetOf(),
    val serviceUuids: MutableSet<String> = mutableSetOf(),
    var sightingCount: Int = 1,
    var isCorrelated: Boolean = false,
    var correlatedCluster: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,
    var locationAccuracy: Float? = null,
    var locationProvider: String? = null,
) {
    fun addSighting(
        timeBucket: String,
        timestamp: Double,
        serviceUuid: String? = null,
        lat: Double? = null,
        lng: Double? = null,
        accuracy: Float? = null,
        provider: String? = null,
    ) {
        timeBuckets.add(timeBucket)
        lastSeen = maxOf(lastSeen, timestamp)
        sightingCount++
        serviceUuid?.let { serviceUuids.add(it) }
        lat?.let { latitude = it }
        lng?.let { longitude = it }
        accuracy?.let { locationAccuracy = it }
        provider?.let { locationProvider = it }
    }
}
