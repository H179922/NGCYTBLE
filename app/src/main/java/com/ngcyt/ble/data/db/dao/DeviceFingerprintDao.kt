package com.ngcyt.ble.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ngcyt.ble.data.db.entity.DeviceFingerprintEntity

@Dao
interface DeviceFingerprintDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(fingerprint: DeviceFingerprintEntity)

    @Query("SELECT * FROM device_fingerprints WHERE mac = :mac")
    suspend fun getByMac(mac: String): DeviceFingerprintEntity?

    @Query("SELECT * FROM device_fingerprints WHERE cluster_id = :clusterId")
    suspend fun getByClusterId(clusterId: String): List<DeviceFingerprintEntity>

    @Query("DELETE FROM device_fingerprints WHERE last_seen < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Double): Int
}
