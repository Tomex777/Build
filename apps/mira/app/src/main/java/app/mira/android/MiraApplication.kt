package app.mira.android

import android.app.Application
import app.mira.runtime.MiraSourceRegistry
import app.mira.source.MiraSource
import app.mira.source.internetarchive.InternetArchiveMovieSource
import app.mira.source.tvmaze.TvMazeSource

class MiraApplication : Application() {
    val sources: List<MiraSource> by lazy {
        listOf(
            InternetArchiveMovieSource(),
            TvMazeSource(),
        )
    }

    val sourceRegistry: MiraSourceRegistry by lazy {
        MiraSourceRegistry { sources }
    }

    val downloadManager: MiraDownloadManager by lazy {
        MiraDownloadManager(this)
    }

    override fun onCreate() {
        super.onCreate()
        downloadManager.startBackgroundEngine()
    }
}
