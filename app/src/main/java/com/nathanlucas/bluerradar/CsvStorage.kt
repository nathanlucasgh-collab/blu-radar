package com.nathanlucas.bluerradar

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CsvStorage {

    private const val DIRECTORY_NAME = "BluetoothRadar"
    private const val OBSERVATIONS_FILE = "bluetooth_observations.csv"
    private const val SESSIONS_FILE = "scan_sessions.csv"
    private const val DEVICES_FILE = "devices.csv"
    private const val ERRORS_FILE = "scan_errors.csv"
    private const val MANIFEST_FILE = "integrity_manifest.txt"

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
        ensureDirectory()
        ensureFile(OBSERVATIONS_FILE, listOf(
            "timestamp_utc",
            "session_id",
            "device_name",
            "ble_address",
            "rssi_dbm",
            "manufacturer_id",
            "manufacturer_data_hex",
            "service_uuids",
            "service_data_hex",
            "tx_power_dbm",
            "connectable",
            "first_seen_utc",
            "last_seen_utc"
        ))
        ensureFile(SESSIONS_FILE, listOf(
            "session_id",
            "timestamp_utc",
            "duration_ms",
            "device_count",
            "scan_started_utc",
            "scan_ended_utc",
            "status"
        ))
        ensureFile(DEVICES_FILE, listOf(
            "device_key",
            "device_name",
            "ble_address",
            "first_seen_utc",
            "last_seen_utc",
            "rssi_dbm_min",
            "rssi_dbm_max",
            "observation_count"
        ))
        ensureFile(ERRORS_FILE, listOf(
            "timestamp_utc",
            "session_id",
            "error_type",
            "error_message",
            "severity"
        ))
    }

    fun appendObservation(record: ObservationRecord) {
        val file = fileFor(OBSERVATIONS_FILE)
        appendCsv(file, listOf(
            record.timestampUtc,
            record.sessionId,
            record.deviceName,
            record.bleAddress,
            record.rssiDbm,
            record.manufacturerId,
            record.manufacturerDataHex,
            record.serviceUuids,
            record.serviceDataHex,
            record.txPowerDbm,
            record.connectable,
            record.firstSeenUtc,
            record.lastSeenUtc
        ))
    }

    fun appendSession(record: SessionRecord) {
        appendCsv(fileFor(SESSIONS_FILE), listOf(
            record.sessionId,
            record.timestampUtc,
            record.durationMs,
            record.deviceCount,
            record.scanStartedUtc,
            record.scanEndedUtc,
            record.status
        ))
    }

    fun appendError(sessionId: String, errorType: String, message: String, severity: String) {
        appendCsv(fileFor(ERRORS_FILE), listOf(
            dateUtcNow(),
            sessionId,
            errorType,
            message,
            severity
        ))
    }

    fun exportBundle(): File {
        val dir = ensureDirectory()
        val archive = File(dir, "bluetooth_radar_export_${timestampStamp()}.zip")
        val zip = ZipOutputStream(FileOutputStream(archive))
        for (name in listOf(OBSERVATIONS_FILE, SESSIONS_FILE, DEVICES_FILE, ERRORS_FILE, MANIFEST_FILE)) {
            val file = fileFor(name)
            if (file.exists()) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(file.readBytes())
                zip.closeEntry()
            }
        }
        zip.close()
        return archive
    }

    fun verifyIntegrity(): File {
        val dir = ensureDirectory()
        val manifest = File(dir, MANIFEST_FILE)
        val entries = mutableListOf<String>()
        for (name in listOf(OBSERVATIONS_FILE, SESSIONS_FILE, DEVICES_FILE, ERRORS_FILE)) {
            val file = fileFor(name)
            if (file.exists()) {
                entries.add("${name},${sha256(file.readBytes())}")
            }
        }
        val writer = OutputStreamWriter(FileOutputStream(manifest, true), StandardCharsets.UTF_8)
        writer.write("timestamp_utc,filename,sha256\n")
        for (entry in entries) {
            writer.write("${dateUtcNow()},$entry\n")
        }
        writer.close()
        return manifest
    }

    fun isWritable(): Boolean {
        return try {
            val dir = ensureDirectory()
            val sample = File(dir, ".writable_test")
            sample.writeText("ok", StandardCharsets.UTF_8)
            sample.delete()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureDirectory(): File {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIRECTORY_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun fileFor(fileName: String): File = File(ensureDirectory(), fileName)

    private fun ensureFile(fileName: String, header: List<String>) {
        val file = fileFor(fileName)
        if (!file.exists()) {
            appendCsv(file, header)
        }
    }

    private fun appendCsv(file: File, values: List<Any?>) {
        val output = OutputStreamWriter(FileOutputStream(file, true), StandardCharsets.UTF_8)
        output.write(values.joinToString(",") { csvCell(it) } + "\n")
        output.close()
    }

    private fun csvCell(value: Any?): String {
        val raw = (value ?: "").toString()
        return if (raw.contains(',') || raw.contains('"') || raw.contains('\n') || raw.contains('\r')) {
            val escaped = raw.replace("\"", "\"\"")
            "\"$escaped\""
        } else {
            raw
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun dateUtcNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

    private fun timestampStamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}

data class ObservationRecord(
    val timestampUtc: String,
    val sessionId: String,
    val deviceName: String,
    val bleAddress: String,
    val rssiDbm: Int,
    val manufacturerId: String,
    val manufacturerDataHex: String,
    val serviceUuids: String,
    val serviceDataHex: String,
    val txPowerDbm: String,
    val connectable: String,
    val firstSeenUtc: String,
    val lastSeenUtc: String
)

data class SessionRecord(
    val sessionId: String,
    val timestampUtc: String,
    val durationMs: Long,
    val deviceCount: Int,
    val scanStartedUtc: String,
    val scanEndedUtc: String,
    val status: String
)
