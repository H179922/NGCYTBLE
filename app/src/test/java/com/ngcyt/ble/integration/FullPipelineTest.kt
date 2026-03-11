package com.ngcyt.ble.integration

import com.ngcyt.ble.domain.detection.DetectionEngine
import com.ngcyt.ble.domain.fingerprint.BleFingerprintEngineImpl
import com.ngcyt.ble.domain.model.ThreatLevel
import com.ngcyt.ble.domain.similarity.BehaviorSimilarityEngineImpl
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Full pipeline integration test: exercises DetectionEngine, BleFingerprintEngineImpl,
 * and BehaviorSimilarityEngineImpl together without any Android dependencies.
 */
class FullPipelineTest {

    private lateinit var fingerprintEngine: BleFingerprintEngineImpl
    private lateinit var similarityEngine: BehaviorSimilarityEngineImpl
    private lateinit var engine: DetectionEngine

    companion object {
        const val MAC_A = "AA:BB:CC:DD:00:01"
        const val MAC_B = "AA:BB:CC:DD:00:02"

        // Non-ubiquitous service UUIDs for fingerprinting
        const val SERVICE_UUID_1 = "0000abcd-0000-1000-8000-00805f9b34fb"
        const val SERVICE_UUID_2 = "0000abce-0000-1000-8000-00805f9b34fb"
        const val SERVICE_UUID_3 = "0000abcf-0000-1000-8000-00805f9b34fb"

        val MANUFACTURER_DATA = mapOf(0x004C to byteArrayOf(0x02, 0x15, 0x01, 0x02))

        val TIME_BUCKETS = listOf("current", "0-5min", "5-10min", "10-15min", "15-20min")
    }

    @Before
    fun setup() {
        fingerprintEngine = BleFingerprintEngineImpl()
        similarityEngine = BehaviorSimilarityEngineImpl()
        engine = DetectionEngine(
            ignoreMacs = emptySet(),
            fingerprintEngine = fingerprintEngine,
            similarityEngine = similarityEngine,
        )
    }

    // ---------------------------------------------------------------
    // 1) Simulate 5 BLE advertisements from MAC-A across time windows
    // 2) Simulate 3 BLE advertisements from MAC-B with similar UUIDs
    // ---------------------------------------------------------------

    private fun simulateMacAAdvertisements() {
        val baseTimestamp = 1_000_000.0
        TIME_BUCKETS.forEachIndexed { index, bucket ->
            val ts = baseTimestamp + index * 300.0 // 5-min increments
            // Feed fingerprint engine directly (the full overload, not the interface one)
            fingerprintEngine.processAdvertisement(
                mac = MAC_A,
                serviceUuids = setOf(SERVICE_UUID_1, SERVICE_UUID_2, SERVICE_UUID_3),
                manufacturerData = MANUFACTURER_DATA,
                txPower = -59,
                rssi = -70 + index,
                timestamp = ts,
            )
            // Feed detection engine
            engine.analyzeDevice(
                mac = MAC_A,
                deviceType = "BLE",
                timestamp = ts,
                timeBucket = bucket,
                serviceUuid = SERVICE_UUID_1,
            )
        }
    }

