package com.ngcyt.ble.domain.fingerprint

import com.ngcyt.ble.domain.detection.BleDeviceFingerprint
import com.ngcyt.ble.domain.detection.BleFingerprintEngine
import kotlin.math.sqrt

class BleFingerprintEngineImpl : BleFingerprintEngine {

    companion object {
        const val CLUSTER_THRESHOLD = 45.0
        const val HIGH_CONFIDENCE_THRESHOLD = 70.0

        const val WEIGHT_SERVICE_UUID = 35.0
        const val WEIGHT_MANUFACTURER_DATA = 30.0
        const val WEIGHT_TIMING = 15.0
        const val WEIGHT_TX_POWER = 10.0
        const val WEIGHT_RSSI = 10.0
    }

    private val macFingerprints = mutableMapOf<String, BleDeviceFingerprintData>()
    private val deviceClusters = mutableMapOf<String, BleDeviceFingerprintData>()
    private val macToCluster = mutableMapOf<String, String>()

    fun processAdvertisement(
        mac: String,
        serviceUuids: Set<String>,
        manufacturerData: Map<Int, ByteArray>,
        txPower: Int,
        rssi: Int,
        timestamp: Double,
        deviceName: String? = null,
    ): String? {
        // Get or create fingerprint
        val fp = macFingerprints.getOrPut(mac) {
            BleDeviceFingerprintData(
                primaryId = mac,
                associatedMacs = mutableSetOf(mac),
                firstSeen = timestamp,
            )
        }

        fp.lastSeen = timestamp
        fp.totalAdvertisements++
        fp.txPower = txPower
        fp.timing.addTimestamp(timestamp)
        fp.rssiHistory.add(rssi)
        if (fp.rssiHistory.size > 50) fp.rssiHistory.removeAt(0)
        deviceName?.let { fp.deviceName = it }

        serviceUuids.forEach { fp.addServiceUuid(it) }
        manufacturerData.forEach { (k, v) -> fp.manufacturerData[k] = v }

        // Try to correlate
        return findCorrelation(mac)
    }

    // Implement BleFingerprintEngine interface (used by DetectionEngine)
    override fun processAdvertisement(mac: String, scanRecord: ByteArray, timestamp: Double): String? {
        // Simplified — real implementation would parse scan record
        // The full version is called directly from BleScanner with parsed fields
        return null
    }

    override fun getAllMacsForDevice(mac: String): Set<String> {
        val clusterId = macToCluster[mac]
        if (clusterId != null && clusterId in deviceClusters) {
            return deviceClusters[clusterId]!!.associatedMacs.toSet()
        }
        return setOf(mac)
    }

    override fun getDeviceForMac(mac: String): BleDeviceFingerprint? {
        val clusterId = macToCluster[mac]
        val fp = if (clusterId != null) deviceClusters[clusterId] else macFingerprints[mac]
        return fp?.let {
            BleDeviceFingerprint(
                primaryId = it.primaryId,
                associatedMacs = it.associatedMacs.toSet(),
                clusterConfidence = it.clusterConfidence,
            )
        }
    }

    fun getFingerprint(mac: String): BleDeviceFingerprintData? = macFingerprints[mac]

    private fun findCorrelation(mac: String): String? {
        val fp = macFingerprints[mac] ?: return null
        if (mac in macToCluster) return macToCluster[mac]

        var bestMatch: String? = null
        var bestScore = 0.0

        for ((otherMac, otherFp) in macFingerprints) {
            if (otherMac == mac) continue
            val score = calculateCorrelationScore(fp, otherFp)
            if (score > bestScore && score >= CLUSTER_THRESHOLD) {
                bestScore = score
                bestMatch = otherMac
            }
        }

        if (bestMatch != null) {
            return mergeIntoCluster(mac, bestMatch, bestScore)
        }

        return null
    }

    private fun calculateCorrelationScore(fp1: BleDeviceFingerprintData, fp2: BleDeviceFingerprintData): Double {
        var score = 0.0

        // 1. Service UUID similarity (max 35 pts)
        score += scoreServiceUuidSimilarity(fp1, fp2)

        // 2. Manufacturer data similarity (max 30 pts)
        score += scoreManufacturerDataSimilarity(fp1, fp2)

        // 3. Timing pattern similarity (max 15 pts)
        score += fp1.timing.similarity(fp2.timing) * WEIGHT_TIMING

        // 4. TX power match (max 10 pts)
        if (fp1.txPower != 0 && fp2.txPower != 0 && fp1.txPower == fp2.txPower) {
            score += WEIGHT_TX_POWER
        }

        // 5. RSSI behavior similarity (max 10 pts)
        score += scoreRssiSimilarity(fp1, fp2)

        return minOf(score, 100.0)
    }

    private fun scoreServiceUuidSimilarity(fp1: BleDeviceFingerprintData, fp2: BleDeviceFingerprintData): Double {
        // Use unique (non-ubiquitous) UUIDs
        if (fp1.uniqueServiceUuids.isNotEmpty() && fp2.uniqueServiceUuids.isNotEmpty()) {
            val intersection = fp1.uniqueServiceUuids.intersect(fp2.uniqueServiceUuids)
            val union = fp1.uniqueServiceUuids.union(fp2.uniqueServiceUuids)

            if (intersection.isNotEmpty()) {
                val jaccard = intersection.size.toDouble() / union.size
                val matchCount = intersection.size
                return when {
                    matchCount >= 3 -> WEIGHT_SERVICE_UUID * minOf(jaccard + 0.3, 1.0)
                    matchCount == 2 -> WEIGHT_SERVICE_UUID * jaccard * 0.8
                    else -> WEIGHT_SERVICE_UUID * jaccard * 0.6
                }
            }
        }
        return 0.0
    }

