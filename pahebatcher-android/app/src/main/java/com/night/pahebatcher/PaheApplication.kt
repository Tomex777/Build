package com.night.pahebatcher

import android.app.Application
import androidx.work.Configuration
import java.util.concurrent.Executors

class PaheApplication : Application(), Configuration.Provider {
    private val downloadExecutor by lazy {
        Executors.newFixedThreadPool(8)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setExecutor(downloadExecutor)
            .setMaxSchedulerLimit(20)
            .build()
}
