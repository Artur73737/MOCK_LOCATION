package com.locationspoofer

import android.app.Application
import com.locationspoofer.data.RepositoryProvider

class LocationSpooferApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RepositoryProvider.get(this)
    }
}
