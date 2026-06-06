package com.example.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.example.data.db.AttendanceRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import kotlin.random.Random

class BleManager(private val context: Context) {
    private val tag = "BleManager"

    // Custom UUID for the Attendance Service (0x18F4 or a full 128-bit UUID)
    val ATTENDANCE_SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val ATTENDANCE_CHAR_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

    // Android Bluetooth hooks
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _scannedDevices = MutableStateFlow<Map<String, BleDevice>>(emptyMap())
    val scannedDevices: StateFlow<List<BleDevice>> = MutableStateFlow<List<BleDevice>>(emptyList()).also { flow ->
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

    // Stream of detected attendance check-in signals (from actual BLE scanning or simulation)
    private val _detectedAttendance = MutableSharedFlow<AttendanceRecord>(extraBufferCapacity = 64)
    val detectedAttendance: SharedFlow<AttendanceRecord> = _detectedAttendance.asSharedFlow()

    private var scanJob: Job? = null
    private var advertisingJob: Job? = null
    private var mockRssiJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    // Mock student templates that scan can locate (representing local devices/classmates)
    private val mockBaseDevices = listOf(
        BleDevice("Alex Rivera (Student)", "B4:4B:D2:12:34:56", -55, isConnectable = true, category = "Student ID: 1004", txPower = -58, services = listOf("ffe0")),
        BleDevice("Zoe Chen (Student)", "C0:31:AA:FF:88:99", -68, isConnectable = true, category = "Student ID: 1007", txPower = -59, services = listOf("ffe0")),
        BleDevice("Marcus Vance (Student)", "FF:EE:DD:AA:B1:C2", -71, isConnectable = true, category = "Student ID: 1012", txPower = -55, services = listOf("ffe0")),
        BleDevice("Aura Sound Pod", "34:A4:E3:D8:C0:09", -82, isConnectable = false, category = "Audio Link", txPower = -61, services = listOf("180F", "180A")),
        BleDevice("Main Hall Transmitter", "48:5F:99:11:AA:C2", -89, isConnectable = false, category = "Infrastructure", txPower = -57, services = listOf("180F"))
    )

    init {
        try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = bm?.adapter
            if (bluetoothAdapter != null) {
                bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
                bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
            }
        } catch (e: Exception) {
            Log.e(tag, "Bluetooth Initialization warning", e)
        }
    }

    // --- RECEIVER SCANNING ---

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return
        _isScanning.value = true
        Log.d(tag, "Scanning started")

        // 1. Setup real BLE Scan if possible
        try {
            if (bluetoothLeScanner != null) {
                val filters = listOf(
                    ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(ATTENDANCE_SERVICE_UUID))
                        .build()
                )
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()

                bluetoothLeScanner?.startScan(filters, settings, realScanCallback)
                Log.d(tag, "Real BLE hardware scanner started successfully")
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not start real BLE scan (missing permissions or BLE disabled): ${e.message}")
        }

        // Initialize virtual devices for instant browser testing
        _scannedDevices.value = mockBaseDevices.associateBy { it.macAddress }

