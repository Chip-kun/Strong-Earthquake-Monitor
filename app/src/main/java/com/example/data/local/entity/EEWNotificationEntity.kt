package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "eew_notification_logs")
data class EEWNotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val alertId: String,
    val reportNum: String,
    val originTime: String,
    val hypocenter: String,
    val magnitude: Float,
    val depthKm: Int,
    val maxShindo: String,
    val isWarning: Boolean,
    val isDrill: Boolean = false,
    val affectedAreas: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val receivedAt: Long = System.currentTimeMillis()
)
