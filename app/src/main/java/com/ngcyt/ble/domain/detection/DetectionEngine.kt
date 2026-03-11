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

        const val FIRST_SIGHTING_POINTS = 5
        const val BUCKET_PRESENCE_POINTS = 15
        const val MAX_BUCKET_POINTS = 60
        const val SIGHTING_COUNT_BONUS_LOW = 5   // 2+ sightings
        const val SIGHTING_COUNT_BONUS_HIGH = 5  // 5+ sightings (additive)
        const val DURATION_5MIN_BONUS = 5
        const val DURATION_10MIN_BONUS = 10
        const val DURATION_15MIN_BONUS = 15
        const val DURATION_20MIN_BONUS = 20
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

        // Base score: every detected device gets first-sighting points
        score += FIRST_SIGHTING_POINTS

        // Sighting count bonus
        if (sighting.sightingCount >= 5) {
            score += SIGHTING_COUNT_BONUS_LOW + SIGHTING_COUNT_BONUS_HIGH
        } else if (sighting.sightingCount >= 2) {
            score += SIGHTING_COUNT_BONUS_LOW
        }

        // Bucket presence score (starts from 1 bucket)
        val bucketCount = sighting.timeBuckets.size
        score += minOf(bucketCount * BUCKET_PRESENCE_POINTS, MAX_BUCKET_POINTS)

        // Duration bonus (graduated thresholds)
        val durationMins = (sighting.lastSeen - sighting.firstSeen) / 60.0
        if (durationMins >= 20) {
            score += DURATION_20MIN_BONUS
        } else if (durationMins >= 15) {
            score += DURATION_15MIN_BONUS
        } else if (durationMins >= 10) {
            score += DURATION_10MIN_BONUS
        } else if (durationMins >= 5) {
            score += DURATION_5MIN_BONUS
        }

        // Service UUID match bonus
        if (sighting.serviceUuids.isNotEmpty()) {
            score += SERVICE_UUID_BONUS
        }

        // MAC correlation bonus — use stored state or explicit parameter
        if (isCorrelated || sighting.isCorrelated) {
            score += MAC_CORRELATION_BONUS
        }

        return score.coerceIn(0, 100)
    }

    fun generateReasoning(sighting: DeviceSighting, score: Int, correlatedCluster: String? = null): String {
        val reasons = mutableListOf<String>()

        val bucketCount = sighting.timeBuckets.size
        if (bucketCount > 1) {
            reasons.add("Device seen in $bucketCount time periods")
        } else {
            reasons.add("First sighting detected")
        }

        if (sighting.sightingCount >= 5) {
            reasons.add("Advertising frequently (${sighting.sightingCount} sightings)")
        }

        val durationMins = (sighting.lastSeen - sighting.firstSeen) / 60.0
        if (durationMins >= 5) {
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
        correlatedCluster: String? = null,
        scanRecord: ByteArray? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        locationAccuracy: Float? = null,
        locationProvider: String? = null,
    ): ThreatAssessment? {
        if (mac in ignoreMacs) return null

        // Use caller-provided correlation result; fall back to internal fingerprinting
        var effectiveCluster = correlatedCluster
        if (effectiveCluster == null && fingerprintEngine != null && scanRecord != null) {
            effectiveCluster = fingerprintEngine.processAdvertisement(
                mac = mac,
                scanRecord = scanRecord,
                timestamp = timestamp,
            )
        }

        // Record behavior for similarity search
        if (similarityEngine != null) {
            val deviceId = effectiveCluster ?: mac
            similarityEngine.recordDeviceBehavior(
                deviceId = deviceId,
                timestamp = timestamp,
                serviceUuid = serviceUuid,
                usesRandomization = effectiveCluster != null,
            )
        }

        val effectiveId = effectiveCluster ?: mac
        val isCorrelated = effectiveCluster != null

        // Update or create sighting
        val existing = deviceHistory[effectiveId]
        if (existing != null) {
            existing.addSighting(
                timeBucket, timestamp, serviceUuid,
                latitude, longitude, locationAccuracy, locationProvider,
            )
            if (isCorrelated) {
                existing.isCorrelated = true
                existing.correlatedCluster = effectiveCluster
            }
        } else {
            deviceHistory[effectiveId] = DeviceSighting(
                mac = effectiveId,
                deviceType = deviceType,
                firstSeen = timestamp,
                lastSeen = timestamp,
                isCorrelated = isCorrelated,
                correlatedCluster = effectiveCluster,
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
        val score = calculateThreatScore(sighting, isCorrelated)

        val assessment = ThreatAssessment(
            mac = mac,
            deviceType = deviceType,
            threatScore = score,
            threatLevel = ThreatLevel.fromScore(score),
            timeBucketsPresent = sighting.timeBuckets.sorted(),
            durationMinutes = (sighting.lastSeen - sighting.firstSeen) / 60.0,
            serviceUuids = sighting.serviceUuids.sorted(),
            reasoning = generateReasoning(sighting, score, effectiveCluster),
            physicalDeviceId = effectiveCluster,
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

        // Only fire alert callbacks for meaningful scores
        if (score >= 20) {
            alertCallbacks.forEach { it(assessment) }
        }

        return assessment
    }

    fun rotateTimeBuckets(maxBucketAge: Int = 10) {
        // Remove devices whose most recent bucket is too old
        // (i.e., not seen in the last maxBucketAge rotations)
        // Bucket names are "bucket_N" where N is the generation counter
        val toRemove = mutableListOf<String>()
        for ((mac, sighting) in deviceHistory) {
            val maxGen = sighting.timeBuckets
                .mapNotNull { it.removePrefix("bucket_").toIntOrNull() }
                .maxOrNull() ?: -1
            // Will be compared against current generation by caller
            // For now, just prune devices not seen in any recent bucket
            if (sighting.timeBuckets.isEmpty()) {
                toRemove.add(mac)
            }
        }
        toRemove.forEach { deviceHistory.remove(it) }
    }

    fun getAllThreats(minScore: Int = 0): List<ThreatAssessment> {
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
                reasoning = generateReasoning(sighting, score, sighting.correlatedCluster),
                physicalDeviceId = sighting.correlatedCluster,
                associatedMacs = if (sighting.isCorrelated && fingerprintEngine != null) {
                    fingerprintEngine.getAllMacsForDevice(mac).filter { it != mac }.sorted()
                } else emptyList(),
                fingerprintConfidence = if (sighting.isCorrelated && fingerprintEngine != null) {
                    fingerprintEngine.getDeviceForMac(mac)?.clusterConfidence ?: 0.0
                } else 0.0,
                isMacRandomized = sighting.isCorrelated,
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
