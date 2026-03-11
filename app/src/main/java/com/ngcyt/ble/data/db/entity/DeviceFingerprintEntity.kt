package com.ngcyt.ble.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "device_fingerprints",
    indices = [
        Index(value = ["cluster_id"]),
    ]
)
data class DeviceFingerprintEntity(
    @PrimaryKey
    val mac: String,
    @ColumnInfo(name = "cluster_id")
    val clusterId: String?,
    @ColumnInfo(name = "cluster_confidence")
    val clusterConfidence: Float,
    @ColumnInfo(name = "service_uuids")
    val serviceUuids: String?,
    @ColumnInfo(name = "manufacturer_data_keys")
    val manufacturerDataKeys: String?,
    @ColumnInfo(name = "tx_power")
    val txPower: Int?,
    @ColumnInfo(name = "first_seen")
    val firstSeen: Double,
    @ColumnInfo(name = "last_seen")
    val lastSeen: Double,
    @ColumnInfo(name = "total_advertisements")
    val totalAdvertisements: Int,
)
