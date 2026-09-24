package app.nami.compat.aniyomi

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeEpisode
import app.nami.domain.AnimeRef
import app.nami.domain.AnimeSearchResult
import app.nami.domain.EpisodeRef
import app.nami.domain.MediaTrack
import app.nami.domain.ResolvedMedia
import app.nami.runtime.NamiSourceRegistry
import app.nami.source.NamiAnimeSource
import app.nami.source.SourceCapabilities
import app.nami.source.SourceMetadata
import app.nami.source.SourceOrigin
import app.nami.source.SourcePage
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

private const val EXTENSION_FEATURE = "tachiyomi.animeextension"
private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
private const val METADATA_NAME = "aniyomix.name"
private const val METADATA_EXTENSION_LIB = "aniyomix.extensionLib"
private const val LOG_TAG = "NamiAniyomiCompat"
private val SUPPORTED_EXTENSION_LIB_VERSIONS = setOf(16.0)
private const val LEGACY_LABEL_PREFIX = "Aniyomi: "

/**
 * Discovers already-installed Aniyomi anime extension APKs and exposes each source through
 * Nami's source contract. Extension failures are isolated per package.
 */
class AniyomiExtensionRegistry(
    private val context: Context,
) : NamiSourceRegistry {

    override suspend fun installedSources(): List<NamiAnimeSource> = withContext(Dispatchers.IO) {
        val packages = installedExtensionPackages()
        Log.i(LOG_TAG, "Found ${packages.size} installed anime extension package(s)")
        packages
            .flatMap { packageInfo ->
                runCatching { loadPackage(packageInfo) }
                    .onFailure {
                        Log.w(LOG_TAG, "Extension package ${packageInfo.packageName} failed during discovery (${it.javaClass.simpleName})")
                    }
                    .getOrDefault(emptyList())
            }
            .distinctBy { it.metadata.id }
            .sortedBy { it.metadata.name.lowercase() }
    }

    private fun installedExtensionPackages(): List<PackageInfo> {
        val flags = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA
        val packageManager = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(flags)
        }
        return packages.filter(::isAnimeExtension)
    }

    private fun isAnimeExtension(info: PackageInfo): Boolean =
        info.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE } &&
            info.applicationInfo?.metaData?.getString(METADATA_SOURCE_CLASS).isNullOrBlank().not()

    private fun loadPackage(packageInfo: PackageInfo): List<NamiAnimeSource> {
        val applicationInfo = packageInfo.applicationInfo ?: return emptyList()
        val metadata = applicationInfo.metaData ?: run {
            Log.w(LOG_TAG, "Skipping ${packageInfo.packageName}: extension metadata is missing")
            return emptyList()
        }
        val extensionVersion = packageInfo.versionName ?: "unknown"
        val extensionLibVersion = metadata.getInt(METADATA_EXTENSION_LIB, 0)
            .takeIf { it > 0 }
            ?.toDouble()
            ?: extensionVersion.substringBeforeLast('.').toDoubleOrNull()
        if (extensionLibVersion !in SUPPORTED_EXTENSION_LIB_VERSIONS) {
            Log.w(LOG_TAG, "Skipping ${packageInfo.packageName}: unsupported extensions-lib ${extensionLibVersion ?: "unknown"}")
            return emptyList()
        }
        val declaredClasses = metadata.getString(METADATA_SOURCE_CLASS)
            ?.split(';')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()
        if (declaredClasses.isEmpty()) {
            Log.w(LOG_TAG, "Skipping ${packageInfo.packageName}: no source classes are declared")
            return emptyList()
        }

        val extensionName = metadata.getString(METADATA_NAME)
            ?: context.packageManager.getApplicationLabel(applicationInfo)
                .toString()
                .removePrefix(LEGACY_LABEL_PREFIX)

        val loader = PathClassLoader(applicationInfo.sourceDir, context.classLoader)

        return declaredClasses.flatMap { declaredName ->
            val className = if (declaredName.startsWith('.')) {
                packageInfo.packageName + declaredName
            } else {
                declaredName
            }

            val loadedSources = runCatching {
                val instance = Class.forName(className, false, loader)
                    .getDeclaredConstructor()
                    .newInstance()

                when (instance) {
                    is AnimeSource -> listOf(instance)
                    is AnimeSourceFactory -> instance.createSources()
                    else -> error("Declared class is neither AnimeSource nor AnimeSourceFactory")
                }
            }.onFailure {
                Log.w(LOG_TAG, "Failed loading ${packageInfo.packageName}:$className (${it.javaClass.simpleName})")
            }.getOrDefault(emptyList())

            if (loadedSources.isEmpty()) {
                Log.w(LOG_TAG, "No sources produced by ${packageInfo.packageName}:$className")
            }

            loadedSources.map { legacy ->
                LegacyAnimeSourceAdapter(
                    packageName = packageInfo.packageName,
                    extensionName = extensionName,
                    extensionVersion = extensionVersion,
                    extensionApiVersion = extensionLibVersion.toInt(),
                    source = legacy,
                ).also {
                    Log.i(LOG_TAG, "Loaded source ${it.metadata.id} from ${packageInfo.packageName} (version $extensionVersion, API ${extensionLibVersion.toInt()})")
                }
            }
        }
    }
}

