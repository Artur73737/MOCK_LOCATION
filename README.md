# Location Spoofer for Android

Location Spoofer is a native Kotlin Android application that publishes a user-selected coordinate through Android's official mock GPS provider. It does not require root. Android must be configured to trust the app as the selected mock-location provider.

## Features

- Real `LocationManager` test-provider injection through a persistent foreground service.
- One GPS update per second, including while the screen is off or the app task is removed.
- Native Jetpack Compose UI in English.
- Native Kotlin `View` map renderer using OpenStreetMap raster tiles. The map has no WebView dependency; a visible grid remains available when the device is offline.
- Pan and zoom the map, edit latitude/longitude directly, and apply coordinates immediately.
- Coordinate state persisted in app storage and restored after process recreation.
- Clear runtime status and error reporting when Android rejects mock locations.

## Requirements

- Android 8.0 (API 26) or newer.
- A device with Developer options enabled.
- Network access for OpenStreetMap tiles. Location injection itself does not require network access.

## Build and test

The project uses Gradle 8.14.3, Android Gradle Plugin 8.7.3, Kotlin 2.2.10, and Java 17.

```powershell
gradle.bat --no-daemon testDebugUnitTest assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

For a locally installable release build, use `gradle.bat --no-daemon assembleRelease`. This workspace release variant is signed with the standard debug keystore so it can be installed directly with ADB; replace that signing configuration with a private release key before distribution.

## Install and configure a device

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.locationspoofer/.MainActivity
```

On the device:

1. Open **Settings > System > Developer options**.
2. Open **Select mock location app**.
3. Choose **Location Spoofer**.
4. Grant location and notification permissions when the app asks.
5. Set a coordinate, open **Map**, pan the marker, and press **Apply location**.
6. Press **Start spoof**.

The foreground notification is intentionally ongoing and has a **Stop** action. Stop spoofing from the app or from that notification before selecting another mock-location provider.

## Architecture

The project is split into three Gradle modules:

- `domain`: coordinate model, state model, repository contract, and use cases with no Android UI code.
- `data`: persistent repository and `LocationSpoofService`, including the real `LocationManager` test-provider implementation.
- `app`: Compose presentation, permission flow, native map renderer, and application entry point.

The repository is the single source of truth. The foreground service reads the same state and publishes the current coordinate to Android every second. `START_STICKY` allows Android to recreate the service after process pressure while the persisted active state is still enabled.

## OpenStreetMap attribution

The native map downloads tiles from `https://tile.openstreetmap.org`. The map displays the required attribution text **© OpenStreetMap contributors**. Follow the OpenStreetMap tile usage policy when distributing or scaling this application.

## Limitations

Android only accepts test-provider locations when this application is selected in Developer options. That is an operating-system security requirement and cannot be bypassed by the app. If the device is offline, the native map stays usable for panning and coordinate selection but displays the built-in grid until tiles can be downloaded.
