package com.ngcyt.ble.domain.detection

import com.ngcyt.ble.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DetectionEngineTest {

    private lateinit var engine: DetectionEngine

    @Before
    fun setup() {
        engine = DetectionEngine(ignoreMacs = setOf("IGNORED:MAC"))
    }

    @Test
    fun `single sighting gets base score`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 1000.0)
        sighting.timeBuckets.add("bucket_0")
        // FIRST_SIGHTING(5) + 1 bucket * BUCKET_PRESENCE(15) = 20
        assertEquals(20, engine.calculateThreatScore(sighting))
    }

    @Test
    fun `two buckets scores higher`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 1300.0)
        sighting.timeBuckets.addAll(listOf("bucket_0", "bucket_1"))
        // FIRST_SIGHTING(5) + 2 * BUCKET_PRESENCE(15) = 35
        assertEquals(35, engine.calculateThreatScore(sighting))
    }

    @Test
    fun `four buckets hits bucket cap`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 2200.0)
        sighting.timeBuckets.addAll(listOf("bucket_0", "bucket_1", "bucket_2", "bucket_3"))
        // FIRST_SIGHTING(5) + 4 * 15 = 65 -> capped at 60 for buckets = 5 + 60 = 65
        // plus duration: (2200-1000)/60 = 20 min -> +20
        assertEquals(85, engine.calculateThreatScore(sighting))
    }

    @Test
    fun `duration 5 min adds bonus`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 0.0, 300.0) // 5 min
        sighting.timeBuckets.add("bucket_0")
        // FIRST_SIGHTING(5) + 1 * BUCKET(15) + DURATION_5MIN(5) = 25
        assertEquals(25, engine.calculateThreatScore(sighting))
    }

    @Test
    fun `duration 20 min adds full bonus`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 0.0, 1200.0) // 20 min
        sighting.timeBuckets.addAll(listOf("bucket_0", "bucket_1"))
        // FIRST_SIGHTING(5) + 2 * BUCKET(15) + DURATION_20MIN(20) = 55
        assertEquals(55, engine.calculateThreatScore(sighting))
    }

    @Test
    fun `service uuid match adds 10 points`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 1000.0)
        sighting.timeBuckets.add("bucket_0")
        sighting.serviceUuids.add("0000180d-0000-1000-8000-00805f9b34fb")
        // FIRST_SIGHTING(5) + BUCKET(15) + UUID(10) = 30
        assertEquals(30, engine.calculateThreatScore(sighting))
    }

    @Test
    fun `mac correlation adds 15 points`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 1000.0)
        sighting.timeBuckets.add("bucket_0")
        // FIRST_SIGHTING(5) + BUCKET(15) + CORRELATION(15) = 35
        assertEquals(35, engine.calculateThreatScore(sighting, isCorrelated = true))
    }

    @Test
    fun `sighting count bonus applies at 2 and 5`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 1000.0, sightingCount = 2)
        sighting.timeBuckets.add("bucket_0")
        // FIRST_SIGHTING(5) + BUCKET(15) + SIGHTING_LOW(5) = 25
        assertEquals(25, engine.calculateThreatScore(sighting))

        sighting.sightingCount = 5
        // FIRST_SIGHTING(5) + BUCKET(15) + SIGHTING_LOW(5) + SIGHTING_HIGH(5) = 30
        assertEquals(30, engine.calculateThreatScore(sighting))
    }

    @Test
    fun `stored isCorrelated is used by calculateThreatScore`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 1000.0, isCorrelated = true)
        sighting.timeBuckets.add("bucket_0")
        // FIRST_SIGHTING(5) + BUCKET(15) + CORRELATION(15) = 35
        assertEquals(35, engine.calculateThreatScore(sighting))
    }

    @Test
    fun `score capped at 100`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 0.0, 1200.0, sightingCount = 5)
        sighting.timeBuckets.addAll(listOf("bucket_0", "bucket_1", "bucket_2", "bucket_3", "bucket_4"))
        sighting.serviceUuids.add("some-uuid")
        // FIRST(5) + SIGHTING(10) + 5*15=75->capped60 + DURATION_20MIN(20) + UUID(10) + CORR(15) = 120 -> 100
        assertEquals(100, engine.calculateThreatScore(sighting, isCorrelated = true))
    }

    @Test
    fun `ignored mac returns null assessment`() {
        val result = engine.analyzeDevice("IGNORED:MAC", "BLE", 1000.0, "bucket_0")
        assertNull(result)
    }

    @Test
    fun `analyzeDevice always returns assessment for non-ignored mac`() {
        val result = engine.analyzeDevice("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, "bucket_0")
        assertNotNull(result)
        assertTrue(result!!.threatScore > 0)
    }

    @Test
    fun `reasoning describes first sighting`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 1000.0)
        sighting.timeBuckets.add("bucket_0")
        val reasoning = engine.generateReasoning(sighting, 20)
        assertTrue(reasoning.contains("First sighting"))
    }

    @Test
    fun `reasoning describes multiple time periods`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 0.0, 1200.0)
        sighting.timeBuckets.addAll(listOf("bucket_0", "bucket_1", "bucket_2"))
        val reasoning = engine.generateReasoning(sighting, 60)
        assertTrue(reasoning.contains("3 time periods"))
    }

    @Test
    fun `getAllThreats returns all devices by default`() {
        engine.analyzeDevice("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, "bucket_0")
        engine.analyzeDevice("11:22:33:44:55:66", "BLE", 1000.0, "bucket_0")
        val threats = engine.getAllThreats()
        assertEquals(2, threats.size)
    }

    @Test
    fun `getAllThreats respects minScore filter`() {
        engine.analyzeDevice("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, "bucket_0")
        // Single sighting scores 20, so filtering at 50 should exclude it
        val threats = engine.getAllThreats(minScore = 50)
        assertEquals(0, threats.size)
    }

    @Test
    fun `correlatedCluster parameter is used`() {
        val result = engine.analyzeDevice(
            "AA:BB:CC:DD:EE:FF", "BLE", 1000.0, "bucket_0",
            correlatedCluster = "cluster_1",
        )
        assertNotNull(result)
        assertTrue(result!!.isMacRandomized)
        assertEquals("cluster_1", result.physicalDeviceId)
    }
}
