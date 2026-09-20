package com.night.homira

import android.app.Application
import com.night.homira.call.HomiraPushBootstrap

class HomiraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching {
            HomiraPushBootstrap.initialize(this)
        }
    }
}
