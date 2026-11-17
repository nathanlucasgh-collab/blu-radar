package com.nathanlucas.bluerradar

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var exportButton: Button
    private lateinit var batteryButton: Button
    private lateinit var statusText: TextView
    private lateinit var countdownText: TextView
    private lateinit var nextScanText: TextView
    private lateinit var detectedText: TextView
    private lateinit var deviceListText: TextView
    private lateinit var scanHistoryText: TextView
    private lateinit var storageStatusText: TextView
    private lateinit var permissionStatusText: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> refreshStatus() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val active = intent.getBooleanExtra(RadarService.EXTRA_ACTIVE, false)
            val state = intent.getStringExtra(RadarService.EXTRA_STATUS) ?: "Idle"
            val countdownMs = intent.getLongExtra(RadarService.EXTRA_SCAN_REMAINING_MS, 0L)
            val nextMs = intent.getLongExtra(RadarService.EXTRA_NEXT_SCAN_MS, 0L)
            val count = intent.getIntExtra(RadarService.EXTRA_DETECTED_DEVICE_COUNT, 0)
            val devices = intent.getStringExtra(RadarService.EXTRA_DEVICE_SUMMARY) ?: "No devices seen yet."
            val history = intent.getStringExtra(RadarService.EXTRA_SCAN_HISTORY) ?: "Scan history unavailable."

            statusText.text = "Status: $state"
            countdownText.text = "Scan countdown: ${formatDuration(countdownMs)}"
            nextScanText.text = "Next scan: ${formatDuration(nextMs)}"
            detectedText.text = "Detected device count: $count"
            deviceListText.text = devices
            scanHistoryText.text = history
            startButton.isEnabled = !active
            stopButton.isEnabled = active
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        exportButton = findViewById(R.id.exportButton)
        batteryButton = findViewById(R.id.batteryButton)
        statusText = findViewById(R.id.statusText)
        countdownText = findViewById(R.id.countdownText)
        nextScanText = findViewById(R.id.nextScanText)
        detectedText = findViewById(R.id.detectedText)
        deviceListText = findViewById(R.id.deviceListText)
        scanHistoryText = findViewById(R.id.scanHistoryText)
        storageStatusText = findViewById(R.id.storageStatusText)
        permissionStatusText = findViewById(R.id.permissionStatusText)

        startButton.setOnClickListener {
            if (!hasRequiredPermissions()) {
                requestRequiredPermissions()
                return@setOnClickListener
            }
            RadarService.start(this)
        }

        stopButton.setOnClickListener {
            RadarService.stop(this)
        }

        exportButton.setOnClickListener {
            val exportFile = CsvStorage.exportBundle()
            Toast.makeText(this, "Export written: ${exportFile.absolutePath}", Toast.LENGTH_LONG).show()
            CsvStorage.verifyIntegrity()
        }

        batteryButton.setOnClickListener {
            PowerUtils.openBatteryOptimizationSettings(this)
        }

        registerReceiver(receiver, IntentFilter(RadarService.ACTION_STATE_CHANGED))
        refreshStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    private fun refreshStatus() {
        val permissionsGranted = hasRequiredPermissions()
        val bluetoothEnabled = getBluetoothAdapter()?.isEnabled == true
        val storageOk = CsvStorage.isWritable()

        permissionStatusText.text = "Permission status: ${if (permissionsGranted) "granted" else "missing"}"
        storageStatusText.text = "Storage status: ${if (storageOk) "ready" else "unavailable"}"
        statusText.text = "Status: ${if (bluetoothEnabled && Preferences.isEnabled(this)) "Running" else "Idle"}"
        startButton.isEnabled = !Preferences.isEnabled(this) && permissionsGranted && bluetoothEnabled
        stopButton.isEnabled = Preferences.isEnabled(this)
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun hasRequiredPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scan = checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
            val connect = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            val location = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            return scan == PackageManager.PERMISSION_GRANTED &&
                connect == PackageManager.PERMISSION_GRANTED &&
                location == PackageManager.PERMISSION_GRANTED
        }

        val location = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        return location == PackageManager.PERMISSION_GRANTED
    }

    private fun getBluetoothAdapter(): BluetoothAdapter? {
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        return bluetoothManager?.adapter
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "--"
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}
