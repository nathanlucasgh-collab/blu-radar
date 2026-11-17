package com.nathanlucas.bluerradar

import android.content.Context
import androidx.core.content.edit

object Preferences {
    private const val PREFS = "bluetooth_radar_settings"
    private const val ENABLED_KEY = "enabled"
    private const val LAST_SESSION_ID_KEY = "last_session_id"
    private const val LAST_SCAN_START_KEY = "last_scan_start"

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(ENABLED_KEY, enabled)
        }
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED_KEY, false)

    fun setLastSessionId(context: Context, sessionId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(LAST_SESSION_ID_KEY, sessionId)
        }
    }

    fun getLastSessionId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAST_SESSION_ID_KEY, null)

    fun setLastScanStart(context: Context, timeMs: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong(LAST_SCAN_START_KEY, timeMs)
        }
    }

    fun getLastScanStart(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(LAST_SCAN_START_KEY, 0L)
}
