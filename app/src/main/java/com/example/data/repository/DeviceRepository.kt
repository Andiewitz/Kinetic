package com.example.data.repository

import com.example.data.db.DeviceBookmark
import com.example.data.db.DeviceBookmarkDao
import kotlinx.coroutines.flow.Flow

class DeviceRepository(private val deviceBookmarkDao: DeviceBookmarkDao) {
    val allBookmarks: Flow<List<DeviceBookmark>> = deviceBookmarkDao.getAllBookmarks()

    suspend fun getBookmarkByMac(mac: String): DeviceBookmark? {
        return deviceBookmarkDao.getBookmarkByMac(mac)
    }

    suspend fun saveBookmark(macAddress: String, customName: String, notes: String, category: String) {
        val existing = deviceBookmarkDao.getBookmarkByMac(macAddress)
        val bookmark = DeviceBookmark(
            macAddress = macAddress,
            customName = if (customName.isBlank()) existing?.customName ?: macAddress else customName,
            notes = notes,
            category = category,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        deviceBookmarkDao.insertBookmark(bookmark)
    }

    suspend fun removeBookmark(macAddress: String) {
        deviceBookmarkDao.deleteBookmark(macAddress)
    }
}
