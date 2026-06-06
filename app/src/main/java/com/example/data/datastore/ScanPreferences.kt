package com.example.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scan_preferences")

class ScanPreferences(private val context: Context) {

    companion object {
        val SCAN_DURATION_SEC = intPreferencesKey("scan_duration_sec")
        val RSSI_THRESHOLD = intPreferencesKey("rssi_threshold")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val SHOW_UNIDENTIFIED = booleanPreferencesKey("show_unidentified")
    }

    val scanDurationFlow: Flow<Int> = context.dataStore.data { preferences ->
        preferences[SCAN_DURATION_SEC] ?: 10
    }

    val rssiThresholdFlow: Flow<Int> = context.dataStore.data { preferences ->
        preferences[RSSI_THRESHOLD] ?: -100
    }

    val autoConnectFlow: Flow<Boolean> = context.dataStore.data { preferences ->
        preferences[AUTO_CONNECT] ?: false
    }

    val showUnidentifiedFlow: Flow<Boolean> = context.dataStore.data { preferences ->
        preferences[SHOW_UNIDENTIFIED] ?: true
    }

    suspend fun saveScanDuration(sec: Int) {
        context.dataStore.edit { preferences ->
            preferences[SCAN_DURATION_SEC] = sec
        }
    }

    suspend fun saveRssiThreshold(dbm: Int) {
        context.dataStore.edit { preferences ->
            preferences[RSSI_THRESHOLD] = dbm
        }
    }

    suspend fun saveAutoConnect(enable: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CONNECT] = enable
        }
    }

    suspend fun saveShowUnidentified(enable: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_UNIDENTIFIED] = enable
        }
    }

    // Helper: standard Flow wrapper
    private inline fun <T> DataStore<Preferences>.data(crossinline transform: suspend (value: Preferences) -> T): Flow<T> {
        return this.data.map { transform(it) }
    }
}
