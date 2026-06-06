package com.example.data.repository

import com.example.data.db.DeviceBookmark
import com.example.data.db.DeviceBookmarkDao
import com.example.data.db.AttendanceRecord
import com.example.data.db.AttendanceRecordDao
import kotlinx.coroutines.flow.Flow

class DeviceRepository(
    private val deviceBookmarkDao: DeviceBookmarkDao,
    private val attendanceRecordDao: AttendanceRecordDao
) {
    val allBookmarks: Flow<List<DeviceBookmark>> = deviceBookmarkDao.getAllBookmarks()
    val allRecords: Flow<List<AttendanceRecord>> = attendanceRecordDao.getAllRecords()

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

    suspend fun addAttendanceRecord(name: String, studentId: String, deviceName: String, mac: String, status: String, bytesHex: String) {
        val record = AttendanceRecord(
            studentName = name,
            studentId = studentId,
            deviceName = deviceName,
            macAddress = mac,
            payloadBytesHex = bytesHex,
            timestamp = System.currentTimeMillis(),
            status = status
        )
        attendanceRecordDao.insertRecord(record)
    }

    suspend fun deleteAttendanceRecord(id: Long) {
        attendanceRecordDao.deleteRecord(id)
    }

    suspend fun clearAllAttendanceRecords() {
        attendanceRecordDao.clearAllRecords()
    }
}
