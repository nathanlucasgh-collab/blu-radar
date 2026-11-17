package com.nathanlucas.bluerradar

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class RadarService : Service() {

    companion object {
        const val ACTION_START = "com.nathanlucas.bluerradar.START"
        const val ACTION_STOP = "com.nathanlucas.bluerradar.STOP"
        const val ACTION_STATE_CHANGED = "com.nathanlucas.bluerradar.STATE_CHANGED"

        const val EXTRA_ACTIVE = "EXTRA_ACTIVE"
        const val EXTRA_STATUS = "EXTRA_STATUS"
        const val EXTRA_SCAN_REMAINING_MS = "EXTRA_SCAN_REMAINING_MS"
        const val EXTRA_NEXT_SCAN_MS = "EXTRA_NEXT_SCAN_MS"
        const val EXTRA_DETECTED_DEVICE_COUNT = "EXTRA_DETECTED_DEVICE_COUNT"
        const val EXTRA_DEVICE_SUMMARY = "EXTRA_DEVICE_SUMMARY"
        const val EXTRA_SCAN_HISTORY = "EXTRA_SCAN_HISTORY"

        private const val CHANNEL_ID = "bluetooth_radar_service"
        private const val NOTIFICATION_ID = 1001
        private const val SCAN_WINDOW_MS = 3 * 60 * 1000L
        private const val IDLE_WINDOW_MS = 12 * 60 * 1000L

        fun start(context: Context) {
            Preferences.setEnabled(context, true)
            ContextCompat.startForegroundService(context, Intent(context, RadarService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            Preferences.setEnabled(context, false)
            ContextCompat.startForegroundService(context, Intent(context, RadarService::class.java).setAction(ACTION_STOP))
        }
    }

    private lateinit var notificationManager: NotificationManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val handler = Handler(Looper.getMainLooper())

    private var isScanning = false
    private var scanEndMs = 0L
    private var nextScanMs = 0L
    private var activeSessionId: String? = null
    private val deviceSummary = mutableListOf<String>()
    private val scanHistory = mutableListOf<String>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            val scanRecord = result.scanRecord
            val deviceName = device?.name ?: "unknown"
            val bleAddress = device?.address ?: "unknown"
            val firstSeen = dateUtcNow()
            val record = ObservationRecord(
                timestampUtc = firstSeen,
                sessionId = activeSessionId ?: "unknown",
                deviceName = deviceName,
                bleAddress = bleAddress,
                rssiDbm = result.rssi,
                manufacturerId = scanRecord?.manufacturerSpecificData?.keyAt(0)?.toString() ?: "",
                manufacturerDataHex = scanRecord?.manufacturerSpecificData?.get(0)?.let { toHex(it) } ?: "",
                serviceUuids = scanRecord?.serviceUuids?.joinToString(",") { it.uuid.toString() } ?: "",
                serviceDataHex = scanRecord?.serviceData?.entries?.joinToString(",") { "${it.key.uuid}:${toHex(it.value)}" } ?: "",
                txPowerDbm = scanRecord?.txPowerLevel?.toString() ?: "",
                connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.isConnectable.toString() else "false",
                firstSeenUtc = firstSeen,
                lastSeenUtc = firstSeen
            )
            CsvStorage.appendObservation(record)
            deviceSummary.add("$deviceName [$bleAddress] @ ${result.rssi} dBm")
            sendStatusBroadcast()
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (result in results) {
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val message = "BLE scan failed: errorCode=$errorCode"
            CsvStorage.appendError(activeSessionId ?: "unknown", "scan_failed", message, "ERROR")
            stopScanning()
            scheduleNextScan()
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startScanCycle()
            ACTION_STOP -> stopScanCycle()
            else -> {
                if (Preferences.isEnabled(this)) {
                    startScanCycle()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startScanCycle() {
        val sessionId = UUID.randomUUID().toString()
        activeSessionId = sessionId
        deviceSummary.clear()
        scanHistory.add("Session $sessionId started @ ${dateUtcNow()}")
        nextScanMs = System.currentTimeMillis() + SCAN_WINDOW_MS
        startScanWindow()
        sendStatusBroadcast()
    }

    private fun stopScanCycle() {
        stopScanning()
        Preferences.setEnabled(this, false)
        stopForeground(true)
        stopSelf()
        sendStatusBroadcast()
    }

    private fun startScanWindow() {
        if (bluetoothAdapter?.isEnabled != true) {
            CsvStorage.appendError(activeSessionId ?: "unknown", "bluetooth_disabled", "Bluetooth is disabled; waiting for state change.", "WARN")
            scheduleNextScan()
            return
        }

        if (isScanning) return

        isScanning = true
        scanEndMs = System.currentTimeMillis() + SCAN_WINDOW_MS
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val filters = mutableListOf<ScanFilter>()
        bluetoothLeScanner?.startScan(filters, settings, scanCallback)

        val sessionId = activeSessionId ?: UUID.randomUUID().toString()
        Preferences.setLastSessionId(this, sessionId)
        Preferences.setLastScanStart(this, System.currentTimeMillis())

        val sessionRecord = SessionRecord(
            sessionId = sessionId,
            timestampUtc = dateUtcNow(),
            durationMs = SCAN_WINDOW_MS,
            deviceCount = 0,
            scanStartedUtc = dateUtcNow(),
            scanEndedUtc = dateUtcNow(),
            status = "ACTIVE"
        )
        CsvStorage.appendSession(sessionRecord)
        startForeground(NOTIFICATION_ID, buildNotification("Scanning BLE devices", "3-minute scan window active"))
        handler.postDelayed({ stopScanning() }, SCAN_WINDOW_MS)
        sendStatusBroadcast()
    }

    private fun stopScanning() {
        if (!isScanning) return
        bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        val sessionId = activeSessionId ?: "unknown"
        scanHistory.add("Session $sessionId completed @ ${dateUtcNow()}")
        CsvStorage.appendError(sessionId, "scan_window_closed", "Scan window ended normally.", "INFO")
        scheduleNextScan()
        sendStatusBroadcast()
    }

    private fun scheduleNextScan() {
        nextScanMs = System.currentTimeMillis() + IDLE_WINDOW_MS
        val intent = Intent(this, RadarService::class.java).setAction(ACTION_START)
        val pendingIntent = PendingIntent.getService(
            this,
            1,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )
        val alarmManager = getSystemService(AlarmManager::class.java)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + IDLE_WINDOW_MS,
            pendingIntent
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Bluetooth Radar",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, message: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun sendStatusBroadcast() {
        val intent = Intent(ACTION_STATE_CHANGED)
        intent.putExtra(EXTRA_ACTIVE, isScanning)
        intent.putExtra(EXTRA_STATUS, if (isScanning) "Scanning" else "Idle")
        intent.putExtra(EXTRA_SCAN_REMAINING_MS, if (isScanning) (scanEndMs - System.currentTimeMillis()).coerceAtLeast(0L) else 0L)
        intent.putExtra(EXTRA_NEXT_SCAN_MS, if (nextScanMs > 0L) (nextScanMs - System.currentTimeMillis()).coerceAtLeast(0L) else 0L)
        intent.putExtra(EXTRA_DETECTED_DEVICE_COUNT, deviceSummary.size)
        intent.putExtra(EXTRA_DEVICE_SUMMARY, deviceSummary.joinToString("\n"))
        intent.putExtra(EXTRA_SCAN_HISTORY, scanHistory.joinToString("\n"))
        sendBroadcast(intent)
    }

    private fun dateUtcNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

    private fun toHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
