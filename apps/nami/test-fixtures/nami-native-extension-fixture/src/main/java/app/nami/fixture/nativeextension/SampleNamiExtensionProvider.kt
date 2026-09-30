package app.nami.fixture.nativeextension

import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeEpisode
import app.nami.domain.AnimeRef
import app.nami.domain.AnimeSearchResult
import app.nami.domain.EpisodeRef
import app.nami.domain.ResolvedMedia
import app.nami.source.NamiAnimeSource
import app.nami.source.NamiConfigurableSource
import app.nami.source.NamiExtensionHost
import app.nami.source.NamiExtensionProvider
import app.nami.source.NamiSourceSetting
import app.nami.source.SourceCapabilities
import app.nami.source.SourceMetadata
import app.nami.source.SourceOrigin
import app.nami.source.SourcePage

/** Small first-party example for contract tests and extension authors. */
class SampleNamiExtensionProvider : NamiExtensionProvider {
    override val extensionId: String = EXTENSION_ID
    override val displayName: String = "Nami Sample Extension"

    override fun sources(host: NamiExtensionHost): List<NamiAnimeSource> =
        listOf(SampleAnimeSource(host))

    private companion object {
        const val EXTENSION_ID = "app.nami.fixture.nativeextension"
    }
}

private class SampleAnimeSource(
    private val host: NamiExtensionHost,
) : NamiAnimeSource, NamiConfigurableSource {
    override val metadata = SourceMetadata(
        id = SOURCE_ID,
        name = "Aurora Anime",
        language = "en",
        origin = SourceOrigin.NATIVE_NAMI,
        homeUrl = "https://example.invalid/anime",
        capabilities = SourceCapabilities(
            searchable = true,
            browsable = true,
            popular = true,
            latest = true,
            details = true,
            episodes = true,
            streamable = true,
            downloadable = true,
            configurable = true,
        ),
    )

    override fun settings(): List<NamiSourceSetting> = listOf(
        NamiSourceSetting.Choice(
            key = "quality",
            title = "Preferred quality",
            summary = "Used when more than one quality is available.",
            choices = listOf("1080p", "720p"),
            defaultValue = host.getPreference(EXTENSION_ID, SOURCE_ID, "quality") ?: "1080p",
        ),
    )

    override suspend fun search(query: String, page: Int): SourcePage<AnimeSearchResult> =
        if (page == 1 && query.contains("nami", ignoreCase = true)) {
            SourcePage(listOf(anime), hasNextPage = false)
        } else {
            SourcePage(emptyList(), hasNextPage = false)
        }

    override suspend fun popular(page: Int): SourcePage<AnimeSearchResult> =
        if (page == 1) SourcePage(listOf(anime), hasNextPage = false)
        else SourcePage(emptyList(), hasNextPage = false)

    override suspend fun latest(page: Int): SourcePage<AnimeSearchResult> = popular(page)

    override suspend fun details(anime: AnimeRef): AnimeDetails = AnimeDetails(
        ref = anime,
        title = "Nami Contract Sample",
        description = "A first-party fixture used to verify Nami's extension API.",
        metadata = mapOf("status" to "Ongoing", "year" to "2026"),
        genres = listOf("Adventure", "Fantasy"),
    )

    override suspend fun episodes(anime: AnimeRef): List<AnimeEpisode> = listOf(
        AnimeEpisode(
            ref = EpisodeRef(SOURCE_ID, ANIME_ID, EPISODE_ID),
            title = "Episode 1 — The Contract",
            number = 1.0,
            sourceState = "sample-episode-v1",
        ),
    )

    override suspend fun resolve(episode: EpisodeRef): List<ResolvedMedia> = listOf(
        ResolvedMedia(
            url = "https://example.invalid/sample-episode-1.m3u8",
            mimeType = "application/vnd.apple.mpegurl",
            quality = "1080p",
            headers = mapOf(
                "Referer" to "https://example.invalid/",
                "User-Agent" to "Nami-Sample-Extension/1.0",
            ),
            hosterName = "Sample CDN",
        ),
    )

    private companion object {
        const val EXTENSION_ID = "app.nami.fixture.nativeextension"
        const val SOURCE_ID = "sample:nami-anime"
        const val ANIME_ID = "nami-contract-sample"
        const val EPISODE_ID = "nami-contract-sample-episode-1"

        val anime = AnimeSearchResult(
            ref = AnimeRef(SOURCE_ID, ANIME_ID),
            title = "Nami Contract Sample",
            sourceState = "sample-anime-v1",
        )
    }
}
