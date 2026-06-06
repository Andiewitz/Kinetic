package com.example.di

import androidx.room.Room
import com.example.ble.BleManager
import com.example.data.datastore.ScanPreferences
import com.example.data.db.AppDatabase
import com.example.data.repository.DeviceRepository
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Database
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "pulselink_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    // Dao
    single { get<AppDatabase>().deviceBookmarkDao() }

    // Repository
    single { DeviceRepository(get()) }

    // Prefs
    single { ScanPreferences(androidContext()) }

    // BleManager
    single { BleManager(androidContext()) }

    // ViewModels
    viewModel { com.example.ui.viewmodel.HomeViewModel(get(), get(), get()) }
}
