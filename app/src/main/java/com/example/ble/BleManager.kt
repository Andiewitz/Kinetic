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

        // Start scanning with a completely fresh, empty list of detected real hardware
        _scannedDevices.value = emptyMap()

        // Setup real BLE Scan if possible
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
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        _isScanning.value = false

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

    // --- EXCHANGING BYTES AND REAL BLE CONNECTIONS ---

    private var activeGatt: BluetoothGatt? = null

    private val clientGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d(tag, "Client connection state change: status $status, state $newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(tag, "Connected to remote GATT server. Discovering services...")
                gatt?.discoverServices()
                
                val address = gatt?.device?.address ?: ""
                _scannedDevices.update { current ->
                    current.mapValues { (_, d) ->
                        if (d.macAddress == address) d.copy(isConnected = true) else d
                    }
                }
                _connectedDevice.value?.let { currentConnected ->
                    if (currentConnected.macAddress == address) {
                        _connectedDevice.value = currentConnected.copy(isConnected = true)
                    }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(tag, "Disconnected from remote GATT server")
                val address = gatt?.device?.address ?: ""
                _connectedDevice.value?.let { currentConnected ->
                    if (currentConnected.macAddress == address) {
                        disconnectDevice()
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                Log.d(tag, "GATT Services discovered successfully")
                val discovered = mutableListOf<com.example.ble.GattService>()
                
                for (service in gatt.services) {
                    val sUuid = service.uuid.toString().uppercase()
                    
                    val sName = when {
                        sUuid.startsWith("0000FFE0") -> "Attendance Verification (0xFFE0)"
                        sUuid.startsWith("0000180F") -> "Battery Service (0x180F)"
                        sUuid.startsWith("0000180A") -> "Device Information (0x180A)"
                        sUuid.startsWith("0000180D") -> "Heart Rate (0x180D)"
                        else -> "Service: ${sUuid.substring(0, 8)}"
                    }

                    val characteristicsList = mutableListOf<com.example.ble.GattCharacteristic>()
                    for (char in service.characteristics) {
                        val cUuid = char.uuid.toString().uppercase()
                        val cName = "Characteristic: ${cUuid.substring(0, 8)}"
                        
                        var cValue = "No read permission"
                        if ((char.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                            cValue = "Read Available"
                            gatt.readCharacteristic(char)
                        }
                        
                        characteristicsList.add(com.example.ble.GattCharacteristic(cName, cValue))
                    }
                    
                    discovered.add(com.example.ble.GattService(sName, characteristicsList))
                }
                
                _gattServicesAndCharacteristics.value = discovered
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val cUuid = characteristic.uuid.toString().uppercase()
                val decodedString = String(value, Charsets.UTF_8)
                Log.d(tag, "Read Characteristic $cUuid value: $decodedString")
                
                _gattServicesAndCharacteristics.update { currentServices ->
                    currentServices.map { service ->
                        val updatedChars = service.characteristics.map { c ->
                            if (c.uuidName.contains(cUuid.substring(0, 8))) {
                                c.copy(value = decodedString)
                            } else {
                                c
                            }
                        }
                        service.copy(characteristics = updatedChars)
                    }
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            @Suppress("DEPRECATION")
            super.onCharacteristicRead(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                val value = characteristic.value ?: byteArrayOf()
                val cUuid = characteristic.uuid.toString().uppercase()
                val decodedString = String(value, Charsets.UTF_8)
                
                _gattServicesAndCharacteristics.update { currentServices ->
                    currentServices.map { service ->
                        val updatedChars = service.characteristics.map { c ->
                            if (c.uuidName.contains(cUuid.substring(0, 8))) {
                                c.copy(value = decodedString)
                            } else {
                                c
                            }
                        }
                        service.copy(characteristics = updatedChars)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(device: BleDevice) {
        scope.launch {
            _scannedDevices.update { current ->
                current.mapValues { (_, d) ->
                    if (d.macAddress == device.macAddress) d.copy(isConnected = true) else d
                }
            }
            _connectedDevice.value = device.copy(isConnected = true)
            Log.d(tag, "Handshaking with ${device.name}")

            _gattServicesAndCharacteristics.value = listOf(com.example.ble.GattService("Establishing RF Link...", emptyList()))

            try {
                // Disconnect any active GATT
                activeGatt?.disconnect()
                activeGatt?.close()
                activeGatt = null

                val remoteDevice = bluetoothAdapter?.getRemoteDevice(device.macAddress)
                activeGatt = remoteDevice?.connectGatt(context, false, clientGattCallback)
            } catch (e: Exception) {
                Log.e(tag, "Error initiating real connectGatt connection: ${e.message}")
                _gattServicesAndCharacteristics.value = listOf(
                    com.example.ble.GattService("Connection Error", listOf(
                        com.example.ble.GattCharacteristic("Error Message", e.message ?: "Failed to initiate link")
                    ))
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice() {
        val currentConnected = _connectedDevice.value ?: return
        scope.launch {
            try {
                activeGatt?.disconnect()
                activeGatt?.close()
                activeGatt = null
            } catch (e: Exception) {
                Log.e(tag, "Error closing GATT link: ${e.message}")
            }

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
