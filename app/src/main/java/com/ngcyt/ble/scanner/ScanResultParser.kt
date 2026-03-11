package com.ngcyt.ble.scanner

import android.bluetooth.le.ScanResult
import android.os.ParcelUuid

data class ParsedScanResult(
    val mac: String,
    val deviceName: String?,
    val rssi: Int,
    val txPower: Int,
    val serviceUuids: Set<String>,
    val manufacturerData: Map<Int, ByteArray>,
    val isConnectable: Boolean,
    val timestamp: Long,
)

object ScanResultParser {
    fun parse(result: ScanResult): ParsedScanResult {
        val record = result.scanRecord

        val serviceUuids = mutableSetOf<String>()
        record?.serviceUuids?.forEach { uuid: ParcelUuid ->
            serviceUuids.add(uuid.uuid.toString())
        }

        val manufacturerData = mutableMapOf<Int, ByteArray>()
        record?.manufacturerSpecificData?.let { sparseArray ->
            for (i in 0 until sparseArray.size()) {
                manufacturerData[sparseArray.keyAt(i)] = sparseArray.valueAt(i)
            }
        }

        return ParsedScanResult(
            mac = result.device.address,
            deviceName = result.device.name ?: record?.deviceName,
            rssi = result.rssi,
            txPower = record?.txPowerLevel ?: 0,
            serviceUuids = serviceUuids,
            manufacturerData = manufacturerData,
            isConnectable = result.isConnectable,
            timestamp = result.timestampNanos,
        )
    }
}
