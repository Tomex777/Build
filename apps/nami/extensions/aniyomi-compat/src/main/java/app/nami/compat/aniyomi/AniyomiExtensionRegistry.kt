package app.nami.compat.aniyomi

import android.app.Application
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
private val SUPPORTED_EXTENSION_LIB_VERSIONS = setOf(14.0, 16.0, 17.0)
private const val LEGACY_LABEL_PREFIX = "Aniyomi: "

/**
 * Discovers already-installed Aniyomi anime extension APKs and exposes each source through
 * Nami's source contract. Extension failures are isolated per package.
 */
class AniyomiExtensionRegistry(
    private val context: Context,
) : NamiSourceRegistry {

    private val signaturePins = ExtensionSignaturePins(context)

    override suspend fun installedSources(): List<NamiAnimeSource> = withContext(Dispatchers.IO) {
        val application = context.applicationContext as? Application
            ?: error("An Android Application is required to load Aniyomi extensions")
        AniyomiExtensionHost.initialize(application)

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
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                0
            })
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

    private fun instantiateSources(
        className: String,
        loader: ClassLoader,
    ): List<AnimeSource> {
        val instance = Class.forName(className, false, loader)
            .getDeclaredConstructor()
            .newInstance()

        return when (instance) {
            is AnimeSource -> listOf(instance)
            is AnimeSourceFactory -> instance.createSources()
            else -> error("Declared class is neither AnimeSource nor AnimeSourceFactory")
        }
    }

    private fun loadPackage(packageInfo: PackageInfo): List<NamiAnimeSource> {
        when (val signerDecision = signaturePins.verify(packageInfo)) {
            ExtensionSignerDecision.UNSIGNED -> {
                Log.w(LOG_TAG, "Skipping ${packageInfo.packageName}: extension APK is unsigned")
                return emptyList()
            }
            ExtensionSignerDecision.REJECTED -> {
                Log.w(LOG_TAG, "Skipping ${packageInfo.packageName}: signer does not match the pinned identity")
                return emptyList()
            }
            ExtensionSignerDecision.FIRST_SEEN_PINNED -> {
                Log.i(LOG_TAG, "Pinned signer identity for ${packageInfo.packageName}")
            }
            ExtensionSignerDecision.TRUSTED -> Unit
        }

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
        if (extensionLibVersion == null || extensionLibVersion !in SUPPORTED_EXTENSION_LIB_VERSIONS) {
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

        val childFirstLoader = ChildFirstPathClassLoader(
            applicationInfo.sourceDir,
            null,
            context.classLoader,
        )
        val fallbackLoader = PathClassLoader(
            applicationInfo.sourceDir,
            null,
            context.classLoader,
        )

        return declaredClasses.flatMap { declaredName ->
            val className = if (declaredName.startsWith('.')) {
                packageInfo.packageName + declaredName
            } else {
                declaredName
            }

            val loadedSources = try {
                instantiateSources(className, childFirstLoader)
            } catch (linkage: LinkageError) {
                Log.w(
                    LOG_TAG,
                    "Child-first load failed for ${packageInfo.packageName}:$className; retrying parent-first",
                )
                runCatching { instantiateSources(className, fallbackLoader) }
                    .onFailure {
                        Log.w(
                            LOG_TAG,
                            "Fallback load failed for ${packageInfo.packageName}:$className (${it.javaClass.simpleName})",
                        )
                    }
                    .getOrDefault(emptyList())
            } catch (failure: Throwable) {
                Log.w(
                    LOG_TAG,
                    "Failed loading ${packageInfo.packageName}:$className (${failure.javaClass.simpleName})",
                )
                emptyList()
            }

            if (loadedSources.isEmpty()) {
                Log.w(LOG_TAG, "No sources produced by ${packageInfo.packageName}:$className")
            }

            loadedSources.map { legacy ->
                LegacyAnimeSourceAdapter(
                    hostContext = context.applicationContext,
                    packageName = packageInfo.packageName,
                    extensionName = extensionName,
                    extensionVersion = extensionVersion,
                    extensionApiVersion = extensionLibVersion!!.toInt(),
                    source = legacy,
                ).also {
                    Log.i(LOG_TAG, "Loaded source ${it.metadata.id} from ${packageInfo.packageName} (version $extensionVersion, API ${extensionLibVersion!!.toInt()})")
                }
            }
        }
    }
}

