package com.ngcyt.ble.domain.fingerprint

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BleFingerprintEngineTest {

    private lateinit var engine: BleFingerprintEngineImpl

    @Before
    fun setup() {
        engine = BleFingerprintEngineImpl()
    }

    @Test
    fun `new mac creates fingerprint`() {
        engine.processAdvertisement(
            mac = "AA:BB:CC:DD:EE:FF",
            serviceUuids = setOf("0000180d-0000-1000-8000-00805f9b34fb"),
            manufacturerData = mapOf(76 to byteArrayOf(0x01, 0x02)),
            txPower = -59,
            rssi = -45,
            timestamp = 1000.0,
        )
        val fp = engine.getFingerprint("AA:BB:CC:DD:EE:FF")
        assertNotNull(fp)
        assertEquals(1, fp!!.serviceUuids.size)
    }

    @Test
    fun `same service uuids correlate two macs`() {
        val sharedUuids = setOf(
            "0000180d-0000-1000-8000-00805f9b34fb", // Heart Rate
            "0000180f-0000-1000-8000-00805f9b34fb", // Battery
            "custom-uuid-12345",                      // Custom service
        )

        // First MAC
        engine.processAdvertisement(
            mac = "AA:BB:CC:DD:EE:01",
            serviceUuids = sharedUuids,
            manufacturerData = mapOf(76 to byteArrayOf(0x01, 0x02, 0x03)),
            txPower = -59,
            rssi = -45,
            timestamp = 1000.0,
        )

        // Second MAC with same services + manufacturer data
        val cluster = engine.processAdvertisement(
            mac = "AA:BB:CC:DD:EE:02",
            serviceUuids = sharedUuids,
            manufacturerData = mapOf(76 to byteArrayOf(0x01, 0x02, 0x03)),
            txPower = -59,
            rssi = -43,
            timestamp = 1005.0,
        )

        assertNotNull(cluster)
    }

    @Test
    fun `completely different devices do not correlate`() {
        engine.processAdvertisement(
            mac = "AA:BB:CC:DD:EE:01",
            serviceUuids = setOf("0000180d-0000-1000-8000-00805f9b34fb"),
            manufacturerData = mapOf(76 to byteArrayOf(0x01)),
            txPower = -59,
            rssi = -45,
            timestamp = 1000.0,
        )

        val cluster = engine.processAdvertisement(
            mac = "AA:BB:CC:DD:EE:02",
            serviceUuids = setOf("0000fff0-0000-1000-8000-00805f9b34fb"),
            manufacturerData = mapOf(6 to byteArrayOf(0x99.toByte())),
            txPower = -30,
            rssi = -80,
            timestamp = 1005.0,
        )

        assertNull(cluster)
    }

    @Test
    fun `get all macs for clustered device`() {
        val sharedUuids = setOf("custom-a", "custom-b", "custom-c")
        val sharedMfr = mapOf(76 to byteArrayOf(0x01, 0x02, 0x03, 0x04))

        engine.processAdvertisement("MAC:01", sharedUuids, sharedMfr, -59, -45, 1000.0)
        engine.processAdvertisement("MAC:02", sharedUuids, sharedMfr, -59, -43, 1005.0)

        val allMacs = engine.getAllMacsForDevice("MAC:01")
        assertTrue(allMacs.contains("MAC:01"))
        assertTrue(allMacs.contains("MAC:02"))
    }

    @Test
    fun `ubiquitous uuids filtered from scoring`() {
        // Generic Access and Generic Attribute are ubiquitous — should not count
        engine.processAdvertisement(
            mac = "MAC:01",
            serviceUuids = setOf("00001800-0000-1000-8000-00805f9b34fb", "00001801-0000-1000-8000-00805f9b34fb"),
            manufacturerData = emptyMap(),
            txPower = -59,
            rssi = -45,
            timestamp = 1000.0,
        )

        engine.processAdvertisement(
            mac = "MAC:02",
            serviceUuids = setOf("00001800-0000-1000-8000-00805f9b34fb", "00001801-0000-1000-8000-00805f9b34fb"),
            manufacturerData = emptyMap(),
            txPower = -30,
            rssi = -80,
            timestamp = 1005.0,
        )

        // Should NOT correlate based on ubiquitous UUIDs alone
        val allMacs = engine.getAllMacsForDevice("MAC:01")
        assertEquals(1, allMacs.size) // Just itself
    }

    @Test
    fun `cleanup removes stale fingerprints`() {
        engine.processAdvertisement("MAC:01", emptySet(), emptyMap(), 0, -45, 1000.0)
        engine.processAdvertisement("MAC:02", emptySet(), emptyMap(), 0, -45, 9999.0)

        // Cleanup with 100s max age, "now" at 10000
        engine.cleanupStaleData(maxAgeSeconds = 100.0, now = 10000.0)

        assertNull(engine.getFingerprint("MAC:01"))
        assertNotNull(engine.getFingerprint("MAC:02"))
    }
}
