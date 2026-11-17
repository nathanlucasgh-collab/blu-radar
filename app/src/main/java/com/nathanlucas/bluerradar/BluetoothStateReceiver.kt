package com.nathanlucas.bluerradar

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BluetoothStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_ON && Preferences.isEnabled(context)) {
                RadarService.start(context)
            }
            if (state == BluetoothAdapter.STATE_OFF) {
                RadarService.stop(context)
            }
        }
    }
}
