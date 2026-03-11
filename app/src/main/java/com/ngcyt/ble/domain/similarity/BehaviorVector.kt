package com.ngcyt.ble.domain.similarity

import kotlin.math.sqrt

data class BehaviorVector(
    val deviceId: String,
    val adFrequency: Float = 0f,
    val adRegularity: Float = 0f,
    val burstTendency: Float = 0f,
    val serviceCount: Float = 0f,
    val uniqueServiceRatio: Float = 0f,
    val serviceEntropy: Float = 0f,
    val activeDuration: Float = 0f,
    val timeConsistency: Float = 0f,
    val usesRandomization: Float = 0f,
    val minimalAdvertisement: Float = 0f,
    val highMobility: Float = 0f,
    val rawAdCount: Int = 0,
    val rawServiceList: List<String> = emptyList(),
    val timestamp: Double = 0.0,
) {
    fun toFloatArray(): FloatArray = floatArrayOf(
        adFrequency, adRegularity, burstTendency,
        serviceCount, uniqueServiceRatio, serviceEntropy,
        activeDuration, timeConsistency,
        usesRandomization, minimalAdvertisement, highMobility,
    )

    companion object {
        const val DIMENSIONS = 11
    }
}

fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) { "Vectors must have same dimension" }
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    val denom = sqrt(normA) * sqrt(normB)
    return if (denom == 0f) 0f else dot / denom
}
