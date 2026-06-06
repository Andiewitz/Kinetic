package com.example.ble

data class GattService(
    val name: String,
    val characteristics: List<GattCharacteristic>
)

data class GattCharacteristic(
    val uuidName: String,
    val value: String
)
