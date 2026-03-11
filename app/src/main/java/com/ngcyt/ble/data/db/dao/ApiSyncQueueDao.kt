package com.ngcyt.ble.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ngcyt.ble.data.db.entity.ApiSyncQueueEntity

@Dao
interface ApiSyncQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ApiSyncQueueEntity): Long

    @Query("SELECT * FROM api_sync_queue WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit")
    suspend fun getPending(limit: Int): List<ApiSyncQueueEntity>

    @Query("UPDATE api_sync_queue SET status = :status, last_attempt = :lastAttempt, retry_count = retry_count + 1 WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, lastAttempt: Double)

    @Query("DELETE FROM api_sync_queue WHERE status = 'COMPLETED'")
    suspend fun deleteCompleted(): Int

    @Query("SELECT * FROM api_sync_queue WHERE status = 'FAILED' AND retry_count < :maxRetries ORDER BY created_at ASC")
    suspend fun getRetryable(maxRetries: Int = 3): List<ApiSyncQueueEntity>
}
