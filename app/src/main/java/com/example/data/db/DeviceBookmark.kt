package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_bookmarks")
data class DeviceBookmark(
    @PrimaryKey val macAddress: String,
    val customName: String,
    val notes: String = "",
    val category: String = "Unknown",
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)
