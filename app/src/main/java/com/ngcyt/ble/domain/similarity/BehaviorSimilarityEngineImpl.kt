package com.ngcyt.ble.domain.similarity

import com.ngcyt.ble.domain.detection.BehaviorSimilarityEngine

class BehaviorSimilarityEngineImpl : BehaviorSimilarityEngine {

    private val analyzer = BehaviorAnalyzer()
    private val storedVectors = mutableMapOf<String, FloatArray>()
    private val storedMetadata = mutableMapOf<String, Map<String, Any>>()

    override fun recordDeviceBehavior(deviceId: String, timestamp: Double, serviceUuid: String?, usesRandomization: Boolean) {
        analyzer.recordAdvertisement(deviceId, timestamp, serviceUuid)
    }

    fun updateDeviceVector(deviceId: String, usesRandomization: Boolean = false): BehaviorVector {
        val vector = analyzer.createBehaviorVector(deviceId, usesRandomization)
        storedVectors[deviceId] = vector.toFloatArray()
        storedMetadata[deviceId] = mapOf(
            "device_id" to deviceId,
            "ad_count" to vector.rawAdCount,
            "service_list" to vector.rawServiceList.take(10).joinToString(","),
            "uses_randomization" to usesRandomization,
        )
        return vector
    }

    fun findSimilarDevices(deviceId: String, nResults: Int = 5, minSimilarity: Float = 0.7f): List<Map<String, Any>> {
        val targetVector = analyzer.createBehaviorVector(deviceId)
        if (targetVector.rawAdCount < 2) return emptyList()
        val target = targetVector.toFloatArray()

        return storedVectors
            .filter { it.key != deviceId }
            .map { (id, vec) ->
                val similarity = cosineSimilarity(target, vec)
                Triple(id, similarity, storedMetadata[id] ?: emptyMap())
            }
            .filter { it.second >= minSimilarity }
            .sortedByDescending { it.second }
            .take(nResults)
            .map { (id, similarity, metadata) ->
                mapOf(
                    "device_id" to id,
                    "similarity" to similarity,
                    "ad_count" to (metadata["ad_count"] ?: 0),
                    "uses_randomization" to (metadata["uses_randomization"] ?: false),
                )
            }
    }

    fun createSuspiciousPatternVector(): BehaviorVector = BehaviorVector(
        deviceId = "__suspicious_pattern__",
        adFrequency = 0.8f,
        adRegularity = 0.7f,
        burstTendency = 0.3f,
        serviceCount = 0.7f,
        uniqueServiceRatio = 0.8f,
        serviceEntropy = 0.6f,
        activeDuration = 0.8f,
        timeConsistency = 0.7f,
        usesRandomization = 1.0f,
        minimalAdvertisement = 0.5f,
        highMobility = 0.3f,
    )

    fun findSuspiciousDevices(nResults: Int = 10): List<Map<String, Any>> {
        val suspicious = createSuspiciousPatternVector().toFloatArray()
        return storedVectors
            .map { (id, vec) ->
                val similarity = cosineSimilarity(suspicious, vec)
                Triple(id, similarity, storedMetadata[id] ?: emptyMap())
            }
            .sortedByDescending { it.second }
            .take(nResults)
            .map { (id, similarity, metadata) ->
                mapOf(
                    "device_id" to id,
                    "similarity" to similarity,
                    "uses_randomization" to (metadata["uses_randomization"] ?: false),
                )
            }
    }

    fun getBehaviorClusters(): Map<String, List<String>> {
        val clusters = mutableMapOf<String, MutableList<String>>()
        for ((id, vec) in storedVectors) {
            val label = when {
                vec[8] > 0.5f -> "randomizing"
                vec[0] > 0.7f -> "high_frequency"
                vec[4] > 0.7f -> "unique_services"
                vec[6] > 0.7f -> "persistent"
                else -> "normal"
            }
            clusters.getOrPut(label) { mutableListOf() }.add(id)
        }
        return clusters
    }

    fun clear() {
        storedVectors.clear()
        storedMetadata.clear()
    }
}
