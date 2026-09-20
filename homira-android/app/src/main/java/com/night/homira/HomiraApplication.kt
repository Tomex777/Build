package com.night.homira

import android.app.Application

class HomiraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        HomiraCrashReporter.install(this)
    }
}
