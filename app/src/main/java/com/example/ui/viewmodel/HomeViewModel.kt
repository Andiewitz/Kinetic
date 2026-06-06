package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ble.BleDevice
import com.example.ble.BleManager
import com.example.data.datastore.ScanPreferences
import com.example.data.db.DeviceBookmark
import com.example.data.repository.DeviceRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import org.orbitmvi.orbit.syntax.simple.intent
import org.orbitmvi.orbit.syntax.simple.postSideEffect
import org.orbitmvi.orbit.syntax.simple.reduce

data class HomeState(
    val rawDevices: List<BleDevice> = emptyList(),
    val filteredDevices: List<BleDevice> = emptyList(),
    val isScanning: Boolean = false,
    val rssiFilter: Int = -100,
    val showUnidentified: Boolean = true,
    val searchQuery: String = "",
    val bookmarks: List<DeviceBookmark> = emptyList()
)

sealed interface HomeSideEffect {
    data class ShowToast(val message: String) : HomeSideEffect
    data class NavigateToDetail(val deviceMacAddress: String) : HomeSideEffect
}

class HomeViewModel(
    private val bleManager: BleManager,
    private val deviceRepository: DeviceRepository,
    private val scanPreferences: ScanPreferences
) : ViewModel(), ContainerHost<HomeState, HomeSideEffect> {

    override val container: Container<HomeState, HomeSideEffect> = container(HomeState())

    init {
        observePrefsAndData()
    }

    private fun observePrefsAndData() = intent {
        // Collect Bookmarks from Room DB
        viewModelScope.launch {
            deviceRepository.allBookmarks.collectLatest { bookmarksList ->
                reduce { state.copy(bookmarks = bookmarksList) }
                filterDevices()
            }
        }

        // Collect Devices from BLE manager & state of scanning
        viewModelScope.launch {
            bleManager.isScanning.collectLatest { isScanning ->
                reduce { state.copy(isScanning = isScanning) }
            }
        }

        viewModelScope.launch {
            bleManager.scannedDevices.collectLatest { devices ->
                reduce { state.copy(rawDevices = devices) }
                filterDevices()
            }
        }

        // Collect ScanPreferences from DataStore
        viewModelScope.launch {
            combine(
                scanPreferences.rssiThresholdFlow,
                scanPreferences.showUnidentifiedFlow
            ) { rssi, showUn ->
                Pair(rssi, showUn)
            }.collectLatest { (rssi, showUn) ->
                reduce { state.copy(rssiFilter = rssi, showUnidentified = showUn) }
                filterDevices()
            }
        }
    }

    fun startScanning() = intent {
        bleManager.startScan()
        postSideEffect(HomeSideEffect.ShowToast("Bluetooth scan initiated"))
    }

    fun stopScanning() = intent {
        bleManager.stopScan()
        postSideEffect(HomeSideEffect.ShowToast("Bluetooth scan stopped"))
    }

    fun updateSearchQuery(query: String) = intent {
        reduce { state.copy(searchQuery = query) }
        filterDevices()
    }

    fun updateRssiFilter(rssi: Int) = intent {
        reduce { state.copy(rssiFilter = rssi) }
        viewModelScope.launch {
            scanPreferences.saveRssiThreshold(rssi)
        }
        filterDevices()
    }

    private fun filterDevices() = intent {
        val searchLower = state.searchQuery.lowercase()
        val filtered = state.rawDevices.filter { device ->
            val matchesRssi = device.rssi >= state.rssiFilter
            val matchesUnidentified = state.showUnidentified || device.name != "Unknown"
            val matchesSearch = device.name.lowercase().contains(searchLower) ||
                    device.macAddress.lowercase().contains(searchLower)

            matchesRssi && matchesUnidentified && matchesSearch
        }
        reduce { state.copy(filteredDevices = filtered) }
    }

    fun saveBookmark(mac: String, customName: String, notes: String, category: String) = intent {
        deviceRepository.saveBookmark(mac, customName, notes, category)
        postSideEffect(HomeSideEffect.ShowToast("Saved settings for $customName"))
    }

    fun deleteBookmark(mac: String) = intent {
        deviceRepository.removeBookmark(mac)
        postSideEffect(HomeSideEffect.ShowToast("Cleared notes/tags successfully"))
    }
}
