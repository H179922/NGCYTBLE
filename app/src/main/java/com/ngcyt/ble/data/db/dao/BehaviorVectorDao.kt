package com.ngcyt.ble.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ngcyt.ble.data.db.entity.BehaviorVectorEntity

@Dao
interface BehaviorVectorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(vector: BehaviorVectorEntity)

    @Query("SELECT * FROM behavior_vectors WHERE device_id = :deviceId")
    suspend fun getByDeviceId(deviceId: String): BehaviorVectorEntity?

    @Query("SELECT * FROM behavior_vectors")
    suspend fun getAll(): List<BehaviorVectorEntity>

    @Query("DELETE FROM behavior_vectors WHERE device_id = :deviceId")
    suspend fun deleteByDeviceId(deviceId: String): Int
}
