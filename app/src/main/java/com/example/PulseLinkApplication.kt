package com.example

import android.app.Application
import com.example.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class PulseLinkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.INFO)
            androidContext(this@PulseLinkApplication)
            modules(appModule)
        }
    }
}
