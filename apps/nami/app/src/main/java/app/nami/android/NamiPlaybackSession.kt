package app.nami.android

import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeEpisode
import app.nami.source.NamiAnimeSource

internal sealed interface NamiPlaybackSession {
    data class Streaming(
        val source: NamiAnimeSource,
        val anime: AnimeDetails,
        val episodes: List<AnimeEpisode>,
        val initialEpisodeIndex: Int,
    ) : NamiPlaybackSession

    data class Downloaded(
        val items: List<NamiDownloadStatus>,
        val initialIndex: Int,
    ) : NamiPlaybackSession
}
