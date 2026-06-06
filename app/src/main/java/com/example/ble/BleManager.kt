package com.example.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class BleManager(private val context: Context) {
    private val tag = "BleManager"

    // Underneath Bluetooth hooks
    private var bluetoothAdapter: BluetoothAdapter? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scannedDevices = MutableStateFlow<Map<String, BleDevice>>(emptyMap())
    val scannedDevices: StateFlow<List<BleDevice>> = MutableStateFlow<List<BleDevice>>(emptyList()).also { flow ->
        // Expose devices as a sorted list
        CoroutineScope(Dispatchers.Main).launch {
            _scannedDevices.collect { map ->
                (flow as MutableStateFlow<List<BleDevice>>).value = map.values.toList().sortedByDescending { it.rssi }
            }
        }
    }.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BleDevice?>(null)
    val connectedDevice: StateFlow<BleDevice?> = _connectedDevice.asStateFlow()

    private val _gattServicesAndCharacteristics = MutableStateFlow<List<GattService>>(emptyList())
    val gattServicesAndCharacteristics: StateFlow<List<GattService>> = _gattServicesAndCharacteristics.asStateFlow()

    private var scanJob: Job? = null
    private var mockRssiJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    // Simulated device data list
    private val mockBaseDevices = listOf(
        BleDevice("PulseTrack Ultra", "7C:96:D2:8B:4F:1A", -62, isConnectable = true, category = "SmartBand", txPower = -58, services = listOf("180D", "180F", "180A")),
        BleDevice("VitalHeart Pro", "A0:C5:5F:B6:77:3E", -74, isConnectable = true, category = "Health Sensor", txPower = -59, services = listOf("180D", "180A")),
        BleDevice("Beacon-SafeCast", "FF:EE:DD:CC:BB:AA", -88, isConnectable = false, category = "Beacon", txPower = -55, services = listOf("FEAA")),
        BleDevice("AuraSound Air", "34:A4:E3:D8:C0:09", -52, isConnectable = true, category = "Audio", txPower = -61, services = listOf("180F", "180A")),
        BleDevice("Matrix Nest Hub", "48:5F:99:11:AA:C2", -79, isConnectable = true, category = "SmartHome", txPower = -57, services = listOf("180F")),
        BleDevice("HydraBottle Smart", "BC:30:5E:2A:43:88", -69, isConnectable = true, category = "Aesthetic Care", txPower = -62, services = listOf("180F"))
    )

    init {
        try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = bm?.adapter
        } catch (e: Exception) {
            Log.e(tag, "BluetoothManager build failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return
        _isScanning.value = true

        Log.d(tag, "Scanning started")
        // Initialize with default states to simulate sudden detection
        _scannedDevices.value = mockBaseDevices.take(3).associateBy { it.macAddress }

        scanJob = scope.launch {
            var counter = 0
            while (_isScanning.value) {
                delay(1200)
                counter++

                // Introduce other devices step-by-step
                if (counter == 2 && mockBaseDevices.size > 3) {
                    addOrUpdateDevice(mockBaseDevices[3])
                } else if (counter == 4 && mockBaseDevices.size > 4) {
                    addOrUpdateDevice(mockBaseDevices[4])
                    addOrUpdateDevice(mockBaseDevices[5])
                }

                // Randomly perturb RSSI signals for existing scan list
                _scannedDevices.update { currentMap ->
                    currentMap.mapValues { (_, device) ->
                        // Don't perturb if currently connected (or lock RSSI fluctuations down)
                        val connected = _connectedDevice.value?.macAddress == device.macAddress
                        val change = if (connected) Random.nextInt(-2, 3) else Random.nextInt(-6, 7)
                        val newRssi = (device.rssi + change).coerceIn(-100, -35)
                        val history = (device.signalHistory + newRssi).takeLast(10)
                        device.copy(rssi = newRssi, signalHistory = history)
                    }
                }
            }
        }
    }

    fun stopScan() {
        if (!_isScanning.value) return
        _isScanning.value = false
        scanJob?.cancel()
        Log.d(tag, "Scanning stopped")
    }

    private fun addOrUpdateDevice(device: BleDevice) {
        _scannedDevices.update { current ->
            if (current.containsKey(device.macAddress)) {
                current
            } else {
                current + (device.macAddress to device)
            }
        }
    }

    fun connectDevice(device: BleDevice) {
        scope.launch {
            // Signal connection state
            _scannedDevices.update { current ->
                current.mapValues { (_, d) ->
                    if (d.macAddress == device.macAddress) d.copy(isConnected = true) else d
                }
            }
            _connectedDevice.value = device.copy(isConnected = true)
            Log.d(tag, "Connected to ${device.name}")

            // Simulate loading services & characteristics
            _gattServicesAndCharacteristics.value = listOf(GattService("Connecting Services...", emptyList()))
            delay(1500)

            _gattServicesAndCharacteristics.value = generateSimulatedGattServices(device)

            // Start characteristics telemetry updates
            startMockTelemetry()
        }
    }

    fun disconnectDevice() {
        val currentConnected = _connectedDevice.value ?: return
        scope.launch {
            _scannedDevices.update { current ->
                current.mapValues { (_, d) ->
                    if (d.macAddress == currentConnected.macAddress) d.copy(isConnected = false) else d
                }
            }
            _connectedDevice.value = null
            _gattServicesAndCharacteristics.value = emptyList()
            mockRssiJob?.cancel()
            Log.d(tag, "Disconnected from ${currentConnected.name}")
        }
    }

    private fun startMockTelemetry() {
        mockRssiJob?.cancel()
        mockRssiJob = scope.launch {
            while (_connectedDevice.value != null) {
                delay(1800)
                // Randomly modify telemetry measurements
                _gattServicesAndCharacteristics.update { currentServices ->
                    currentServices.map { service ->
                        service.copy(
                            characteristics = service.characteristics.map { char ->
                                if (char.value.contains("bpm")) {
                                    val bpm = Random.nextInt(68, 128)
                                    char.copy(value = "$bpm bpm")
                                } else if (char.value.contains("%")) {
                                    val pct = (char.value.removeSuffix("%").toIntOrNull() ?: 84)
                                    val newPct = (pct - 1).coerceAtLeast(1)
                                    char.copy(value = "$newPct%")
                                } else {
                                    char
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun generateSimulatedGattServices(device: BleDevice): List<GattService> {
        val list = mutableListOf<GattService>()

        // 1. Device Info Service
        list.add(
            GattService(
                name = "Device Information Service (0x180A)",
                characteristics = listOf(
                    GattCharacteristic("Manufacturer Name", "PulseLink Innovations LLC"),
                    GattCharacteristic("Model Number", "PL-${device.macAddress.takeLast(5).replace(":", "")}"),
                    GattCharacteristic("Firmware Version", "v2.4.11-rc3"),
                    GattCharacteristic("Hardware Revision", "Rev B01")
                )
            )
        )

        // 2. Battery Service (if supported)
        if (device.services.contains("180F")) {
            list.add(
                GattService(
                    name = "Battery Service (0x180F)",
                    characteristics = listOf(
                        GattCharacteristic("Battery Level", "${Random.nextInt(65, 100)}%")
                    )
                )
            )
        }

        // 3. Heart Rate Service (if supported)
        if (device.services.contains("180D")) {
            list.add(
                GattService(
                    name = "Heart Rate Service (0x180D)",
                    characteristics = listOf(
                        GattCharacteristic("Heart Rate Measurement", "${Random.nextInt(72, 85)} bpm"),
                        GattCharacteristic("Body Sensor Location", "Wrist Placement")
                    )
                )
            )
        }

        // 4. Custom telemetry
        list.add(
            GattService(
                name = "Proprietary Sensor Profile (0xFFE1)",
                characteristics = listOf(
                    GattCharacteristic("Real-time Signal RSSI", "${device.rssi} dBm"),
                    GattCharacteristic("Payload Sequence Packet", "0x${Random.nextInt(100, 9999)}")
                )
            )
        )

        return list
    }
}

data class GattService(
    val name: String,
    val characteristics: List<GattCharacteristic>
)

data class GattCharacteristic(
    val uuidName: String,
    val value: String
)
