package com.example.ble

import kotlin.math.pow

data class BleDevice(
    val name: String,
    val macAddress: String,
    val rssi: Int,
    val isConnectable: Boolean = true,
    val txPower: Int = -59,
    val manufacturerData: String = "",
    val isConnected: Boolean = false,
    val services: List<String> = emptyList(),
    val category: String = "Other",
    val signalHistory: List<Int> = listOf(rssi)
) {
    // Estimating distance using the Log-Normal Shadowing Model / Free Space Path Loss
    val distanceMeters: Double
        get() {
            if (rssi == 0) return -1.0
            val ratio = rssi * 1.0 / txPower
            return if (ratio < 1.0) {
                ratio.pow(10.0)
            } else {
                (0.89976) * ratio.pow(7.7095) + 0.111
            }
        }
}
