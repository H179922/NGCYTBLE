package com.ngcyt.ble.domain.model

enum class ThreatLevel(val value: String) {
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical");

    companion object {
        fun fromScore(score: Int): ThreatLevel = when {
            score >= 90 -> CRITICAL
            score >= 75 -> HIGH
            score >= 50 -> MEDIUM
            score >= 25 -> LOW
            else -> MINIMAL
        }
    }
}
