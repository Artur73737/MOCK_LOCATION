package com.locationspoofer.data

import android.content.Context
import com.locationspoofer.domain.repository.LocationRepository

object RepositoryProvider {
    @Volatile private var instance: LocationRepository? = null

    fun get(context: Context): LocationRepository = instance ?: synchronized(this) {
        instance ?: LocationRepositoryImpl(context).also { instance = it }
    }
}
