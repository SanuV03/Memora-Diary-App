package com.chronicle.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ChronicleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DailyReminderWorker.schedule(this)
    }
}