package com.veya.app

import android.app.Application
import com.veya.app.data.DownloadStore
import com.veya.app.youtube.VeyaPlaybackBridge
import com.veya.app.youtube.VeyaSessionProvider
import com.veya.app.youtube.VeyaYouTubeRepository
import dev.tomex.youtube.api.YouTubeEngine
import dev.tomex.youtube.core.YouTubeEngineFactory

class VeyaApplication : Application() {
    lateinit var downloads: DownloadStore
        private set

    lateinit var youtubeEngine: YouTubeEngine
        private set

    lateinit var youtubeRepository: VeyaYouTubeRepository
        private set

    lateinit var playbackBridge: VeyaPlaybackBridge
        private set

    override fun onCreate() {
        super.onCreate()
        downloads = DownloadStore(this)

        val session = VeyaSessionProvider(this)
        youtubeEngine = YouTubeEngineFactory.create(session)
        youtubeRepository = VeyaYouTubeRepository(youtubeEngine)
        playbackBridge = VeyaPlaybackBridge(youtubeEngine)
    }
}
