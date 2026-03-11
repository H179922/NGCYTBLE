package com.ngcyt.ble.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ngcyt.ble.data.db.entity.DeviceSightingEntity

@Dao
interface DeviceSightingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sighting: DeviceSightingEntity): Long

    @Query("SELECT * FROM device_sightings WHERE mac = :mac ORDER BY timestamp DESC")
    suspend fun getByMac(mac: String): List<DeviceSightingEntity>

    @Query("SELECT * FROM device_sightings WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getByTimeRange(startTime: Double, endTime: Double): List<DeviceSightingEntity>

    @Query("DELETE FROM device_sightings WHERE timestamp < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Double): Int

    @Query("SELECT COUNT(*) FROM device_sightings")
    suspend fun getCount(): Int
}
