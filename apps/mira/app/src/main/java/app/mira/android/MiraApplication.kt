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

    val sourceEnablementStore: MiraSourceEnablementStore by lazy {
        MiraSourceEnablementStore(this)
    }

    val sourceRegistry: MiraSourceRegistry by lazy {
        MiraSourceRegistry {
            sources.filter { source ->
                sourceEnablementStore.isEnabled(source.metadata.id)
            }
        }
    }

    val downloadManager: MiraDownloadManager by lazy {
        MiraDownloadManager(this)
    }

    val libraryStore: MiraLibraryStore by lazy {
        MiraLibraryStore(this)
    }

    val watchProgressStore: MiraWatchProgressStore by lazy {
        MiraWatchProgressStore(this)
    }

    override fun onCreate() {
        super.onCreate()
        downloadManager.startBackgroundEngine()
    }
}
