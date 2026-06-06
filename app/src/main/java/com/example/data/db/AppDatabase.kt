package com.example.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DeviceBookmark::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceBookmarkDao(): DeviceBookmarkDao
}
