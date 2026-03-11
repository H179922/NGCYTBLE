package com.ngcyt.ble.domain.model

/**
 * Resolves human-readable labels for BLE devices using advertised name,
 * manufacturer company ID (Bluetooth SIG assigned numbers), and service UUIDs.
 */
object DeviceLabel {

    // Bluetooth SIG company identifiers (decimal) for common manufacturers
    // Full list: https://www.bluetooth.com/specifications/assigned-numbers/company-identifiers/
    private val MANUFACTURER_NAMES = mapOf(
        6 to "Microsoft",
        76 to "Apple",
        117 to "Samsung",
        224 to "Google",
        89 to "Nordic Semiconductor",
        13 to "Texas Instruments",
        10 to "Qualcomm",
        48 to "Plantronics",
        87 to "Garmin",
        92 to "Sony",
        152 to "Bose",
        171 to "Amazon",
        269 to "Fitbit",
        305 to "Xiaomi",
        343 to "Huawei",
        388 to "Tile",
        741 to "Anker",
        919 to "Skullcandy",
        1177 to "Jabra",
        2338 to "JBL",
    )

    // Service UUIDs that indicate device type (standard Bluetooth GATT services)
    private val SERVICE_UUID_HINTS = mapOf(
        "0000180d" to "Heart Rate Monitor",
        "00001816" to "Cycling Sensor",
        "0000180f" to "Battery Service",
        "0000180a" to "Device Info",
        "00001812" to "HID Device",
        "0000110b" to "Audio Sink",
        "0000110a" to "Audio Source",
        "00001802" to "Immediate Alert",
        "00001803" to "Link Loss",
        "00001804" to "TX Power",
        "0000fd6f" to "Exposure Notification",
        "0000fe2c" to "Google Fast Pair",
        "7905f431" to "Apple AirPods",
        "74ec2172" to "Apple AirTag",
    )

    // Apple manufacturer data device type bytes (byte at offset 1 of Apple's 0x004C payload)
    private val APPLE_DEVICE_TYPES = mapOf(
        0x01 to "Apple iBeacon",
        0x02 to "Apple iBeacon",
        0x05 to "Apple AirDrop",
        0x07 to "Apple AirPods",
        0x09 to "Apple AirPods Pro",
        0x0A to "Apple AirPods Max",
        0x0C to "Apple HomePod",
        0x0E to "Apple AirPods Pro",
        0x0F to "Apple AirPods",
        0x10 to "Apple Find My",
        0x12 to "Apple Find My",
        0x19 to "Apple AirTag",
    )

    /**
     * Produce the best human-readable label for a device.
     *
     * Priority:
     * 1. Advertised device name (if non-generic)
     * 2. Apple device type inference from manufacturer data
     * 3. Manufacturer name + service UUID hint
     * 4. Manufacturer name alone
     * 5. Service UUID hint alone
     * 6. null (caller should fall back to MAC display)
     */
    fun resolve(
        deviceName: String?,
        manufacturerIds: Set<Int>,
        manufacturerData: Map<Int, ByteArray>? = null,
        serviceUuids: Set<String> = emptySet(),
    ): String? {
        // 1. Advertised name — skip generic/empty names
        if (!deviceName.isNullOrBlank() && !isGenericName(deviceName)) {
            return deviceName
        }

        // 2. Apple device type inference from manufacturer data payload
        if (76 in manufacturerIds && manufacturerData != null) {
            val appleData = manufacturerData[76]
            if (appleData != null && appleData.size >= 2) {
                val deviceType = appleData[0].toInt() and 0xFF
                val appleLabel = APPLE_DEVICE_TYPES[deviceType]
                if (appleLabel != null) return appleLabel
            }
        }

        // 3. Manufacturer + service UUID hint
        val manufacturer = manufacturerIds.firstNotNullOfOrNull { MANUFACTURER_NAMES[it] }
        val serviceHint = serviceUuids.firstNotNullOfOrNull { uuid ->
            val prefix = uuid.take(8).lowercase()
            SERVICE_UUID_HINTS[prefix]
        }

        if (manufacturer != null && serviceHint != null) {
            return "$manufacturer $serviceHint"
        }

        // 4. Manufacturer alone
        if (manufacturer != null) {
            return "$manufacturer device"
        }

        // 5. Service hint alone
        if (serviceHint != null) {
            return serviceHint
        }

        return null
    }

    /**
     * Get manufacturer name from company ID, or null.
     */
    fun manufacturerName(companyId: Int): String? = MANUFACTURER_NAMES[companyId]

    private fun isGenericName(name: String): Boolean {
        val lower = name.lowercase().trim()
        return lower.length <= 2 ||
            lower == "unknown" ||
            lower == "ble device" ||
            lower == "bluetooth device" ||
            lower.matches(Regex("^[0-9a-f]{2}(:[0-9a-f]{2}){2,5}$")) // MAC-like
    }
}
