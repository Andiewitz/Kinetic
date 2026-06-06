package com.example

import com.example.ble.BleDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleDeviceTest {

    @Test
    fun testBleDeviceDistanceCalculationWithZeroRssi() {
        val device = BleDevice(
            name = "Test student beacon",
            macAddress = "AA:BB:CC:DD:EE:FF",
            rssi = 0,
            txPower = -59
        )
        assertEquals(-1.0, device.distanceMeters, 0.001)
    }

    @Test
    fun testBleDeviceDistanceAtTxPower() {
        // When RSSI matches original Tx Power, distance calculation ratio is 1.0
        val device = BleDevice(
            name = "Test student beacon",
            macAddress = "AA:BB:CC:DD:EE:FF",
            rssi = -59,
            txPower = -59
        )
        // Ratio is -59 / -59 = 1.0, which is not < 1.0 (it is equal to 1.0)
        // Math: (0.89976) * 1.0.pow(7.7095) + 0.111 = 0.89976 + 0.111 = 1.01076
        assertEquals(1.01076, device.distanceMeters, 0.001)
    }

    @Test
    fun testBleDeviceDistanceWithWeakerSignal() {
        val device1 = BleDevice(
            name = "Close beacon",
            macAddress = "11:22:33:44:55:66",
            rssi = -50,
            txPower = -59
        )
        val device2 = BleDevice(
            name = "Far beacon",
            macAddress = "AA:BB:CC:DD:EE:FF",
            rssi = -90,
            txPower = -59
        )
        // A weaker RSSI (more negative) should yield a farther distance estimation
        assertTrue(device2.distanceMeters > device1.distanceMeters)
    }

    @Test
    fun testBleDeviceDefaults() {
        val device = BleDevice(
            name = "Default Student Beacon",
            macAddress = "00:11:22:33:44:55",
            rssi = -70
        )
        assertEquals("Other", device.category)
        assertTrue(device.isConnectable)
        assertEquals(-59, device.txPower)
        assertEquals("", device.manufacturerData)
        assertEquals(1, device.signalHistory.size)
        assertEquals(-70, device.signalHistory[0])
    }
}
