package com.ngcyt.ble.data.api

import com.ngcyt.ble.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ExternalApiClientTest {

    @Test
    fun `alerts mode queues nothing for telemetry`() {
        val client = ExternalApiClient("http://test.local/api", "key123", ApiMode.ALERTS_ONLY)
        client.recordTelemetry(mapOf("test" to "data"))
        assertEquals(0, client.getPendingCount())
        client.close()
    }

    @Test
    fun `telemetry mode queues events`() {
        val client = ExternalApiClient("http://test.local/api", "key123", ApiMode.FULL_TELEMETRY)
        client.recordTelemetry(mapOf("test" to "data1"))
        client.recordTelemetry(mapOf("test" to "data2"))
        assertEquals(2, client.getPendingCount())
        client.close()
    }

    @Test
    fun `threat assessment toMap produces valid payload`() {
        val assessment = ThreatAssessment(
            mac = "AA:BB:CC:DD:EE:FF",
            deviceType = "BLE",
            threatScore = 75,
            threatLevel = ThreatLevel.HIGH,
            timeBucketsPresent = listOf("current", "5-10min"),
            durationMinutes = 12.5,
            serviceUuids = listOf("Heart Rate"),
            reasoning = "Test",
        )
        val map = assessment.toMap()
        assertNotNull(map["mac"])
        assertNotNull(map["threat_score"])
        assertEquals("high", map["threat_level"])
    }
}
