package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.EEWNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EEWNotificationDao {

    @Query("SELECT * FROM eew_notification_logs ORDER BY receivedAt DESC")
    fun getAllLogs(): Flow<List<EEWNotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: EEWNotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<EEWNotificationEntity>): List<Long>

    @Query("SELECT * FROM eew_notification_logs WHERE alertId = :alertId LIMIT 1")
    suspend fun getLogByAlertId(alertId: String): EEWNotificationEntity?

    @Delete
    suspend fun deleteLog(log: EEWNotificationEntity)

    @Query("DELETE FROM eew_notification_logs")
    suspend fun clearAllLogs()

    @Query("SELECT COUNT(*) FROM eew_notification_logs")
    fun getLogCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM eew_notification_logs")
    suspend fun countStatic(): Int
}