        // 2. Start simulation loop for background updates & RSSI fluctuations
        scanJob = scope.launch {
            while (_isScanning.value) {
                delay(1500)

                // Perturb RSSI and signal values
                _scannedDevices.update { currentMap ->
                    currentMap.mapValues { (_, device) ->
                        val connected = _connectedDevice.value?.macAddress == device.macAddress
                        val change = if (connected) Random.nextInt(-1, 2) else Random.nextInt(-4, 5)
                        val newRssi = (device.rssi + change).coerceIn(-95, -30)
                        val history = (device.signalHistory + newRssi).takeLast(10)
                        device.copy(rssi = newRssi, signalHistory = history)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        _isScanning.value = false
        scanJob?.cancel()

        try {
            if (bluetoothLeScanner != null) {
                bluetoothLeScanner?.stopScan(realScanCallback)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error stopping real BLE scan: ${e.message}")
        }
        Log.d(tag, "Scanning stopped")
    }

    private val realScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            if (result == null || result.device == null) return

            val address = result.device.address ?: "00:00:00:00:00:00"
            val rawName = result.device.name ?: "Unknown Peer"
            val rssi = result.rssi
            val txPower = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                result.txPower
            } else {
                -59
            }

            // Detect check-in packets via advertised Service Data
            val serviceData = result.scanRecord?.getServiceData(ParcelUuid(ATTENDANCE_SERVICE_UUID))
            var studentId = "Unknown ID"
            var studentName = rawName
            var status = "Present"
            var customCategory = "Student Link"

            if (serviceData != null) {
                try {
                    val decodedString = String(serviceData, Charsets.UTF_8)
                    // Format: "ID;Name;Status"
                    val parts = decodedString.split(";")
                    if (parts.size >= 3) {
                        studentId = parts[0]
                        studentName = parts[1]
                        status = parts[2]
                        customCategory = "Student ID: $studentId"

                        // Automatically check them in instantly upon BLE detection!
                        val record = AttendanceRecord(
                            studentName = studentName,
                            studentId = studentId,
                            deviceName = rawName,
                            macAddress = address,
                            payloadBytesHex = bytesToHex(serviceData),
                            status = status
                        )
                        _detectedAttendance.tryEmit(record)
                    }
                } catch (e: java.lang.Exception) {
                    Log.e(tag, "Error parsing advertiser payload: ${e.message}")
                }
            }

            val device = BleDevice(
                name = studentName,
                macAddress = address,
                rssi = rssi,
                isConnectable = true,
                txPower = txPower,
                services = listOf("ffe0"),
                category = customCategory
            )

            _scannedDevices.update { current ->
                current + (address to device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(tag, "BLE Scan failed with code $errorCode")
        }
    }

    // --- SENDER ADVERTISING / BROADCASTING ---

    @SuppressLint("MissingPermission")
    fun startAdvertising(studentName: String, studentId: String, status: String) {
        if (_isAdvertising.value) return
        _isAdvertising.value = true
        Log.d(tag, "Advertising check-in for $studentName ($studentId)")

        // 1. Real BLE hardware Advertising if supported
        try {
            if (bluetoothLeAdvertiser != null) {
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build()

                // Compress data into service payload: "ID;Name;Status"
                val payloadString = "$studentId;$studentName;$status"
                val payloadBytes = payloadString.toByteArray(Charsets.UTF_8)

                val data = AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .addServiceUuid(ParcelUuid(ATTENDANCE_SERVICE_UUID))
                    .addServiceData(ParcelUuid(ATTENDANCE_SERVICE_UUID), payloadBytes)
                    .build()

                bluetoothLeAdvertiser?.startAdvertising(settings, data, realAdvertiseCallback)
                Log.d(tag, "Real BLE hardware advertising initiated with payload: $payloadString")

                // Start GATT Server to allow receiver to connect and read/write characteristics directly
                setupGattServer(studentName, studentId, status)
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not start real advertisement (BLE hardware advertiser unsupported or permissions missing): ${e.message}")
        }

        // 2. Simulated self-advertising loops for developer tools
        advertisingJob = scope.launch {
            while (_isAdvertising.value) {
                delay(1200)
                Log.d(tag, "[Simulation] Broadcasting beacon chirp from $studentName...")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (!_isAdvertising.value) return
        _isAdvertising.value = false
        advertisingJob?.cancel()

        try {
            if (bluetoothLeAdvertiser != null) {
                bluetoothLeAdvertiser?.stopAdvertising(realAdvertiseCallback)
            }
            if (gattServer != null) {
                gattServer?.close()
                gattServer = null
            }
        } catch (e: Exception) {
            Log.e(tag, "Error stopping advertising: ${e.message}")
        }
        Log.d(tag, "Advertising stopped")
    }

    private val realAdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d(tag, "BLE Advertisement successfully airing")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(tag, "BLE Advertisement error code $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupGattServer(name: String, studentId: String, status: String) {
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            gattServer = bluetoothManager.openGattServer(context, object : BluetoothGattServerCallback() {
                override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                    super.onConnectionStateChange(device, status, newState)
                    Log.d(tag, "GATT Server client state change: device ${device?.address} state $newState")
                }

                override fun onCharacteristicReadRequest(
                    device: BluetoothDevice?,
                    requestId: Int,
                    offset: Int,
                    characteristic: BluetoothGattCharacteristic?
                ) {
                    super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
                    if (characteristic?.uuid == ATTENDANCE_CHAR_UUID) {
                        val payloadString = "$studentId;$name;$status"
                        val responseBytes = payloadString.toByteArray(Charsets.UTF_8).drop(offset).toByteArray()
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseBytes)
                    }
                }
            })

            val service = BluetoothGattService(ATTENDANCE_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val characteristic = BluetoothGattCharacteristic(
                ATTENDANCE_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            service.addCharacteristic(characteristic)
            gattServer?.addService(service)
        } catch (e: Exception) {
            Log.e(tag, "Failed to provision custom GATT Service: ${e.message}")
        }
    }

    // --- EXCHANGING BYTES AND TESTING FLOWS ---

    /**
     * Triggers a direct simulated bite receipt from a virtual peripheral
     */
    fun simulateDirectCheckIn(vDevice: BleDevice, id: String, name: String, status: String) {
        scope.launch {
            val servicePayload = "$id;$name;$status"
            val payloadBytes = servicePayload.toByteArray(Charsets.UTF_8)
            val record = AttendanceRecord(
                studentName = name,
                studentId = id,
                deviceName = vDevice.name,
                macAddress = vDevice.macAddress,
                payloadBytesHex = bytesToHex(payloadBytes),
                status = status
            )
            delay(800) // simulated handshake lag
            _detectedAttendance.emit(record)
        }
    }

    /**
     * Connects to a device and performs the real or simulated direct bytes handshake
     */
    fun connectDevice(device: BleDevice) {
        scope.launch {
            _scannedDevices.update { current ->
                current.mapValues { (_, d) ->
                    if (d.macAddress == device.macAddress) d.copy(isConnected = true) else d
                }
            }
            _connectedDevice.value = device.copy(isConnected = true)
            Log.d(tag, "Handshaking with ${device.name}")

            _gattServicesAndCharacteristics.value = listOf(GattService("Establishing RF Link...", emptyList()))
            delay(1000)

            // Feed services
            val services = mutableListOf(
                GattService(
                    name = "Attendance Verification Protocol (0xFFE0)",
                    characteristics = listOf(
                        GattCharacteristic("Check-In State Characteristics", "Tx: Handshaking bytes")
                    )
                )
            )
            _gattServicesAndCharacteristics.value = services

            // If it is a virtual student, let's complete a simulated write check-in of student parameters
            if (device.name.contains("(Student)")) {
                // Determine ID parameter from custom category
                val studentId = device.category.removePrefix("Student ID: ").trim().ifEmpty { "N/A" }
                val studentName = device.name.removeSuffix(" (Student)")
                val payloadString = "$studentId;$studentName;Present"
                val responseBytes = payloadString.toByteArray(Charsets.UTF_8)

                delay(1200)
                _gattServicesAndCharacteristics.value = listOf(
                    GattService(
                        name = "Attendance Verification Protocol (0xFFE0)",
                        characteristics = listOf(
                            GattCharacteristic("Payload Hex Bytes Received", bytesToHex(responseBytes)),
                            GattCharacteristic("Verified Student Name", studentName),
                            GattCharacteristic("Verified ID Card", studentId),
                            GattCharacteristic("Verification Stamp", "APPROVED")
                        )
                    )
                )

                _detectedAttendance.emit(
                    AttendanceRecord(
                        studentName = studentName,
                        studentId = studentId,
                        deviceName = device.name,
                        macAddress = device.macAddress,
                        payloadBytesHex = bytesToHex(responseBytes),
                        status = "Checked-in via BLE"
                    )
                )
            } else {
                // Standard random characteristics
                delay(800)
                _gattServicesAndCharacteristics.value = listOf(
                    GattService(
                        name = "Custom Device Information",
                        characteristics = listOf(
                            GattCharacteristic("Mac Address Link", device.macAddress),
                            GattCharacteristic("Chirp Payload", "No attendance structure found")
                        )
                    )
                )
            }
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
            Log.d(tag, "RF Handshake Closed for ${currentConnected.name}")
        }
    }

    // --- UTILITIES ---

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X ".format(it) }.trim()
    }
}
