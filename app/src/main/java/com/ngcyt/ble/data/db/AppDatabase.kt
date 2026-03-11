package com.ngcyt.ble.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ngcyt.ble.data.db.dao.ApiSyncQueueDao
import com.ngcyt.ble.data.db.dao.BehaviorVectorDao
import com.ngcyt.ble.data.db.dao.DeviceFingerprintDao
import com.ngcyt.ble.data.db.dao.DeviceSightingDao
import com.ngcyt.ble.data.db.dao.ThreatAssessmentDao
import com.ngcyt.ble.data.db.entity.ApiSyncQueueEntity
import com.ngcyt.ble.data.db.entity.BehaviorVectorEntity
import com.ngcyt.ble.data.db.entity.DeviceFingerprintEntity
import com.ngcyt.ble.data.db.entity.DeviceSightingEntity
import com.ngcyt.ble.data.db.entity.ThreatAssessmentEntity

@Database(
    entities = [
        DeviceSightingEntity::class,
        DeviceFingerprintEntity::class,
        BehaviorVectorEntity::class,
        ThreatAssessmentEntity::class,
        ApiSyncQueueEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceSightingDao(): DeviceSightingDao
    abstract fun deviceFingerprintDao(): DeviceFingerprintDao
    abstract fun behaviorVectorDao(): BehaviorVectorDao
    abstract fun threatAssessmentDao(): ThreatAssessmentDao
    abstract fun apiSyncQueueDao(): ApiSyncQueueDao
}
