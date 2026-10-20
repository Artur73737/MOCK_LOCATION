package com.locationspoofer.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.locationspoofer.data.location.LocationSpoofService
import com.locationspoofer.domain.model.Coordinates
import com.locationspoofer.domain.model.SpoofState
import com.locationspoofer.domain.model.RandomizerRegion
import com.locationspoofer.domain.repository.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationRepositoryImpl(context: Context) : LocationRepository {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(loadState())
    override val state: StateFlow<SpoofState> = _state.asStateFlow()

    override fun start(): Result<Unit> = runCatching {
        val intent = Intent(appContext, LocationSpoofService::class.java).setAction(LocationSpoofService.ACTION_START)
        ContextCompat.startForegroundService(appContext, intent)
        publish(_state.value.copy(active = true, lastError = null))
    }.onFailure { error -> publish(_state.value.copy(lastError = error.message ?: "Unable to start spoofing")) }

    override fun stop(): Result<Unit> = runCatching {
        appContext.startService(Intent(appContext, LocationSpoofService::class.java).setAction(LocationSpoofService.ACTION_STOP))
        publish(_state.value.copy(active = false, lastError = null))
    }.onFailure { error -> publish(_state.value.copy(lastError = error.message ?: "Unable to stop spoofing")) }

    override fun updateCoordinates(coordinates: Coordinates): Result<Unit> = runCatching {
        require(coordinates.isValid) { "Coordinates are outside the valid range" }
        publish(_state.value.copy(coordinates = coordinates, lastError = null))
    }.onFailure { error -> publish(_state.value.copy(lastError = error.message ?: "Invalid coordinates")) }

    override fun configureRandomizer(enabled: Boolean, intervalMillis: Long, region: RandomizerRegion): Result<Unit> = runCatching {
        require(intervalMillis >= MIN_RANDOMIZER_INTERVAL_MILLIS) { "Randomizer interval must be at least one second" }
        publish(_state.value.copy(randomizeEarth = enabled, randomizeIntervalMillis = intervalMillis, randomizerRegion = region, lastError = null))
    }.onFailure { error -> publish(_state.value.copy(lastError = error.message ?: "Invalid randomizer settings")) }

    override fun reportError(message: String) {
        publish(_state.value.copy(lastError = message))
    }

    override fun clearError() {
        publish(_state.value.copy(lastError = null))
    }

    private fun loadState(): SpoofState = SpoofState(
        active = preferences.getBoolean(KEY_ACTIVE, false),
        coordinates = Coordinates(
            preferences.getString(KEY_LATITUDE, "41.9028")!!.toDoubleOrNull() ?: 41.9028,
            preferences.getString(KEY_LONGITUDE, "12.4964")!!.toDoubleOrNull() ?: 12.4964,
        ),
        randomizeEarth = preferences.getBoolean(KEY_RANDOMIZE_EARTH, false),
        randomizeIntervalMillis = preferences.getLong(KEY_RANDOMIZE_INTERVAL_MILLIS, 60_000L)
            .coerceAtLeast(MIN_RANDOMIZER_INTERVAL_MILLIS),
        randomizerRegion = preferences.getString(KEY_RANDOMIZER_REGION, RandomizerRegion.WORLD.name)
            ?.let { value -> runCatching { RandomizerRegion.valueOf(value) }.getOrDefault(RandomizerRegion.WORLD) }
            ?: RandomizerRegion.WORLD,
    )

    private fun publish(state: SpoofState) {
        _state.value = state
        preferences.edit()
            .putBoolean(KEY_ACTIVE, state.active)
            .putString(KEY_LATITUDE, state.coordinates.latitude.toString())
            .putString(KEY_LONGITUDE, state.coordinates.longitude.toString())
            .putBoolean(KEY_RANDOMIZE_EARTH, state.randomizeEarth)
            .putLong(KEY_RANDOMIZE_INTERVAL_MILLIS, state.randomizeIntervalMillis)
            .putString(KEY_RANDOMIZER_REGION, state.randomizerRegion.name)
            .apply()
    }

    private companion object {
        const val PREFS = "location_spoofer_state"
        const val KEY_ACTIVE = "active"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_RANDOMIZE_EARTH = "randomize_earth"
        const val KEY_RANDOMIZE_INTERVAL_MILLIS = "randomize_interval_millis"
        const val KEY_RANDOMIZER_REGION = "randomizer_region"
        const val MIN_RANDOMIZER_INTERVAL_MILLIS = 1_000L
    }
}
