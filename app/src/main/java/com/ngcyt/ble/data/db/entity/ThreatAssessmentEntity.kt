package com.ngcyt.ble.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ngcyt.ble.domain.model.ThreatLevel
import com.ngcyt.ble.domain.model.ThreatSource

@Entity(
    tableName = "threat_assessments",
    indices = [
        Index(value = ["mac"]),
        Index(value = ["threat_score"]),
        Index(value = ["timestamp"]),
    ]
)
data class ThreatAssessmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mac: String,
    @ColumnInfo(name = "device_type")
    val deviceType: String,
    @ColumnInfo(name = "threat_score")
    val threatScore: Int,
    @ColumnInfo(name = "threat_level")
    val threatLevel: ThreatLevel,
    @ColumnInfo(name = "time_buckets")
    val timeBuckets: String?,
    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Double,
    @ColumnInfo(name = "service_uuids")
    val serviceUuids: String?,
    val reasoning: String,
    @ColumnInfo(name = "physical_device_id")
    val physicalDeviceId: String?,
    @ColumnInfo(name = "associated_macs")
    val associatedMacs: String?,
    @ColumnInfo(name = "fingerprint_confidence")
    val fingerprintConfidence: Double,
    @ColumnInfo(name = "is_mac_randomized")
    val isMacRandomized: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    @ColumnInfo(name = "location_accuracy")
    val locationAccuracy: Float?,
    val source: ThreatSource,
    val timestamp: Double,
)