    private fun scoreManufacturerDataSimilarity(fp1: BleDeviceFingerprintData, fp2: BleDeviceFingerprintData): Double {
        if (fp1.manufacturerData.isEmpty() || fp2.manufacturerData.isEmpty()) return 0.0

        // Check for matching company IDs
        val sharedKeys = fp1.manufacturerData.keys.intersect(fp2.manufacturerData.keys)
        if (sharedKeys.isEmpty()) return 0.0

        var totalSimilarity = 0.0
        for (key in sharedKeys) {
            val d1 = fp1.manufacturerData[key] ?: continue
            val d2 = fp2.manufacturerData[key] ?: continue

            // Compare payload bytes
            val minLen = minOf(d1.size, d2.size)
            if (minLen == 0) continue

            var matching = 0
            for (i in 0 until minLen) {
                if (d1[i] == d2[i]) matching++
            }
            totalSimilarity += matching.toDouble() / minLen
        }

        val avgSimilarity = totalSimilarity / sharedKeys.size
        return WEIGHT_MANUFACTURER_DATA * avgSimilarity
    }

    private fun scoreRssiSimilarity(fp1: BleDeviceFingerprintData, fp2: BleDeviceFingerprintData): Double {
        if (fp1.rssiHistory.size < 2 || fp2.rssiHistory.size < 2) return 0.0

        val var1 = calculateVariance(fp1.rssiHistory.map { it.toDouble() })
        val var2 = calculateVariance(fp2.rssiHistory.map { it.toDouble() })

        // Similar variance suggests similar radio behavior
        val ratio = if (var1 > 0 && var2 > 0) {
            minOf(var1, var2) / maxOf(var1, var2)
        } else if (var1 == 0.0 && var2 == 0.0) {
            1.0
        } else {
            0.0
        }

        return WEIGHT_RSSI * ratio
    }

    private fun calculateVariance(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }

    private fun mergeIntoCluster(mac1: String, mac2: String, confidence: Double): String {
        val cluster1 = macToCluster[mac1]
        val cluster2 = macToCluster[mac2]

        val clusterId: String

        when {
            cluster1 != null && cluster2 != null && cluster1 != cluster2 -> {
                // Merge cluster2 into cluster1
                val c2fp = deviceClusters.remove(cluster2)
                if (c2fp != null) {
                    deviceClusters[cluster1]?.mergeFrom(c2fp)
                    for (mac in c2fp.associatedMacs) {
                        macToCluster[mac] = cluster1
                    }
                }
                clusterId = cluster1
            }
            cluster1 != null -> {
                macToCluster[mac2] = cluster1
                deviceClusters[cluster1]?.associatedMacs?.add(mac2)
                macFingerprints[mac2]?.let { deviceClusters[cluster1]?.mergeFrom(it) }
                clusterId = cluster1
            }
            cluster2 != null -> {
                macToCluster[mac1] = cluster2
                deviceClusters[cluster2]?.associatedMacs?.add(mac1)
                macFingerprints[mac1]?.let { deviceClusters[cluster2]?.mergeFrom(it) }
                clusterId = cluster2
            }
            else -> {
                clusterId = "device_${deviceClusters.size + 1}_${System.currentTimeMillis()}"
                val merged = BleDeviceFingerprintData(
                    primaryId = clusterId,
                    associatedMacs = mutableSetOf(mac1, mac2),
                    firstSeen = minOf(
                        macFingerprints[mac1]?.firstSeen ?: Double.MAX_VALUE,
                        macFingerprints[mac2]?.firstSeen ?: Double.MAX_VALUE,
                    ),
                    clusterConfidence = confidence,
                )
                macFingerprints[mac1]?.let { merged.mergeFrom(it) }
                macFingerprints[mac2]?.let { merged.mergeFrom(it) }
                deviceClusters[clusterId] = merged
                macToCluster[mac1] = clusterId
                macToCluster[mac2] = clusterId
            }
        }

        deviceClusters[clusterId]?.clusterConfidence = maxOf(
            deviceClusters[clusterId]?.clusterConfidence ?: 0.0,
            confidence,
        )

        return clusterId
    }

    fun cleanupStaleData(maxAgeSeconds: Double = 3600.0, maxFingerprints: Int = 5000, now: Double = System.currentTimeMillis() / 1000.0) {
        val stale = macFingerprints.filter { now - it.value.lastSeen > maxAgeSeconds }.keys.toList()
        for (mac in stale) {
            macFingerprints.remove(mac)
            macToCluster.remove(mac)
        }

        if (macFingerprints.size > maxFingerprints) {
            val sorted = macFingerprints.entries.sortedBy { it.value.lastSeen }
            val excess = sorted.take(sorted.size - maxFingerprints)
            for ((mac, _) in excess) {
                macFingerprints.remove(mac)
                macToCluster.remove(mac)
            }
        }

        // Clean orphaned clusters
        val activeClusters = macToCluster.values.toSet()
        val orphaned = deviceClusters.keys.filter { it !in activeClusters }
        orphaned.forEach { deviceClusters.remove(it) }
    }

    fun getClusterStats(): Map<String, Any> = mapOf(
        "total_macs" to macFingerprints.size,
        "total_clusters" to deviceClusters.size,
        "macs_in_clusters" to macToCluster.size,
        "unclustered_macs" to (macFingerprints.size - macToCluster.size),
    )
}
