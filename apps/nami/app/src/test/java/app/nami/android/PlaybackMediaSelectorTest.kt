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
    fun malformedMediaLocationIsSkippedBeforeQualitySelection() {
        val malformed = ResolvedMedia(
            url = "not-a-media-url",
            quality = "2160p",
            hosterName = "Broken",
        )
        val selected = PlaybackMediaSelector.choose(
            listOf(malformed, media("1080p", "Working")),
        )

        assertEquals("1080p", selected?.quality)
        assertEquals("Working", selected?.hosterName)
    }

    @Test
    fun expiredCandidateIsSkippedBeforePlayback() {
        val now = 1_000_000L
        val expired = media("2160p", "Expired").copy(expiresAtEpochMillis = now)
        val fresh = media("1080p", "Fresh").copy(expiresAtEpochMillis = now + 60_000L)

        val selected = PlaybackMediaSelector.choose(
            media = listOf(expired, fresh),
            nowEpochMillis = now,
        )

        assertEquals(fresh, selected)
    }

    @Test
    fun expiredFallbackIsNotOfferedAfterFailure() {
        val now = 1_000_000L
        val current = media("1080p", "Current")
        val expired = media("720p", "Expired").copy(expiresAtEpochMillis = now - 1L)

        assertEquals(
            null,
            PlaybackMediaSelector.nextPlayable(
                media = listOf(current, expired),
                current = current,
                nowEpochMillis = now,
            ),
        )
    }

    @Test
    fun streamRecoverySelectsAnotherPlayableCandidate() {
        val current = media("1080p", "Broken")
        val malformed = ResolvedMedia(url = "not-a-url", quality = "2160p")
        val fallback = media("720p", "Backup")
        val selected = PlaybackMediaSelector.nextPlayable(
            listOf(current, malformed, fallback),
            current,
        )

        assertEquals(fallback, selected)
    }

    @Test
    fun streamRecoveryReturnsNullWhenNoAlternativeExists() {
        val current = media("720p", "Only")

        assertEquals(null, PlaybackMediaSelector.nextPlayable(listOf(current), current))
    }

    @Test
    fun contentLocationRemainsPlayableForOfflineMedia() {
        val offline = ResolvedMedia(
            url = "content://app.nami.downloads/episode/1",
            quality = "720p",
            hosterName = "Offline",
        )

        val selected = PlaybackMediaSelector.choose(listOf(offline))

        assertEquals(offline, selected)
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
