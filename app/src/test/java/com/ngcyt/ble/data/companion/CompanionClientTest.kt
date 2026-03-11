package com.ngcyt.ble.data.companion

import org.junit.Assert.*
import org.junit.Test

class CompanionClientTest {

    @Test
    fun `initial status is disconnected`() {
        val client = CompanionClient()
        assertEquals(CompanionStatus.DISCONNECTED, client.status.value)
        client.close()
    }

    @Test
    fun `configure sets url`() {
        val client = CompanionClient()
        client.configure("http://192.168.1.100:5000")
        // No crash, config stored
        client.close()
    }

    @Test
    fun `wifi threats initially empty`() {
        val client = CompanionClient()
        assertTrue(client.wifiThreats.value.isEmpty())
        client.close()
    }
}
