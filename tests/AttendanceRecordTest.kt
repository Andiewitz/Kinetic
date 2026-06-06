package com.example

import com.example.data.db.AttendanceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AttendanceRecordTest {

    @Test
    fun testAttendanceRecordCreationAndDefaults() {
        val record = AttendanceRecord(
            studentName = "Alex Rivera",
            studentId = "202410",
            deviceName = "Alex Phone",
            macAddress = "34:A4:E3:D8:C0:09",
            payloadBytesHex = "FFE8AA03",
            status = "Present"
        )

        assertEquals("Alex Rivera", record.studentName)
        assertEquals("202410", record.studentId)
        assertEquals("Alex Phone", record.deviceName)
        assertEquals("34:A4:E3:D8:C0:09", record.macAddress)
        assertEquals("FFE8AA03", record.payloadBytesHex)
        assertEquals("Present", record.status)
        assertEquals(0L, record.id)
        assertNotNull(record.timestamp)
    }

    @Test
    fun testAttendanceRecordStatusInitialization() {
        val recordLate = AttendanceRecord(
            studentName = "Zoe Chen",
            studentId = "202411",
            deviceName = "Zoe BT",
            macAddress = "C0:31:AA:FF:88:99",
            payloadBytesHex = "FFE8BB01",
            status = "Late"
        )
        assertEquals("Late", recordLate.status)

        val recordExcused = AttendanceRecord(
            studentName = "Marcus Vance",
            studentId = "202412",
            deviceName = "Marcus Broadcast",
            macAddress = "FF:EE:DD:AA:B1:C2",
            payloadBytesHex = "FFE8CC02",
            status = "Excused"
        )
        assertEquals("Excused", recordExcused.status)
    }
}
