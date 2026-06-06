package com.example.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DeviceBookmark::class, AttendanceRecord::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceBookmarkDao(): DeviceBookmarkDao
    abstract fun attendanceRecordDao(): AttendanceRecordDao
}
