package com.nathanlucas.bluerradar

import android.app.Application

class RadarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CsvStorage.initialize(this)
    }
}
