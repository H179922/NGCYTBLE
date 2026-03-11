package com.ngcyt.ble.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "api_sync_queue",
    indices = [
        Index(value = ["status"]),
    ]
)
data class ApiSyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val payload: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Double,
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    @ColumnInfo(name = "last_attempt")
    val lastAttempt: Double? = null,
    val status: String = STATUS_PENDING,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SENDING = "SENDING"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_COMPLETED = "COMPLETED"
    }
}
