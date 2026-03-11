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
    fun `single bucket scores zero`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 1000.0)
        sighting.timeBuckets.add("current")
        assertEquals(0, engine.calculateThreatScore(sighting))
    }

    @Test
    fun `two buckets scores 20`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 1300.0)
        sighting.timeBuckets.addAll(listOf("current", "5-10min"))
        assertEquals(20, engine.calculateThreatScore(sighting))
    }

    @Test
    fun `four buckets scores 60`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 2200.0)
        sighting.timeBuckets.addAll(listOf("current", "5-10min", "10-15min", "15-20min"))
        assertEquals(60, engine.calculateThreatScore(sighting))
    }

    @Test
    fun `duration 15 min adds 10 points`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 0.0, 900.0) // 15 min
        sighting.timeBuckets.addAll(listOf("current", "5-10min"))
        assertEquals(30, engine.calculateThreatScore(sighting)) // 20 + 10
    }

    @Test
    fun `duration 20 min adds 20 points`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 0.0, 1200.0) // 20 min
        sighting.timeBuckets.addAll(listOf("current", "5-10min"))
        assertEquals(40, engine.calculateThreatScore(sighting)) // 20 + 20
    }

    @Test
    fun `service uuid match adds 10 points`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 1300.0)
        sighting.timeBuckets.addAll(listOf("current", "5-10min"))
        sighting.serviceUuids.add("0000180d-0000-1000-8000-00805f9b34fb")
        assertEquals(30, engine.calculateThreatScore(sighting)) // 20 + 10
    }

    @Test
    fun `mac correlation adds 15 points`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, 1300.0)
        sighting.timeBuckets.addAll(listOf("current", "5-10min"))
        assertEquals(35, engine.calculateThreatScore(sighting, isCorrelated = true)) // 20 + 15
    }

    @Test
    fun `score capped at 100`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 0.0, 1200.0) // 20 min
        sighting.timeBuckets.addAll(listOf("current", "0-5min", "5-10min", "10-15min", "15-20min"))
        sighting.serviceUuids.add("some-uuid")
        // 80 (buckets, capped 60) + 20 (duration) + 10 (uuid) + 15 (correlated) = 105, capped 100
        assertEquals(100, engine.calculateThreatScore(sighting, isCorrelated = true))
    }

    @Test
    fun `ignored mac returns null assessment`() {
        val result = engine.analyzeDevice("IGNORED:MAC", "BLE", 1000.0, "current")
        assertNull(result)
    }

    @Test
    fun `reasoning describes multiple time periods`() {
        val sighting = DeviceSighting("AA:BB:CC:DD:EE:FF", "BLE", 0.0, 1200.0)
        sighting.timeBuckets.addAll(listOf("current", "5-10min", "10-15min"))
        val reasoning = engine.generateReasoning(sighting, 60)
        assertTrue(reasoning.contains("3 time periods"))
        assertTrue(reasoning.contains("20 minutes"))
    }

    @Test
    fun `rotate time buckets ages labels`() {
        engine.analyzeDevice("AA:BB:CC:DD:EE:FF", "BLE", 1000.0, "current")
        engine.rotateTimeBuckets()

        val sighting = engine.deviceHistory["AA:BB:CC:DD:EE:FF"]
        assertNotNull(sighting)
        assertTrue(sighting!!.timeBuckets.contains("0-5min"))
        assertFalse(sighting.timeBuckets.contains("current"))
    }

    @Test
    fun `rotate removes devices only in oldest bucket`() {
        engine.analyzeDevice("OLD:DEVICE", "BLE", 1000.0, "15-20min")
        engine.rotateTimeBuckets()
        assertNull(engine.deviceHistory["OLD:DEVICE"])
    }
}
