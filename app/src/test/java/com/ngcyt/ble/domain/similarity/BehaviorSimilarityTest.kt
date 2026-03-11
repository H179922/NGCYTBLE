package com.ngcyt.ble.domain.similarity

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BehaviorSimilarityTest {

    @Test
    fun `cosine similarity of identical vectors is 1`() {
        val a = floatArrayOf(0.5f, 0.8f, 0.3f, 0.1f, 0.9f, 0.4f, 0.7f, 0.2f, 1.0f, 0.0f, 0.5f)
        assertEquals(1.0f, cosineSimilarity(a, a), 0.001f)
    }

    @Test
    fun `cosine similarity of orthogonal vectors is 0`() {
        val a = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(0.0f, cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `behavior analyzer creates vector from advertisement history`() {
        val analyzer = BehaviorAnalyzer()
        // Simulate 10 advertisements over 5 minutes
        for (i in 0 until 10) {
            analyzer.recordAdvertisement("device1", 1000.0 + i * 30.0, "service-a")
        }
        val vector = analyzer.createBehaviorVector("device1")
        assertTrue(vector.adFrequency > 0)
        assertTrue(vector.serviceCount > 0)
        assertEquals(10, vector.rawAdCount)
    }

    @Test
    fun `suspicious pattern vector has expected characteristics`() {
        val engine = BehaviorSimilarityEngineImpl()
        val suspicious = engine.createSuspiciousPatternVector()
        assertTrue(suspicious.adFrequency > 0.5f)
        assertTrue(suspicious.usesRandomization > 0.5f)
        assertTrue(suspicious.activeDuration > 0.5f)
    }

    @Test
    fun `similar devices found by cosine similarity`() {
        val engine = BehaviorSimilarityEngineImpl()

        // Device A: regular, high frequency
        for (i in 0 until 20) {
            engine.recordDeviceBehavior("deviceA", 1000.0 + i * 30.0, "svc-1")
        }
        engine.updateDeviceVector("deviceA")

        // Device B: similar pattern
        for (i in 0 until 20) {
            engine.recordDeviceBehavior("deviceB", 2000.0 + i * 30.0, "svc-1")
        }
        engine.updateDeviceVector("deviceB")

        // Device C: very different pattern
        for (i in 0 until 3) {
            engine.recordDeviceBehavior("deviceC", 5000.0 + i * 300.0, "svc-x")
        }
        engine.updateDeviceVector("deviceC")

        val similar = engine.findSimilarDevices("deviceA", nResults = 5, minSimilarity = 0.5f)
        assertTrue(similar.any { it["device_id"] == "deviceB" })
    }
}
