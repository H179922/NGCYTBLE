package com.ngcyt.ble.scanner

enum class ScanStatus {
    IDLE,
    SCANNING,
    BLUETOOTH_OFF,
    LOCATION_REQUIRED,
    SCAN_FAILED,
    PERMISSION_DENIED,
}
