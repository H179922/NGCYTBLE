package com.ngcyt.ble.domain.fingerprint

import org.junit.Assert.*
import org.junit.Test

class TimingProfileTest {

    @Test
    fun `similarity of identical profiles is high`() {
        val p1 = TimingProfile()
        val p2 = TimingProfile()
        // Simulate identical timing: probes at 0, 1.0, 2.0, 3.0 seconds
        listOf(0.0, 1.0, 2.0, 3.0).forEach { p1.addTimestamp(it); p2.addTimestamp(it) }
        assertTrue(p1.similarity(p2) > 0.7)
    }

    @Test
    fun `similarity of different profiles is low`() {
        val p1 = TimingProfile()
        val p2 = TimingProfile()
        listOf(0.0, 1.0, 2.0, 3.0).forEach { p1.addTimestamp(it) }
        listOf(0.0, 5.0, 15.0, 45.0).forEach { p2.addTimestamp(it) }
        assertTrue(p1.similarity(p2) < 0.3)
    }

    @Test
    fun `burst detection identifies short intervals`() {
        val p = TimingProfile()
        // Burst: 3 probes in 200ms, then 5s gap, then 3 more
        listOf(0.0, 0.1, 0.2, 5.0, 5.1, 5.2).forEach { p.addTimestamp(it) }
        assertTrue(p.burstSize > 1)
    }
}
