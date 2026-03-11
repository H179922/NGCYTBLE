package com.ngcyt.ble.domain.fingerprint

import kotlin.math.abs
import kotlin.math.sqrt

class TimingProfile {
    private val timestamps = mutableListOf<Double>()
    var meanInterval: Double = 0.0; private set
    var stdInterval: Double = 0.0; private set
    var burstSize: Int = 0; private set
    var burstInterval: Double = 0.0; private set

    fun addTimestamp(timestamp: Double) {
        timestamps.add(timestamp)
        if (timestamps.size > 100) {
            timestamps.removeAt(0)
        }
        recalculate()
    }

    private fun recalculate() {
        if (timestamps.size < 3) return
        val sorted = timestamps.sorted()
        val intervals = (0 until sorted.size - 1).map { sorted[it + 1] - sorted[it] }
        if (intervals.isEmpty()) return

        val burstIntervals = intervals.filter { it < 0.5 }
        val mainIntervals = intervals.filter { it >= 0.5 }

        if (mainIntervals.isNotEmpty()) {
            meanInterval = mainIntervals.average()
            stdInterval = if (mainIntervals.size > 1) {
                val mean = mainIntervals.average()
                sqrt(mainIntervals.map { (it - mean) * (it - mean) }.average())
            } else 0.0
        }

        if (burstIntervals.isNotEmpty()) {
            burstInterval = burstIntervals.average()
            var burstCount = 0
            var current = 1
            for (interval in intervals) {
                if (interval < 0.5) {
                    current++
                } else {
                    if (current > 1) burstCount++
                    current = 1
                }
            }
            burstSize = if (burstCount == 0) current
                else (burstIntervals.size / maxOf(burstCount, 1)) + 1
        }
    }

    fun similarity(other: TimingProfile): Double {
        if (meanInterval == 0.0 || other.meanInterval == 0.0) return 0.0

        var score = 0.0

        // Mean interval similarity (allow 20% variance)
        val ratio = minOf(meanInterval, other.meanInterval) / maxOf(meanInterval, other.meanInterval)
        score += when {
            ratio > 0.8 -> 0.5
            ratio > 0.6 -> 0.25
            else -> 0.0
        }

        // Burst characteristics
        if (burstSize > 0 && other.burstSize > 0) {
            score += when {
                burstSize == other.burstSize -> 0.3
                abs(burstSize - other.burstSize) == 1 -> 0.15
                else -> 0.0
            }
        }

        // Burst interval similarity
        if (burstInterval > 0 && other.burstInterval > 0) {
            val burstRatio = minOf(burstInterval, other.burstInterval) / maxOf(burstInterval, other.burstInterval)
            if (burstRatio > 0.8) score += 0.2
        }

        return minOf(score, 1.0)
    }
}
