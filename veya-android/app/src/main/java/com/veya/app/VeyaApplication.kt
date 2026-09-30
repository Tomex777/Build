package com.veya.app

import android.app.Application
import com.veya.app.data.WatchHistoryStore
import com.veya.app.youtube.VeyaPlaybackBridge
import com.veya.app.youtube.VeyaSessionProvider
import com.veya.app.youtube.VeyaYouTubeRepository
import dev.tomex.youtube.api.YouTubeEngine
import dev.tomex.youtube.core.YouTubeEngineFactory

class VeyaApplication : Application() {
    lateinit var youtubeEngine: YouTubeEngine
        private set

    lateinit var youtubeRepository: VeyaYouTubeRepository
        private set

    val playbackBridge: VeyaPlaybackBridge by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        VeyaPlaybackBridge(youtubeEngine)
    }

    lateinit var history: WatchHistoryStore
        private set

    override fun onCreate() {
        super.onCreate()
        history = WatchHistoryStore(this)
        youtubeEngine = YouTubeEngineFactory.create(VeyaSessionProvider(this))
        youtubeRepository = VeyaYouTubeRepository(youtubeEngine)
    }
}
