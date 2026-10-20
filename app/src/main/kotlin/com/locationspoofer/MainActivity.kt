package com.locationspoofer

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.locationspoofer.data.RepositoryProvider
import com.locationspoofer.domain.model.Coordinates
import com.locationspoofer.domain.model.RandomizerRegion
import com.locationspoofer.domain.repository.LocationRepository
import com.locationspoofer.domain.usecase.StartSpoofUseCase
import com.locationspoofer.domain.usecase.StopSpoofUseCase
import com.locationspoofer.domain.usecase.UpdateCoordinatesUseCase
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LocationSpooferApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSpooferApp() {
    val context = LocalContext.current
    val repository = remember { RepositoryProvider.get(context) }
    val state by repository.state.collectAsState()
    val start = remember { StartSpoofUseCase(repository) }
    val stop = remember { StopSpoofUseCase(repository) }
    val update = remember { UpdateCoordinatesUseCase(repository) }
    var showMap by remember { mutableStateOf(false) }
    var latitude by remember(state.coordinates.latitude) { mutableStateOf(formatCoordinate(state.coordinates.latitude)) }
    var longitude by remember(state.coordinates.longitude) { mutableStateOf(formatCoordinate(state.coordinates.longitude)) }
    var permissionRequestPending by remember { mutableStateOf(false) }
    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionRequestPending = false
        if (result.values.all { it }) start()
    }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text(if (showMap) "Choose location" else "Location Spoofer") }) },
        ) { padding ->
            if (showMap) {
                    MapScreen(
                    coordinates = state.coordinates,
                    onCoordinatesChanged = { coordinates ->
                        update(coordinates)
                        latitude = formatCoordinate(coordinates.latitude)
                        longitude = formatCoordinate(coordinates.longitude)
                    },
                    onApply = {
                        update(state.coordinates)
                        showMap = false
                    },
                    modifier = Modifier.padding(padding),
                )
            } else {
                SpoofScreen(
                    state = state,
                    latitude = latitude,
                    longitude = longitude,
                    onLatitudeChanged = { latitude = it },
                    onLongitudeChanged = { longitude = it },
                    onMap = { showMap = true },
                    onApply = {
                        val parsed = Coordinates(latitude.replace(',', '.').toDoubleOrNull() ?: Double.NaN, longitude.replace(',', '.').toDoubleOrNull() ?: Double.NaN)
                        update(parsed)
                    },
                    onStart = {
                        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                            start()
                        } else {
                            permissionRequestPending = true
                            permissionLauncher.launch(permissions.toTypedArray())
                        }
                    },
                    onStop = { stop() },
                    onRandomizerChanged = { enabled, intervalMillis, region -> repository.configureRandomizer(enabled, intervalMillis, region) },
                    mockLocationEnabled = isMockLocationSelected(context),
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    LaunchedEffect(permissionRequestPending) { if (!permissionRequestPending) repository.clearError() }
}

@Composable
private fun SpoofScreen(
    state: com.locationspoofer.domain.model.SpoofState,
    latitude: String,
    longitude: String,
    onLatitudeChanged: (String) -> Unit,
    onLongitudeChanged: (String) -> Unit,
    onMap: () -> Unit,
    onApply: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRandomizerChanged: (Boolean, Long, RandomizerRegion) -> Unit,
    mockLocationEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var randomizerIntervalValue by remember(state.randomizeIntervalMillis) {
        mutableStateOf(if (state.randomizeIntervalMillis % 60_000L == 0L) (state.randomizeIntervalMillis / 60_000L).toString() else (state.randomizeIntervalMillis / 1_000L).toString())
    }
    var randomizerUnit by remember(state.randomizeIntervalMillis) {
        mutableStateOf(if (state.randomizeIntervalMillis % 60_000L == 0L) RandomizerUnit.MINUTES else RandomizerUnit.SECONDS)
    }
    var randomizerMenuExpanded by remember { mutableStateOf(false) }
    var regionMenuExpanded by remember { mutableStateOf(false) }
    var randomizerRegion by remember(state.randomizerRegion) { mutableStateOf(state.randomizerRegion) }

    fun updateRandomizerInterval(value: String = randomizerIntervalValue, unit: RandomizerUnit = randomizerUnit) {
        value.toLongOrNull()?.takeIf { it >= 1L }?.let { onRandomizerChanged(state.randomizeEarth, it * unit.multiplierMillis, randomizerRegion) }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.active) "Spoof active" else "Spoof stopped", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(state.coordinates.toString(), style = MaterialTheme.typography.bodyLarge)
                Text(if (state.active) "The foreground service sends a GPS location every second." else "Start the service to inject this location into Android.")
            }
        }
        if (!mockLocationEnabled) {
            Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Text("Select Location Spoofer in Developer options > Select mock location app before starting.")
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(latitude, onLatitudeChanged, label = { Text("Latitude") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(longitude, onLongitudeChanged, label = { Text("Longitude") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onApply, modifier = Modifier.weight(1f)) { Text("Apply") }
            OutlinedButton(onClick = onMap, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Map")
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Randomize Earth location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Generate a new land coordinate at a fixed interval.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = state.randomizeEarth,
                        onCheckedChange = { onRandomizerChanged(it, state.randomizeIntervalMillis, randomizerRegion) },
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = randomizerIntervalValue,
                        onValueChange = { value ->
                            randomizerIntervalValue = value.filter(Char::isDigit).take(6)
                            updateRandomizerInterval(randomizerIntervalValue)
                        },
                        label = { Text("Update every") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = state.randomizeEarth,
                    )
                    Box {
                        OutlinedButton(enabled = state.randomizeEarth, onClick = { randomizerMenuExpanded = true }) { Text(randomizerUnit.label) }
                        DropdownMenu(expanded = randomizerMenuExpanded, onDismissRequest = { randomizerMenuExpanded = false }) {
                            RandomizerUnit.entries.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit.label) },
                                    onClick = {
                                        randomizerUnit = unit
                                        randomizerMenuExpanded = false
                                        updateRandomizerInterval(unit = unit)
                                    },
                                )
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Region", style = MaterialTheme.typography.bodyLarge)
                    Box {
                        OutlinedButton(enabled = state.randomizeEarth, onClick = { regionMenuExpanded = true }) { Text(randomizerRegion.label) }
                        DropdownMenu(expanded = regionMenuExpanded, onDismissRequest = { regionMenuExpanded = false }) {
                            RandomizerRegion.entries.forEach { region ->
                                DropdownMenuItem(
                                    text = { Text(region.label) },
                                    onClick = {
                                        randomizerRegion = region
                                        regionMenuExpanded = false
                                        updateRandomizerInterval()
                                    },
                                )
                            }
                        }
                    }
                }
                Text("Only inland catalogue points are used. The first random coordinate is generated when spoofing starts. Minimum interval: 1 second.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(onClick = if (state.active) onStop else onStart, modifier = Modifier.fillMaxWidth()) {
            Icon(if (state.active) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.size(8.dp)); Text(if (state.active) "Stop spoof" else "Start spoof")
        }
        state.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Real system integration", style = MaterialTheme.typography.titleMedium)
        Text("This app uses Android's official GPS test provider. Root is not required, but Android must be configured to allow this app to provide mock locations.")
    }
}

private enum class RandomizerUnit(val label: String, val multiplierMillis: Long) {
    SECONDS("seconds", 1_000L),
    MINUTES("minutes", 60_000L),
}

@Composable
private fun MapScreen(
    coordinates: Coordinates,
    onCoordinatesChanged: (Coordinates) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mapView: NativeMapView? by remember { mutableStateOf(null) }
    Column(modifier.fillMaxSize().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth()
                .zIndex(1f)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Pan the map, then apply the marker", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { mapView?.zoomOut() }, content = { Text("−") })
                OutlinedButton(onClick = { mapView?.zoomIn() }, content = { Text("+") })
                Button(onClick = onApply) { Text("Apply location") }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    NativeMapView(context).also { view ->
                        mapView = view
                        view.onCoordinatesChanged = onCoordinatesChanged
                        view.setCenter(coordinates)
                    }
                },
                update = { view ->
                    mapView = view
                    view.onCoordinatesChanged = onCoordinatesChanged
                    view.setCenter(coordinates)
                },
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth().zIndex(1f),
        ) { Text(coordinates.toString(), Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) }
    }
}

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

private fun isMockLocationSelected(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java)
    val mode = if (Build.VERSION.SDK_INT >= 29) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, android.os.Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION") appOps.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, android.os.Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
