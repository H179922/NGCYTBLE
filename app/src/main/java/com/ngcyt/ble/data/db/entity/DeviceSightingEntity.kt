package com.ngcyt.ble.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "device_sightings",
    indices = [
        Index(value = ["mac"]),
        Index(value = ["timestamp"]),
        Index(value = ["time_bucket"]),
    ]
)
data class DeviceSightingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Double,
    val mac: String,
    val rssi: Int,
    @ColumnInfo(name = "service_uuids")
    val serviceUuids: String?,
    @ColumnInfo(name = "manufacturer_data", typeAffinity = ColumnInfo.BLOB)
    val manufacturerData: ByteArray?,
    val latitude: Double?,
    val longitude: Double?,
    @ColumnInfo(name = "location_accuracy")
    val locationAccuracy: Float?,
    @ColumnInfo(name = "location_provider")
    val locationProvider: String?,
    @ColumnInfo(name = "time_bucket")
    val timeBucket: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceSightingEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
