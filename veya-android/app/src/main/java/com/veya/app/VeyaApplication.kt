package com.veya.app

import android.app.Application
import com.veya.app.data.DownloadStore

class VeyaApplication : Application() {
    lateinit var downloads: DownloadStore
        private set

    override fun onCreate() {
        super.onCreate()
        downloads = DownloadStore(this)
    }
}
