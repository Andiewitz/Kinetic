package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceBookmarkDao {
    @Query("SELECT * FROM device_bookmarks ORDER BY lastSeenTimestamp DESC")
    fun getAllBookmarks(): Flow<List<DeviceBookmark>>

    @Query("SELECT * FROM device_bookmarks WHERE macAddress = :macAddress LIMIT 1")
    suspend fun getBookmarkByMac(macAddress: String): DeviceBookmark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: DeviceBookmark)

    @Query("DELETE FROM device_bookmarks WHERE macAddress = :macAddress")
    suspend fun deleteBookmark(macAddress: String)
}