private class LegacyAnimeSourceAdapter(
    private val packageName: String,
    private val extensionName: String,
    private val extensionVersion: String,
    private val extensionApiVersion: Int,
    private val source: AnimeSource,
) : NamiAnimeSource {

    private val animeCache = ConcurrentHashMap<String, SAnime>()
    private val episodeCache = ConcurrentHashMap<String, SEpisode>()

    override val metadata: SourceMetadata = SourceMetadata(
        id = packageName + ":" + source.id,
        name = source.name,
        language = (source as? eu.kanade.tachiyomi.animesource.AnimeCatalogueSource)?.lang,
        origin = SourceOrigin.ANIYOMI_COMPATIBLE,
        extensionName = extensionName,
        homeUrl = (source as? AnimeHttpSource)?.getHomeUrl(),
        capabilities = SourceCapabilities(
            searchable = source is eu.kanade.tachiyomi.animesource.AnimeCatalogueSource,
            browsable = source is AnimeHttpSource,
            details = true,
            episodes = true,
            streamable = source is AnimeHttpSource,
            downloadable = false,
            configurable = source is eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource,
        ),
        extensionPackage = packageName,
        extensionVersion = extensionVersion,
        extensionApiVersion = extensionApiVersion,
    )

    override suspend fun search(query: String, page: Int): SourcePage<AnimeSearchResult> {
        val catalogue = source as? eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
            ?: return SourcePage(emptyList(), false)
        val result = catalogue.getSearchAnime(page, query, catalogue.getFilterList())
        val mapped = result.animes.map { anime ->
            animeCache[anime.url] = anime
            AnimeSearchResult(
                ref = AnimeRef(metadata.id, anime.url),
                title = anime.title,
                coverUrl = anime.thumbnail_url,
                description = anime.description,
            )
        }
        return SourcePage(mapped, result.hasNextPage)
    }

    override suspend fun details(anime: AnimeRef): AnimeDetails {
        val sourceAnime = animeCache[anime.sourceAnimeId] ?: restoreAnime(anime.sourceAnimeId)
        val updated = source.getAnimeDetails(sourceAnime)
        animeCache[updated.url] = updated
        if (updated.url != anime.sourceAnimeId) {
            animeCache[anime.sourceAnimeId] = updated
        }

        return AnimeDetails(
            ref = AnimeRef(metadata.id, updated.url),
            title = updated.title,
            coverUrl = updated.thumbnail_url,
            bannerUrl = updated.background_url,
            description = updated.description,
            metadata = buildMap {
                updated.author?.takeIf(String::isNotBlank)?.let { put("Author", it) }
                updated.artist?.takeIf(String::isNotBlank)?.let { put("Artist", it) }
                put("Status", statusName(updated.status))
            },
            genres = updated.genre
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                .orEmpty(),
            webUrl = (source as? AnimeHttpSource)?.getAnimeUrl(updated),
        )
    }

    override suspend fun episodes(anime: AnimeRef): List<AnimeEpisode> {
        val sourceAnime = animeCache[anime.sourceAnimeId] ?: restoreAnime(anime.sourceAnimeId)
        return source.getEpisodeList(sourceAnime).map { episode ->
            val key = episodeKey(anime.sourceAnimeId, episode.url)
            episodeCache[key] = episode
            AnimeEpisode(
                ref = EpisodeRef(
                    sourceId = metadata.id,
                    sourceAnimeId = anime.sourceAnimeId,
                    sourceEpisodeId = episode.url,
                ),
                title = episode.name,
                number = episode.episode_number.takeIf { it >= 0f }?.toDouble(),
                uploadedAtEpochMillis = episode.date_upload.takeIf { it > 0L },
            )
        }
    }

    override suspend fun resolve(episode: EpisodeRef): List<ResolvedMedia> {
        val legacyEpisode = episodeCache[episodeKey(episode.sourceAnimeId, episode.sourceEpisodeId)]
            ?: SEpisode.create().apply {
                url = episode.sourceEpisodeId
                name = episode.sourceEpisodeId
            }

        var hosters = source.getHosterList(legacyEpisode)
        val http = source as? AnimeHttpSource
        if (http != null) {
            hosters = with(http) { hosters.sortHosters() }
        }

        return hosters.flatMap { hoster ->
            var videos = source.getVideoList(hoster)
            if (http != null) {
                videos = http.sortVideosForNami(videos)
            }
            videos.mapNotNull { video ->
                val resolved = if (!video.initialized && http != null) {
                    http.resolveVideo(video)
                } else {
                    video
                }
                resolved?.toNamiMedia()
            }
        }
    }

    private fun restoreAnime(sourceAnimeId: String): SAnime = SAnime.create().apply {
        url = sourceAnimeId
        title = decodeTitle(sourceAnimeId)
    }

    private fun decodeTitle(value: String): String {
        val encoded = value.substringAfter("&title=", "")
        if (encoded.isNotBlank()) {
            return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(encoded)
        }
        return value.substringAfterLast('/').substringBefore('?').ifBlank { value }
    }

    private fun episodeKey(animeId: String, episodeId: String) = animeId + "\u0000" + episodeId

    private fun statusName(status: Int): String = when (status) {
        SAnime.ONGOING -> "Ongoing"
        SAnime.COMPLETED -> "Completed"
        SAnime.LICENSED -> "Licensed"
        SAnime.PUBLISHING_FINISHED -> "Finished"
        SAnime.CANCELLED -> "Cancelled"
        SAnime.ON_HIATUS -> "On hiatus"
        else -> "Unknown"
    }

    private fun Video.toNamiMedia(): ResolvedMedia {
        val videoHeaders = headers
        val headerMap = videoHeaders?.names()?.associateWith { name -> videoHeaders[name].orEmpty() }.orEmpty()
        return ResolvedMedia(
            url = videoUrl,
            quality = videoTitle.ifBlank { resolution?.let { it.toString() + "p" } },
            headers = headerMap,
            subtitles = subtitleTracks.map { MediaTrack(it.url, it.lang) },
            audioTracks = audioTracks.map { MediaTrack(it.url, it.lang) },
        )
    }
}
