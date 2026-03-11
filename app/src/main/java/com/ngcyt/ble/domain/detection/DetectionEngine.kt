package com.ngcyt.ble.domain.detection

import com.ngcyt.ble.domain.model.*

class DetectionEngine(
    private val ignoreMacs: Set<String> = emptySet(),
    private val fingerprintEngine: BleFingerprintEngine? = null,
    private val similarityEngine: BehaviorSimilarityEngine? = null,
) {
    companion object {
        val TIME_BUCKETS = mapOf(
            "current" to (0 to 2),
            "0-5min" to (0 to 5),
            "5-10min" to (5 to 10),
            "10-15min" to (10 to 15),
            "15-20min" to (15 to 20),
        )

        const val BUCKET_PRESENCE_POINTS = 20
        const val MAX_BUCKET_POINTS = 60
        const val DURATION_BONUS = 10
        const val LONG_DURATION_BONUS = 20
        const val SERVICE_UUID_BONUS = 10
        const val MAC_CORRELATION_BONUS = 15
    }

    val deviceHistory = mutableMapOf<String, DeviceSighting>()
    private val alertCallbacks = mutableListOf<(ThreatAssessment) -> Unit>()

    fun registerAlertCallback(callback: (ThreatAssessment) -> Unit) {
        alertCallbacks.add(callback)
    }

    fun calculateThreatScore(sighting: DeviceSighting, isCorrelated: Boolean = false): Int {
        var score = 0

        // Base score from number of time buckets (max 60 points)
        val bucketCount = sighting.timeBuckets.size
        if (bucketCount > 1) {
            score += minOf((bucketCount - 1) * BUCKET_PRESENCE_POINTS, MAX_BUCKET_POINTS)
        }

        // Duration bonus
        val durationMins = (sighting.lastSeen - sighting.firstSeen) / 60.0
        if (durationMins >= 20) {
            score += LONG_DURATION_BONUS
        } else if (durationMins >= 15) {
            score += DURATION_BONUS
        }

        // Service UUID match bonus
        if (sighting.serviceUuids.isNotEmpty()) {
            score += SERVICE_UUID_BONUS
        }

        // MAC correlation bonus
        if (isCorrelated) {
            score += MAC_CORRELATION_BONUS
        }

        return score.coerceIn(0, 100)
    }

    fun generateReasoning(sighting: DeviceSighting, score: Int, correlatedCluster: String? = null): String {
        val reasons = mutableListOf<String>()

        val bucketCount = sighting.timeBuckets.size
        if (bucketCount > 1) {
            reasons.add("Device seen in $bucketCount time periods")
        }

        val durationMins = (sighting.lastSeen - sighting.firstSeen) / 60.0
        if (durationMins >= 15) {
            reasons.add("Present for ${durationMins.toInt()} minutes")
        }

        if (sighting.serviceUuids.isNotEmpty()) {
            reasons.add("Advertising ${sighting.serviceUuids.size} service(s)")
        }

        if (correlatedCluster != null && fingerprintEngine != null) {
            val allMacs = fingerprintEngine.getAllMacsForDevice(sighting.mac)
            if (allMacs.size > 1) {
                reasons.add("MAC randomization detected (${allMacs.size} MACs linked)")
            }
        }

        return if (reasons.isEmpty()) {
            "Single brief sighting - likely passerby"
        } else {
            reasons.joinToString("; ")
        }
    }

    fun analyzeDevice(
        mac: String,
        deviceType: String,
        timestamp: Double,
        timeBucket: String,
        serviceUuid: String? = null,
        scanRecord: ByteArray? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        locationAccuracy: Float? = null,
        locationProvider: String? = null,
    ): ThreatAssessment? {
        if (mac in ignoreMacs) return null

        // Process through fingerprinting engine if available
        var correlatedCluster: String? = null
        if (fingerprintEngine != null && scanRecord != null) {
            correlatedCluster = fingerprintEngine.processAdvertisement(
                mac = mac,
                scanRecord = scanRecord,
                timestamp = timestamp,
            )
        }

        // Record behavior for similarity search
        if (similarityEngine != null) {
            val deviceId = correlatedCluster ?: mac
            similarityEngine.recordDeviceBehavior(
                deviceId = deviceId,
                timestamp = timestamp,
                serviceUuid = serviceUuid,
                usesRandomization = correlatedCluster != null,
            )
        }

        val effectiveId = correlatedCluster ?: mac

        // Update or create sighting
        val existing = deviceHistory[effectiveId]
        if (existing != null) {
            existing.addSighting(
                timeBucket, timestamp, serviceUuid,
                latitude, longitude, locationAccuracy, locationProvider,
            )
        } else {
            deviceHistory[effectiveId] = DeviceSighting(
                mac = effectiveId,
                deviceType = deviceType,
                firstSeen = timestamp,
                lastSeen = timestamp,
                latitude = latitude,
                longitude = longitude,
                locationAccuracy = locationAccuracy,
                locationProvider = locationProvider,
            ).apply {
                timeBuckets.add(timeBucket)
                serviceUuid?.let { serviceUuids.add(it) }
            }
        }

        val sighting = deviceHistory[effectiveId]!!
        val isCorrelated = correlatedCluster != null
        val score = calculateThreatScore(sighting, isCorrelated)

        if (score > 0) {
            val assessment = ThreatAssessment(
                mac = mac,
                deviceType = deviceType,
                threatScore = score,
                threatLevel = ThreatLevel.fromScore(score),
                timeBucketsPresent = sighting.timeBuckets.sorted(),
                durationMinutes = (sighting.lastSeen - sighting.firstSeen) / 60.0,
                serviceUuids = sighting.serviceUuids.sorted(),
                reasoning = generateReasoning(sighting, score, correlatedCluster),
                physicalDeviceId = correlatedCluster,
                associatedMacs = if (isCorrelated && fingerprintEngine != null) {
                    fingerprintEngine.getAllMacsForDevice(mac).filter { it != mac }.sorted()
                } else emptyList(),
                fingerprintConfidence = if (isCorrelated && fingerprintEngine != null) {
                    fingerprintEngine.getDeviceForMac(mac)?.clusterConfidence ?: 0.0
                } else 0.0,
                isMacRandomized = isCorrelated,
                latitude = sighting.latitude,
                longitude = sighting.longitude,
                locationAccuracy = sighting.locationAccuracy,
            )

            if (score >= 20) {
                alertCallbacks.forEach { it(assessment) }
            }

            return assessment
        }

        return null
    }

    fun rotateTimeBuckets() {
        // Remove devices only seen in oldest bucket
        val toRemove = deviceHistory.entries
            .filter { it.value.timeBuckets == mutableSetOf("15-20min") }
            .map { it.key }
        toRemove.forEach { deviceHistory.remove(it) }

        // Age the bucket labels
        val bucketMap = mapOf(
            "10-15min" to "15-20min",
            "5-10min" to "10-15min",
            "0-5min" to "5-10min",
            "current" to "0-5min",
        )

        for (sighting in deviceHistory.values) {
            val newBuckets = mutableSetOf<String>()
            for (bucket in sighting.timeBuckets) {
                val mapped = bucketMap[bucket]
                if (mapped != null) {
                    newBuckets.add(mapped)
                } else if (bucket == "15-20min") {
                    newBuckets.add(bucket)
                }
            }
            sighting.timeBuckets.clear()
            sighting.timeBuckets.addAll(newBuckets)
        }
    }

    fun getAllThreats(minScore: Int = 20): List<ThreatAssessment> {
        return deviceHistory.map { (mac, sighting) ->
            val score = calculateThreatScore(sighting)
            ThreatAssessment(
                mac = mac,
                deviceType = sighting.deviceType,
                threatScore = score,
                threatLevel = ThreatLevel.fromScore(score),
                timeBucketsPresent = sighting.timeBuckets.sorted(),
                durationMinutes = (sighting.lastSeen - sighting.firstSeen) / 60.0,
                serviceUuids = sighting.serviceUuids.sorted(),
                reasoning = generateReasoning(sighting, score),
                latitude = sighting.latitude,
                longitude = sighting.longitude,
                locationAccuracy = sighting.locationAccuracy,
            )
        }.filter { it.threatScore >= minScore }
            .sortedByDescending { it.threatScore }
    }

    fun cleanupStaleDevices(maxAgeSeconds: Double = 3600.0, maxDevices: Int = 10000) {
        val now = System.currentTimeMillis() / 1000.0

        val stale = deviceHistory.filter { now - it.value.lastSeen > maxAgeSeconds }.keys.toList()
        stale.forEach { deviceHistory.remove(it) }

        if (deviceHistory.size > maxDevices) {
            val sorted = deviceHistory.entries.sortedBy { it.value.lastSeen }
            val excess = sorted.take(sorted.size - maxDevices)
            excess.forEach { deviceHistory.remove(it.key) }
        }
    }

    fun clearHistory() {
        deviceHistory.clear()
    }
}

// Forward declarations — implemented in Tasks 4 and 5
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
