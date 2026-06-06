package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_records")
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentName: String,
    val studentId: String,
    val deviceName: String,
    val macAddress: String,
    val payloadBytesHex: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Present" // e.g., "Present", "Late", "Excused"
)
