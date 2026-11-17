# Bluetooth Radar

Native Kotlin Android BLE observation app for a TCL T450H / Android 15 target. It performs a 3-minute BLE scanning window every 15 minutes, keeps a persistent local CSV evidence trail in `Downloads/BluetoothRadar/`, and supports manual start/stop plus restart recovery where Android permits.

## Scope

This project is a native Android application designed for:

- Bluetooth LE scanning using `BluetoothLeScanner` / `ScanCallback`
- Persistent local CSV evidence under `Downloads/BluetoothRadar/`
- Foreground service and boot recovery
- Permissions and Bluetooth state monitoring
- Local-first processing with no cloud dependency

## Important limitation

This repository contains a source-level Android project scaffold, not a verified APK produced in this environment. The code is intended for Android Studio and a physical device, but compilation and device validation remain unverified here because this session does not include the Android SDK toolchain or a connected TCL T450H device.

## Project structure

- `app/src/main/java/com/nathanlucas.bluerradar/` — Kotlin source code
- `app/src/main/AndroidManifest.xml` — permissions, service, receivers
- `app/src/main/res/layout/activity_main.xml` — dashboard UI
- `README.md` — usage instructions
- `BUILD_REPORT.md` — current status and test matrix

## Local CSV evidence

The app writes to the public Downloads directory at:

`/storage/emulated/0/Download/BluetoothRadar/`

Files created are:

- `bluetooth_observations.csv`
- `scan_sessions.csv`
- `devices.csv`
- `scan_errors.csv`

CSV writing is append-only by design. The app also exposes export and integrity functions.

## Installation

1. Install Android Studio.
2. Open this repository as a project.
3. Let Gradle sync.
4. Connect a physical Android 15 device (TCL T450H) or a compatible emulator.
5. Build the APK from Android Studio.
6. Grant Bluetooth, location, and notification permissions.
7. Start the app and use Start/Stop controls.

## Build and validation status

Implemented in source form: yes
Compiled here: no
APK generated here: no
Device tested here: no

This is intentionally documented transparently.