    private fun simulateMacBAdvertisements() {
        val baseTimestamp = 1_000_010.0
        val bucketsForB = listOf("current", "0-5min", "5-10min")
        bucketsForB.forEachIndexed { index, bucket ->
            val ts = baseTimestamp + index * 300.0
            // Same service UUIDs as MAC-A to create fingerprint similarity
            fingerprintEngine.processAdvertisement(
                mac = MAC_B,
                serviceUuids = setOf(SERVICE_UUID_1, SERVICE_UUID_2, SERVICE_UUID_3),
                manufacturerData = MANUFACTURER_DATA,
                txPower = -59,
                rssi = -70 + index,
                timestamp = ts,
            )
            engine.analyzeDevice(
                mac = MAC_B,
                deviceType = "BLE",
                timestamp = ts,
                timeBucket = bucket,
                serviceUuid = SERVICE_UUID_1,
            )
        }
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    fun `MAC-A across all time buckets has HIGH or CRITICAL threat`() {
        simulateMacAAdvertisements()

        val threats = engine.getAllThreats(minScore = 0)
        val macAThreat = threats.find { it.mac == MAC_A }
        assertNotNull("MAC-A should have a threat assessment", macAThreat)

        // 5 buckets -> (5-1)*20 = 80, capped at 60. Plus duration and UUID bonuses.
        // Score should be >= 70, qualifying as HIGH or CRITICAL.
        assertTrue(
            "MAC-A threat level should be HIGH or CRITICAL but was ${macAThreat!!.threatLevel}",
            macAThreat.threatLevel == ThreatLevel.HIGH || macAThreat.threatLevel == ThreatLevel.CRITICAL,
        )
        assertTrue(
            "MAC-A should be present in multiple time buckets",
            macAThreat.timeBucketsPresent.size >= 4,
        )
    }

    @Test
    fun `similarity engine finds MAC-A and MAC-B as behaviorally similar`() {
        simulateMacAAdvertisements()
        simulateMacBAdvertisements()

        // Record behavior through the similarity engine directly so vectors get stored
        val baseTs = 1_000_000.0
        for (i in 0 until 5) {
            similarityEngine.recordDeviceBehavior(MAC_A, baseTs + i * 300.0, SERVICE_UUID_1, false)
        }
        for (i in 0 until 3) {
            similarityEngine.recordDeviceBehavior(MAC_B, baseTs + 10 + i * 300.0, SERVICE_UUID_1, false)
        }

        // Build vectors so findSimilarDevices can compare them
        similarityEngine.updateDeviceVector(MAC_A)
        similarityEngine.updateDeviceVector(MAC_B)

        val similar = similarityEngine.findSimilarDevices(MAC_A, nResults = 5, minSimilarity = 0.5f)
        val macBResult = similar.find { it["device_id"] == MAC_B }
        assertNotNull(
            "MAC-B should appear as behaviorally similar to MAC-A",
            macBResult,
        )
        val similarity = macBResult!!["similarity"] as Float
        assertTrue("Similarity should be >= 0.5, was $similarity", similarity >= 0.5f)
    }

    @Test
    fun `ThreatAssessment toMap produces valid map with expected keys`() {
        simulateMacAAdvertisements()

        val threats = engine.getAllThreats(minScore = 0)
        assertTrue("Should have at least one threat", threats.isNotEmpty())

        val map = threats.first().toMap()

        // Required keys for external API serialization
        val requiredKeys = listOf(
            "mac", "device_type", "threat_score", "threat_level",
            "time_buckets", "duration_minutes", "service_uuids",
            "reasoning", "source",
        )
        for (key in requiredKeys) {
            assertTrue("toMap() must contain key '$key'", map.containsKey(key))
        }

        // Verify types
        assertTrue("threat_score should be Int", map["threat_score"] is Int)
        assertTrue("threat_level should be String", map["threat_level"] is String)
        assertTrue("time_buckets should be List", map["time_buckets"] is List<*>)
        assertTrue("service_uuids should be List", map["service_uuids"] is List<*>)
    }

    @Test
    fun `getAllThreats returns threats sorted by score descending`() {
        // MAC-A in 5 buckets (high score)
        simulateMacAAdvertisements()
        // MAC-B in 3 buckets (lower score)
        simulateMacBAdvertisements()

        val threats = engine.getAllThreats(minScore = 0)
        assertTrue("Should have at least 2 threats", threats.size >= 2)

        for (i in 0 until threats.size - 1) {
            assertTrue(
                "Threats should be sorted descending: index $i (${threats[i].threatScore}) >= index ${i + 1} (${threats[i + 1].threatScore})",
                threats[i].threatScore >= threats[i + 1].threatScore,
            )
        }
    }

    @Test
    fun `rotateTimeBuckets ages buckets correctly`() {
        // Put a device in "current" and "0-5min"
        engine.analyzeDevice(MAC_A, "BLE", 1000.0, "current", SERVICE_UUID_1)
        engine.analyzeDevice(MAC_A, "BLE", 1001.0, "0-5min", SERVICE_UUID_1)

        engine.rotateTimeBuckets()

        val sighting = engine.deviceHistory[MAC_A]
        assertNotNull("Device should still exist after rotation", sighting)
        // "current" -> "0-5min", "0-5min" -> "5-10min"
        assertTrue(
            "After rotation, 'current' should become '0-5min'",
            sighting!!.timeBuckets.contains("0-5min"),
        )
        assertTrue(
            "After rotation, '0-5min' should become '5-10min'",
            sighting.timeBuckets.contains("5-10min"),
        )
        assertFalse(
            "'current' should no longer be present",
            sighting.timeBuckets.contains("current"),
        )
    }

    @Test
    fun `rotateTimeBuckets removes device only in oldest bucket`() {
        engine.analyzeDevice("OLD:DEVICE", "BLE", 1000.0, "15-20min")
        engine.analyzeDevice(MAC_A, "BLE", 1000.0, "current")

        engine.rotateTimeBuckets()

        assertNull(
            "Device only in oldest bucket should be removed",
            engine.deviceHistory["OLD:DEVICE"],
        )
        assertNotNull(
            "Device in newer bucket should survive rotation",
            engine.deviceHistory[MAC_A],
        )
    }

    @Test
    fun `cleanupStaleDevices removes old entries`() {
        // Insert a device with a very old lastSeen
        val oldTimestamp = 1.0 // epoch second 1 — very old
        engine.analyzeDevice(MAC_A, "BLE", oldTimestamp, "current", SERVICE_UUID_1)

        // Ensure device is present
        assertNotNull(engine.deviceHistory[MAC_A])

        // Cleanup with a very short max age — since System.currentTimeMillis() is far
        // ahead of timestamp 1.0, the device should be stale
        engine.cleanupStaleDevices(maxAgeSeconds = 1.0, maxDevices = 10000)

        assertNull(
            "Stale device should have been removed",
            engine.deviceHistory[MAC_A],
        )
    }

    @Test
    fun `cleanupStaleDevices enforces maxDevices limit`() {
        // Insert more devices than the limit
        val now = System.currentTimeMillis() / 1000.0
        for (i in 0 until 5) {
            val mac = "AA:BB:CC:DD:FF:%02X".format(i)
            engine.analyzeDevice(mac, "BLE", now + i, "current")
        }
        assertEquals(5, engine.deviceHistory.size)

        // Cleanup with maxDevices = 2 — should keep only the 2 most recent
        engine.cleanupStaleDevices(maxAgeSeconds = 999999.0, maxDevices = 2)

        assertTrue(
            "Should have at most 2 devices after cleanup, had ${engine.deviceHistory.size}",
            engine.deviceHistory.size <= 2,
        )
    }

    @Test
    fun `full pipeline alert callback fires for high-scoring device`() {
        val alerts = mutableListOf<com.ngcyt.ble.domain.model.ThreatAssessment>()
        engine.registerAlertCallback { alerts.add(it) }

        simulateMacAAdvertisements()

        assertTrue(
            "Alert callback should have fired for high-scoring device",
            alerts.isNotEmpty(),
        )
        assertTrue(
            "Alert should have score >= 20",
            alerts.all { it.threatScore >= 20 },
        )
    }
}
