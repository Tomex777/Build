package app.nami.android

import app.nami.domain.AnimeEpisode
import app.nami.domain.EpisodeRef
import app.nami.domain.ResolvedMedia
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackMediaSelectorTest {
    private fun media(quality: String, host: String) = ResolvedMedia(
        url = "https://example.invalid/$host/$quality.mp4",
        quality = quality,
        hosterName = host,
    )

    @Test
    fun autoChoosesHighestQualityRegardlessOfSourceOrder() {
        val selected = PlaybackMediaSelector.choose(
            listOf(media("360p", "A"), media("1080p", "B"), media("720p", "A")),
        )
        assertEquals("1080p", selected?.quality)
    }

    @Test
    fun preferredQualityFallsBackDownBeforeGoingAbove() {
        val selected = PlaybackMediaSelector.choose(
            listOf(media("1080p", "A"), media("720p", "A"), media("360p", "A")),
            preferredHeight = 900,
        )
        assertEquals("720p", selected?.quality)
    }

    @Test
    fun preferredHostIsHonoredWhenAvailable() {
        val selected = PlaybackMediaSelector.choose(
            listOf(media("1080p", "A"), media("720p", "B")),
            preferredHost = "B",
        )
        assertEquals("B", selected?.hosterName)
    }

    @Test
    fun episodeNavigatorUsesEpisodeNumbersNotListDirection() {
        val episodes = listOf(
            episode("Episode 8", 8.0),
            episode("Episode 7", 7.0),
            episode("Episode 6", 6.0),
        )
        assertEquals(0, PlaybackEpisodeNavigator.nextIndex(episodes, 1))
        assertEquals(2, PlaybackEpisodeNavigator.previousIndex(episodes, 1))
    }

    private fun episode(title: String, number: Double) = AnimeEpisode(
        ref = EpisodeRef("source", "anime", title),
        title = title,
        number = number,
    )
}
