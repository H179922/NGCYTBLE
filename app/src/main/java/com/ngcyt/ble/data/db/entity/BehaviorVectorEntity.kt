package com.ngcyt.ble.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "behavior_vectors")
data class BehaviorVectorEntity(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val vector: FloatArray,
    @ColumnInfo(name = "ad_count")
    val adCount: Int,
    @ColumnInfo(name = "service_list")
    val serviceList: String?,
    @ColumnInfo(name = "uses_randomization")
    val usesRandomization: Boolean,
    val timestamp: Double,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BehaviorVectorEntity) return false
        return deviceId == other.deviceId
    }

    override fun hashCode(): Int = deviceId.hashCode()
}
