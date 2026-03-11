package com.ngcyt.ble.domain.similarity

import com.ngcyt.ble.domain.fingerprint.isUbiquitousUuid
import kotlin.math.ln
import kotlin.math.min

class BehaviorAnalyzer {
    companion object {
        const val MAX_AD_FREQUENCY = 10.0   // 10 ads/min is high
        const val MAX_SERVICE_COUNT = 20.0
        const val MAX_DURATION_MINS = 60.0
    }

    private val adHistory = mutableMapOf<String, MutableList<Double>>()
    private val serviceHistory = mutableMapOf<String, MutableMap<String, Int>>()
    private val firstSeen = mutableMapOf<String, Double>()
    private val lastSeen = mutableMapOf<String, Double>()

    fun recordAdvertisement(deviceId: String, timestamp: Double, serviceUuid: String? = null) {
        val history = adHistory.getOrPut(deviceId) { mutableListOf() }
        history.add(timestamp)
        if (history.size > 100) history.removeAt(0)

        if (serviceUuid != null) {
            val services = serviceHistory.getOrPut(deviceId) { mutableMapOf() }
            services[serviceUuid] = (services[serviceUuid] ?: 0) + 1
        }

        if (deviceId !in firstSeen) firstSeen[deviceId] = timestamp
        lastSeen[deviceId] = timestamp
    }

    fun createBehaviorVector(deviceId: String, usesRandomization: Boolean = false): BehaviorVector {
        val ads = adHistory[deviceId] ?: emptyList()
        val services = serviceHistory[deviceId] ?: emptyMap()

        var vector = BehaviorVector(
            deviceId = deviceId,
            timestamp = System.currentTimeMillis() / 1000.0,
            rawAdCount = ads.size,
            rawServiceList = services.keys.toList(),
            usesRandomization = if (usesRandomization) 1f else 0f,
        )

        if (ads.size < 2) return vector

        // Ad frequency
        val durationMins = (ads.max() - ads.min()) / 60.0
        val adFrequency = if (durationMins > 0) {
            min(ads.size / durationMins / MAX_AD_FREQUENCY, 1.0).toFloat()
        } else 0f

        // Ad regularity
        val intervals = (0 until ads.size - 1).map { ads[it + 1] - ads[it] }
        var adRegularity = 0f
        if (intervals.size > 1) {
            val mean = intervals.average()
            val std = kotlin.math.sqrt(intervals.map { (it - mean) * (it - mean) }.average())
            if (mean > 0) {
                val cv = std / mean
                adRegularity = maxOf(0f, (1.0 - minOf(cv, 2.0) / 2.0).toFloat())
            }
        }

        // Burst tendency
        val burstTendency = if (intervals.isNotEmpty()) {
            intervals.count { it < 1.0 }.toFloat() / intervals.size
        } else 0f

        // Service features
        var serviceCount = 0f
        var uniqueServiceRatio = 0f
        var serviceEntropy = 0f

        if (services.isNotEmpty()) {
            val total = services.size
            val unique = services.keys.count { !isUbiquitousUuid(it) }

            serviceCount = min(total.toDouble() / MAX_SERVICE_COUNT, 1.0).toFloat()
            uniqueServiceRatio = if (total > 0) unique.toFloat() / total else 0f

            val totalAds = services.values.sum()
            if (totalAds > 0) {
                var entropy = 0.0
                for (count in services.values) {
                    val p = count.toDouble() / totalAds
                    if (p > 0) entropy -= p * ln(p)
                }
                serviceEntropy = min(entropy / 3.0, 1.0).toFloat()
            }
        }

        // Duration
        val first = firstSeen[deviceId] ?: 0.0
        val last = lastSeen[deviceId] ?: 0.0
        val activeDuration = if (first > 0 && last > 0) {
            min((last - first) / 60.0 / MAX_DURATION_MINS, 1.0).toFloat()
        } else 0f

        // Minimal advertisement (no services, no name = evasive)
        val minimalAd = if (services.isEmpty()) 1f else 0f

        return vector.copy(
            adFrequency = adFrequency,
            adRegularity = adRegularity,
            burstTendency = burstTendency,
            serviceCount = serviceCount,
            uniqueServiceRatio = uniqueServiceRatio,
            serviceEntropy = serviceEntropy,
            activeDuration = activeDuration,
            minimalAdvertisement = minimalAd,
        )
    }
}