internal class LegacyAnimeSourceAdapter(
    private val hostContext: Context,
    private val packageName: String,
    private val extensionName: String,
    private val extensionVersion: String,
    private val extensionApiVersion: Int,
    private val source: AnimeSource,
) : NamiAnimeSource, AniyomiConfigurableSourceHandle, AniyomiBrowserSourceHandle {

    init {
        AniyomiBrowserSessionRegistry.register(source)
    }

    private val animeCache = ConcurrentHashMap<String, SAnime>()
    private val episodeCache = ConcurrentHashMap<String, SEpisode>()
    private val catalogue =
        source as? eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
    private val supportsPopularListing =
        extensionApiVersion >= 17 || catalogue != null
    private val supportsLatestListing = when {
        extensionApiVersion >= 17 -> source.supportsLatest
        else -> catalogue?.supportsLatest == true
    }

    override val metadata: SourceMetadata = SourceMetadata(
        id = packageName + ":" + source.id,
        name = source.name,
        language = source.lang.takeIf { it.isNotBlank() },
        origin = SourceOrigin.ANIYOMI_COMPATIBLE,
        extensionName = extensionName,
        homeUrl = (source as? AnimeHttpSource)?.getHomeUrl(),
        capabilities = SourceCapabilities(
            searchable = extensionApiVersion >= 17 || catalogue != null,
            browsable = supportsPopularListing,
            popular = supportsPopularListing,
            latest = supportsLatestListing,
            details = true,
            episodes = true,
            streamable = source is AnimeHttpSource,
            downloadable = source is AnimeHttpSource,
            configurable = source is eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource,
        ),
        extensionPackage = packageName,
        extensionVersion = extensionVersion,
        extensionApiVersion = extensionApiVersion,
    )

    override fun browserHeaders(url: String): Map<String, String> =
        AniyomiBrowserSessionRegistry.headers(source.id)

    override fun preferenceName(): String = "source_${source.id}"

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val configurable = source as? eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
            ?: error("Source ${metadata.name} does not expose configurable preferences")
        configurable.setupPreferenceScreen(screen)
    }

    override suspend fun popular(page: Int): SourcePage<AnimeSearchResult> {
        if (!supportsPopularListing) return SourcePage(emptyList(), false)

        val result = when {
            extensionApiVersion <= 14 -> {
                val legacyCatalogue = catalogue ?: return SourcePage(emptyList(), false)
                @Suppress("DEPRECATION")
                legacyCatalogue.fetchPopularAnime(page)
                    .toBlocking()
                    .single()
            }
            else -> source.getPopularAnime(page)
        }
        return mapAnimePage(result)
    }

    override suspend fun latest(page: Int): SourcePage<AnimeSearchResult> {
        if (!supportsLatestListing) return SourcePage(emptyList(), false)

        val result = when {
            extensionApiVersion <= 14 -> {
                val legacyCatalogue = catalogue ?: return SourcePage(emptyList(), false)
                @Suppress("DEPRECATION")
                legacyCatalogue.fetchLatestUpdates(page)
                    .toBlocking()
                    .single()
            }
            else -> source.getLatestUpdates(page)
        }
        return mapAnimePage(result)
    }

    override suspend fun search(query: String, page: Int): SourcePage<AnimeSearchResult> {
        val result = when {
            extensionApiVersion <= 14 -> {
                val legacyCatalogue = catalogue ?: return SourcePage(emptyList(), false)
                @Suppress("DEPRECATION")
                legacyCatalogue.fetchSearchAnime(page, query, legacyCatalogue.getFilterList())
                    .toBlocking()
                    .single()
            }
            else -> source.getSearchAnime(page, query, source.getFilterList())
        }
        return mapAnimePage(result)
    }

    private fun mapAnimePage(
        result: eu.kanade.tachiyomi.animesource.model.AnimesPage,
    ): SourcePage<AnimeSearchResult> {
        val mapped = result.animes.map { anime ->
            animeCache[anime.url] = anime
            AnimeSearchResult(
                ref = AnimeRef(metadata.id, anime.url),
                title = anime.title,
                coverUrl = anime.thumbnail_url,
                description = anime.description,
                sourceState = LegacyAnimeStateCodec.encode(anime),
            )
        }
        return SourcePage(mapped, result.hasNextPage)
    }

    override suspend fun details(anime: AnimeRef): AnimeDetails =
        details(anime, sourceState = null)

    override suspend fun details(
        anime: AnimeRef,
        sourceState: String?,
    ): AnimeDetails {
        val sourceAnime = animeCache[anime.sourceAnimeId]
            ?: LegacyAnimeStateCodec.decode(sourceState)
            ?: restoreAnime(anime.sourceAnimeId)
        val updated = when {
            extensionApiVersion >= 17 -> source.getAnimeEpisodeUpdate(
                anime = sourceAnime,
                episodes = emptyList(),
                fetchDetails = true,
                fetchEpisodes = false,
            ).anime
            extensionApiVersion <= 14 -> {
                @Suppress("DEPRECATION")
                source.fetchAnimeDetails(sourceAnime).toBlocking().single()
            }
            else -> {
                @Suppress("DEPRECATION")
                source.getAnimeDetails(sourceAnime)
            }
        }
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
            sourceState = LegacyAnimeStateCodec.encode(updated),
        )
    }

    override suspend fun episodes(anime: AnimeRef): List<AnimeEpisode> =
        episodes(anime, sourceState = null)

    override suspend fun episodes(
        anime: AnimeRef,
        sourceState: String?,
    ): List<AnimeEpisode> {
        val sourceAnime = animeCache[anime.sourceAnimeId]
            ?: LegacyAnimeStateCodec.decode(sourceState)
            ?: restoreAnime(anime.sourceAnimeId)
        val sourceEpisodes = when {
            extensionApiVersion >= 17 -> source.getAnimeEpisodeUpdate(
                anime = sourceAnime,
                episodes = emptyList(),
                fetchDetails = false,
                fetchEpisodes = true,
            ).episodes
            extensionApiVersion <= 14 -> {
                @Suppress("DEPRECATION")
                source.fetchEpisodeList(sourceAnime).toBlocking().single()
            }
            else -> {
                @Suppress("DEPRECATION")
                source.getEpisodeList(sourceAnime)
            }
        }
        return sourceEpisodes.map { episode ->
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
                sourceState = LegacyEpisodeStateCodec.encode(episode),
            )
        }
    }

    override suspend fun resolve(episode: EpisodeRef): List<ResolvedMedia> =
        resolve(episode, sourceState = null)

    override suspend fun resolve(
        episode: EpisodeRef,
        sourceState: String?,
    ): List<ResolvedMedia> {
        val legacyEpisode = episodeCache[episodeKey(episode.sourceAnimeId, episode.sourceEpisodeId)]
            ?: LegacyEpisodeStateCodec.decode(sourceState)
            ?: SEpisode.create().apply {
                url = episode.sourceEpisodeId
                name = episode.sourceEpisodeId
            }

        val http = source as? AnimeHttpSource

        if (extensionApiVersion <= 14) {
            @Suppress("DEPRECATION")
            var videos = source.getVideoList(legacyEpisode)
            if (http != null) {
                videos = http.sortVideosForNami(videos)
            }
            return videos.mapNotNull { video ->
                video.resolveLegacyVideo(http)?.toNamiMedia()
            }
        }

        var hosters = source.getHosterList(legacyEpisode)
        if (http != null) {
            hosters = with(http) { hosters.sortHosters() }
        }

        return hosters.flatMap { hoster ->
            var videos = hoster.videoList ?: source.getVideoList(hoster)
            if (http != null) {
                videos = http.sortVideosForNami(videos)
            }
            videos.mapNotNull { video ->
                val resolved = if (!video.initialized && http != null) {
                    http.resolveVideo(video)
                } else {
                    video
                }
                resolved?.toNamiMedia(hoster.hosterName)
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

    private suspend fun Video.resolveLegacyVideo(http: AnimeHttpSource?): Video? {
        if (http == null) return this

        if (videoUrl.isBlank() || videoUrl == "null") {
            @Suppress("DEPRECATION")
            val resolvedUrl = runCatching { http.getVideoUrl(this) }.getOrNull()
            if (!resolvedUrl.isNullOrBlank()) {
                videoUrl = resolvedUrl
            }
        }

        return if (!initialized) {
            http.resolveVideo(this) ?: this
        } else {
            this
        }
    }

    private fun Video.toNamiMedia(hosterName: String? = null): ResolvedMedia {
        val videoHeaders = headers
        val headerMap = videoHeaders?.names()?.associateWith { name -> videoHeaders[name].orEmpty() }.orEmpty()
        return ResolvedMedia(
            url = videoUrl,
            quality = videoTitle.ifBlank { resolution?.let { it.toString() + "p" } },
            headers = headerMap,
            subtitles = subtitleTracks.map { MediaTrack(it.url, it.lang) },
            audioTracks = audioTracks.map { MediaTrack(it.url, it.lang) },
            hosterName = hosterName?.takeUnless { it == eu.kanade.tachiyomi.animesource.model.Hoster.NO_HOSTER_LIST },
        )
    }
}
