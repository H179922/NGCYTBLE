package com.ngcyt.ble.domain.model

import org.junit.Assert.*
import org.junit.Test

class ThreatAssessmentTest {

    @Test
    fun `threat level from score - minimal`() {
        assertEquals(ThreatLevel.MINIMAL, ThreatLevel.fromScore(0))
        assertEquals(ThreatLevel.MINIMAL, ThreatLevel.fromScore(24))
    }

    @Test
    fun `threat level from score - low`() {
        assertEquals(ThreatLevel.LOW, ThreatLevel.fromScore(25))
        assertEquals(ThreatLevel.LOW, ThreatLevel.fromScore(49))
    }

    @Test
    fun `threat level from score - medium`() {
        assertEquals(ThreatLevel.MEDIUM, ThreatLevel.fromScore(50))
        assertEquals(ThreatLevel.MEDIUM, ThreatLevel.fromScore(74))
    }

    @Test
    fun `threat level from score - high`() {
        assertEquals(ThreatLevel.HIGH, ThreatLevel.fromScore(75))
        assertEquals(ThreatLevel.HIGH, ThreatLevel.fromScore(89))
    }

    @Test
    fun `threat level from score - critical`() {
        assertEquals(ThreatLevel.CRITICAL, ThreatLevel.fromScore(90))
        assertEquals(ThreatLevel.CRITICAL, ThreatLevel.fromScore(100))
    }

    @Test
    fun `device sighting add sighting updates fields`() {
        val sighting = DeviceSighting(
            mac = "AA:BB:CC:DD:EE:FF",
            deviceType = "BLE",
            firstSeen = 1000.0,
            lastSeen = 1000.0,
        )

        sighting.addSighting("5-10min", 2000.0, serviceUuid = "0000180d-0000-1000-8000-00805f9b34fb")

        assertEquals(setOf("5-10min"), sighting.timeBuckets)
        assertEquals(2000.0, sighting.lastSeen, 0.01)
        assertEquals(2, sighting.sightingCount)
        assertTrue(sighting.serviceUuids.contains("0000180d-0000-1000-8000-00805f9b34fb"))
    }

    @Test
    fun `threat assessment toMap includes fingerprint data when present`() {
        val assessment = ThreatAssessment(
            mac = "AA:BB:CC:DD:EE:FF",
            deviceType = "BLE",
            threatScore = 75,
            threatLevel = ThreatLevel.HIGH,
            timeBucketsPresent = listOf("current", "5-10min", "10-15min"),
            durationMinutes = 18.5,
            serviceUuids = listOf("Heart Rate"),
            reasoning = "Device seen in 3 time periods; Present for 18 minutes",
            physicalDeviceId = "device_1_12345",
            associatedMacs = listOf("11:22:33:44:55:66"),
            fingerprintConfidence = 72.5,
            isMacRandomized = true,
        )

        val map = assessment.toMap()
        assertEquals("AA:BB:CC:DD:EE:FF", map["mac"])
        assertEquals(75, map["threat_score"])
        assertEquals("high", map["threat_level"])
        assertEquals("device_1_12345", map["physical_device_id"])
        assertEquals(true, map["is_mac_randomized"])
    }
}
