package com.locationspoofer

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.ContextCompat
import com.locationspoofer.data.RepositoryProvider
import com.locationspoofer.domain.model.Coordinates
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
            topBar = { TopAppBar(title = { Text(if (showMap) "Scegli posizione" else "Location Spoofer") }) },
        ) { padding ->
            if (showMap) {
                MapScreen(
                    coordinates = state.coordinates,
                    onCoordinatesChanged = { coordinates ->
                        update(coordinates)
                        latitude = formatCoordinate(coordinates.latitude)
                        longitude = formatCoordinate(coordinates.longitude)
                    },
                    onBack = { showMap = false },
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
    mockLocationEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.active) "Spoof attivo" else "Spoof fermo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(state.coordinates.toString(), style = MaterialTheme.typography.bodyLarge)
                Text(if (state.active) "Il servizio invia una posizione GPS ogni secondo." else "Avvia il servizio per iniettare la posizione nel sistema.")
            }
        }
        if (!mockLocationEnabled) {
            Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Text("Seleziona Location Spoofer in Opzioni sviluppatore > App di posizione fittizia prima di avviare.")
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(latitude, onLatitudeChanged, label = { Text("Latitudine") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(longitude, onLongitudeChanged, label = { Text("Longitudine") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onApply, modifier = Modifier.weight(1f)) { Text("Imposta") }
            OutlinedButton(onClick = onMap, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Mappa")
            }
        }
        Button(onClick = if (state.active) onStop else onStart, modifier = Modifier.fillMaxWidth()) {
            Icon(if (state.active) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.size(8.dp)); Text(if (state.active) "Ferma spoof" else "Avvia spoof")
        }
        state.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Uso reale", style = MaterialTheme.typography.titleMedium)
        Text("Questa app usa il test provider GPS ufficiale di Android. Non richiede root, ma Android deve essere configurato per consentire a questa app di fornire posizioni fittizie.")
    }
}

@Composable
private fun MapScreen(
    coordinates: Coordinates,
    onCoordinatesChanged: (Coordinates) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Trascina il marker", style = MaterialTheme.typography.titleMedium)
            FilterChip(selected = false, onClick = onBack, label = { Text("Fine") })
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        addJavascriptInterface(object {
                            @JavascriptInterface fun onLocation(lat: Double, lon: Double) {
                                (context as? android.app.Activity)?.runOnUiThread { onCoordinatesChanged(Coordinates(lat, lon)) }
                            }
                        }, "AndroidBridge")
                        loadDataWithBaseURL("https://localhost/", mapHtml(coordinates), "text/html", "UTF-8", null)
                    }
                },
                update = { view -> view.evaluateJavascript("window.setMarker(${coordinates.latitude}, ${coordinates.longitude});", null) },
            )
        }
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) { Text(coordinates.toString(), Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) }
    }
}

private fun mapHtml(coordinates: Coordinates): String = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1.0"><link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/><style>html,body,#map{height:100%;margin:0} .leaflet-control-attribution{font-size:10px}</style></head>
<body><div id="map"></div><script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><script>
let map=L.map('map').setView([${coordinates.latitude},${coordinates.longitude}],5);L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);let marker=L.marker([${coordinates.latitude},${coordinates.longitude}],{draggable:true}).addTo(map);marker.on('dragend',()=>{let p=marker.getLatLng();AndroidBridge.onLocation(p.lat,p.lng)});window.setMarker=(lat,lon)=>{marker.setLatLng([lat,lon]);map.panTo([lat,lon])};
</script></body></html>
""".trimIndent()

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
