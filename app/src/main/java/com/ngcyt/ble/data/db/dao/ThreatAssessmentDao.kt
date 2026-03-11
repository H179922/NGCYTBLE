package com.ngcyt.ble.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ngcyt.ble.data.db.entity.ThreatAssessmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreatAssessmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assessment: ThreatAssessmentEntity): Long

    @Query("SELECT * FROM threat_assessments WHERE mac = :mac ORDER BY timestamp DESC")
    suspend fun getByMac(mac: String): List<ThreatAssessmentEntity>

    @Query("SELECT * FROM threat_assessments ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<ThreatAssessmentEntity>>

    @Query("SELECT * FROM threat_assessments WHERE threat_score >= :minScore ORDER BY threat_score DESC")
    fun getAboveScore(minScore: Int): Flow<List<ThreatAssessmentEntity>>

    @Query("DELETE FROM threat_assessments WHERE timestamp < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Double): Int
}
