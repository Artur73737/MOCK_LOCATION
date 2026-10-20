package com.locationspoofer.data.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.locationspoofer.data.RepositoryProvider
import com.locationspoofer.domain.model.SpoofState
import com.locationspoofer.domain.model.EarthLocationGenerator
import com.locationspoofer.domain.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocationSpoofService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private lateinit var repository: LocationRepository
    private lateinit var locationManager: LocationManager
    private val earthLocationGenerator = EarthLocationGenerator()
    private var lastRandomizedAt = 0L

    override fun onCreate() {
        super.onCreate()
        repository = RepositoryProvider.get(this)
        locationManager = getSystemService(LocationManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopInjecting()
                stopSelf()
            }
            else -> {
                startForeground(NOTIFICATION_ID, notification(repository.state.value))
                startInjecting()
            }
        }
        return START_STICKY
    }

    private fun startInjecting() {
        if (loopJob?.isActive == true) return
        loopJob = serviceScope.launch {
            while (isActive) {
                val state = repository.state.value
                if (state.active) {
                    try {
                        val currentState = randomizeIfDue(state)
                        inject(currentState)
                        updateNotification(currentState)
                    } catch (error: Throwable) {
                        repositoryStateError(error)
                    }
                }
                delay(1000L)
            }
        }
    }

    private fun randomizeIfDue(state: SpoofState): SpoofState {
        if (!state.randomizeEarth) {
            lastRandomizedAt = 0L
            return state
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastRandomizedAt < state.randomizeIntervalMillis) return state
        lastRandomizedAt = now
        val coordinates = earthLocationGenerator.next()
        repository.updateCoordinates(coordinates)
        return repository.state.value
    }

    private fun inject(state: SpoofState) {
        val provider = LocationManager.GPS_PROVIDER
        runCatching {
            locationManager.addTestProvider(
                provider,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE,
            )
        }
        locationManager.setTestProviderEnabled(provider, true)
        val location = Location(provider).apply {
            latitude = state.coordinates.latitude
            longitude = state.coordinates.longitude
            accuracy = 3f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                verticalAccuracyMeters = 3f
                speed = 0f
                bearing = 0f
            }
        }
        locationManager.setTestProviderLocation(provider, location)
    }

    private fun stopInjecting() {
        loopJob?.cancel()
        runCatching { locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false) }
        runCatching { locationManager.removeTestProvider(LocationManager.GPS_PROVIDER) }
    }

    private fun repositoryStateError(error: Throwable) {
        // Keep the service alive so the user can fix mock-location selection and retry.
        val message = error.message ?: error::class.java.simpleName
        Log.e(TAG, "Unable to inject mock location", error)
        repository.reportError(message)
    }

    private fun updateNotification(state: SpoofState) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
    }

    private fun notification(state: SpoofState): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = PendingIntent.getActivity(this, 1, launchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopIntent = Intent(this, LocationSpoofService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Location Spoofer active")
            .setContentText(state.coordinates.toString())
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Location spoof", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Foreground location spoofing status"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // START_STICKY lets Android recreate this service after the task is swiped away.
    }

    override fun onDestroy() {
        stopInjecting()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.locationspoofer.action.START"
        const val ACTION_STOP = "com.locationspoofer.action.STOP"
        private const val CHANNEL_ID = "location_spoof"
        private const val NOTIFICATION_ID = 4201
        private const val TAG = "LocationSpoofService"
    }
}
