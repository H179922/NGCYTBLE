package com.ngcyt.ble.domain.fingerprint

/** BLE Service UUIDs that are too common to be useful for fingerprinting. */
val UBIQUITOUS_SERVICE_UUIDS = setOf(
    "00001800-0000-1000-8000-00805f9b34fb", // Generic Access
    "00001801-0000-1000-8000-00805f9b34fb", // Generic Attribute
    "0000fe8f-0000-1000-8000-00805f9b34fb", // Google Nearby
    "0000fea0-0000-1000-8000-00805f9b34fb", // Google
    "0000fd6f-0000-1000-8000-00805f9b34fb", // Exposure Notification (COVID)
)

fun isUbiquitousUuid(uuid: String): Boolean = uuid.lowercase() in UBIQUITOUS_SERVICE_UUIDS
