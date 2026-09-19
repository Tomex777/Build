package com.night.pahebatcher.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class VerificationKind { ANIMEPAHE }

class VerificationRequired(
    val kind: VerificationKind,
    message: String,
) : IOException(message)

data class AnimeSearchResult(
    val session: String,
    val title: String,
    val poster: String,
    val type: String,
    val episodes: Int,
    val status: String,
    val animeId: Int? = null,
    val aniListId: Int? = null,
    val sourceQueries: List<String> = emptyList(),
    val description: String = "",
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val score: Int? = null,
    val catalogNote: String = "",
)

data class EpisodeInfo(
    val number: Double,
    val session: String,
    val title: String,
    val fansub: String,
    val audio: String,
    val playUrl: String,
    val airedAt: Long? = null,
    val uploadedAt: Long? = null,
) {
    val epLabel: String
        get() = if (number == number.toInt().toDouble()) number.toInt().toString() else number.toString()
}

data class AnimeDetails(
    val result: AnimeSearchResult,
    val host: String,
    val episodes: List<EpisodeInfo>,
)

data class StreamInfo(
    val url: String,
    val cookie: String,
    val userAgent: String,
    val referer: String,
    val quality: Int,
    val audio: String,
    val fansub: String,
)

private data class ReleaseOption(
    val resolution: Int,
    val url: String,
    val isDub: Boolean,
    val fansub: String,
)

private data class Segment(
    val url: String,
    val keyUrl: String?,
    val iv: ByteArray?,
    val durationSeconds: Double,
)

private data class PaheRawResponse(
    val code: Int,
    val contentType: String,
    val body: String,
)

private class RuntimeCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        this.cookies.removeAll { existing ->
            existing.expiresAt < now ||
                cookies.any { incoming ->
                    incoming.name == existing.name &&
                        incoming.domain == existing.domain &&
                        incoming.path == existing.path
                }
        }
        this.cookies += cookies.filter { it.expiresAt >= now }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        return cookies.filter { it.matches(url) }
    }
}

class PaheRepository(
    private val context: Context,
    val sessions: SessionStore,
) {
    private val runtimeCookieJar = RuntimeCookieJar()
    private val sourceMatches = SourceMatchStore(context)

    private val client = OkHttpClient.Builder()
        .cookieJar(runtimeCookieJar)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val host = request.url.host.lowercase(Locale.US)
            if (host.contains("animepahe") || host == "pahe.win") {
                val builder = request.newBuilder()
                    .header("User-Agent", sessions.animeUserAgent())
                sessions.animeCookie().takeIf { it.isNotBlank() }?.let {
                    builder.header("Cookie", it)
                }
                chain.proceed(builder.build())
            } else {
                chain.proceed(request)
            }
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val animeHosts: List<String>
        get() {
            val verifiedHost = sessions.animeHost().takeIf { it.isNotBlank() }
            if (sessions.animeCookie().isNotBlank() && verifiedHost != null) {
                return listOf(verifiedHost)
            }

            return buildList {
                verifiedHost?.let(::add)
                addAll(listOf("animepahe.pw", "animepahe.com", "animepahe.org"))
            }.distinct()
        }

    suspend fun validateAnimeSession(): Boolean = withContext(Dispatchers.IO) {
        val host = sessions.animeHost()
        if (host.isBlank() || sessions.animeCookie().isBlank()) {
            sessions.markAnimeValidated(false)
            throw VerificationRequired(
                VerificationKind.ANIMEPAHE,
                "AnimePahe browser session is missing",
            )
        }

        var verificationError: VerificationRequired? = null
        var lastFailure: Exception? = null
        val probes = listOf(
            "https://$host/api?m=search&q=bleach",
            "https://$host/api?m=airing&page=1",
            "https://$host/",
        )

        for (url in probes) {
            try {
                val body = requestText(url, referer = "https://$host/")
                if (body.isNotBlank()) {
                    sessions.markAnimeValidated(true)
                    return@withContext true
                }
            } catch (e: VerificationRequired) {
                verificationError = e
                lastFailure = e
            } catch (e: Exception) {
                lastFailure = e
            }
        }

        if (verificationError != null) {
            sessions.markAnimeValidated(false)
            throw verificationError
        }
        throw lastFailure ?: IOException("AnimePahe session validation failed")
    }

    suspend fun recentlyAvailable(): List<AnimeSearchResult> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null

        for (host in animeHosts) {
            try {
                var raw = requestAnimeRaw(
                    url = "https://$host/api?m=airing&page=1",
                    referer = "https://$host/",
                )

                if (raw.code == 429) {
                    delay(12_000)
                    raw = requestAnimeRaw(
                        url = "https://$host/api?m=airing&page=1",
                        referer = "https://$host/",
                    )
                }

                val htmlResponse = raw.contentType.contains("text/html", ignoreCase = true)
                if (raw.code == 429 || htmlResponse) {
                    throw IOException("AnimePahe recent releases are temporarily rate limited")
                }
                if (raw.code !in 200..299) {
                    throw IOException("AnimePahe recent releases returned HTTP ${raw.code}")
                }

                val root = JSONObject(raw.body)
                val rows = root.optJSONArray("data") ?: return@withContext emptyList()
                sessions.rememberAnimeHost(host)

                return@withContext buildList {
                    for (index in 0 until rows.length()) {
                        val item = rows.optJSONObject(index) ?: continue
                        val animeId = item.optInt("anime_id", 0)
                        val session = item.optString("anime_session")
                        val title = item.optString("anime_title").trim()
                        if (animeId <= 0 || session.isBlank() || title.isBlank()) continue

                        add(
                            AnimeSearchResult(
                                session = session,
                                title = title,
                                poster = normalizePoster(item.optString("snapshot"), host),
                                type = "Anime",
                                episodes = 0,
                                status = "",
                                animeId = animeId,
                                sourceQueries = listOf(title),
                                catalogNote = "Available now",
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        throw lastError ?: IOException("AnimePahe recent releases are unavailable")
    }

    suspend fun resolveCatalogEpisode(
        catalog: AnimeSearchResult,
        episodeNumber: Double,
        preferredAudio: String,
    ): EpisodeInfo = withContext(Dispatchers.IO) {
        var source = matchCatalogSource(catalog, forceRefresh = false)

        try {
            return@withContext findEpisodeOnSource(source, episodeNumber, preferredAudio)
        } catch (_: VerificationRequired) {
            source = matchCatalogSource(catalog, forceRefresh = true)
            return@withContext findEpisodeOnSource(source, episodeNumber, preferredAudio)
        } catch (_: IOException) {
            source = matchCatalogSource(catalog, forceRefresh = true)
            return@withContext findEpisodeOnSource(source, episodeNumber, preferredAudio)
        }
    }

    private suspend fun matchCatalogSource(
        catalog: AnimeSearchResult,
        forceRefresh: Boolean,
    ): AnimeSearchResult {
        if (!forceRefresh && catalog.animeId != null && catalog.session.isNotBlank()) {
            catalog.aniListId?.let { aniListId ->
                sourceMatches.put(
                    SourceMatch(
                        aniListId = aniListId,
                        animeId = catalog.animeId,
                        session = catalog.session,
                        host = sessions.animeHost().ifBlank { animeHosts.first() },
                        sourceTitle = catalog.title,
                    )
                )
            }
            return catalog
        }

        if (!forceRefresh) {
            catalog.aniListId?.let { aniListId ->
                sourceMatches.get(aniListId)?.let { cached ->
                    return catalog.copy(
                        animeId = cached.animeId,
                        session = cached.session,
                        sourceQueries = (catalog.sourceQueries + cached.sourceTitle).distinct(),
                    )
                }
            }
        }

        val aliases = (catalog.sourceQueries + catalog.title)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)

        var lastError: Exception? = null
        var verificationError: VerificationRequired? = null

        for (host in animeHosts) {
            val candidates = mutableListOf<AnimeSearchResult>()

            for (query in aliases) {
                try {
                    candidates += searchOnHost(host, query)
                } catch (e: VerificationRequired) {
                    verificationError = verificationError ?: e
                    lastError = e
                    break
                } catch (e: Exception) {
                    lastError = e
                }

                val exact = candidates.firstOrNull { candidate ->
                    val normalized = normalizeTitle(candidate.title)
                    aliases.any { alias -> normalizeTitle(alias) == normalized }
                }
                if (exact != null) {
                    sessions.rememberAnimeHost(host)
                    catalog.aniListId?.let { aniListId ->
                        exact.animeId?.let { animeId ->
                            sourceMatches.put(
                                SourceMatch(
                                    aniListId = aniListId,
                                    animeId = animeId,
                                    session = exact.session,
                                    host = host,
                                    sourceTitle = exact.title,
                                )
                            )
                        }
                    }
                    return exact.copy(
                        aniListId = catalog.aniListId,
                        sourceQueries = aliases,
                    )
                }
            }

            val fallback = candidates
                .map { candidate ->
                    val normalized = normalizeTitle(candidate.title)
                    val score = aliases.maxOfOrNull { alias ->
                        val normalizedAlias = normalizeTitle(alias)
                        when {
                            normalized == normalizedAlias -> 3
                            normalized.contains(normalizedAlias) || normalizedAlias.contains(normalized) -> 2
                            else -> 0
                        }
                    } ?: 0
                    score to candidate
                }
                .filter { it.first > 0 }
                .maxByOrNull { it.first }
                ?.second

            if (fallback != null) {
                sessions.rememberAnimeHost(host)
                catalog.aniListId?.let { aniListId ->
                    fallback.animeId?.let { animeId ->
                        sourceMatches.put(
                            SourceMatch(
                                aniListId = aniListId,
                                animeId = animeId,
                                session = fallback.session,
                                host = host,
                                sourceTitle = fallback.title,
                            )
                        )
                    }
                }
                return fallback.copy(
                    aniListId = catalog.aniListId,
                    sourceQueries = aliases,
                )
            }
        }

        verificationError?.let { throw it }
        throw lastError ?: IOException("This AniList title could not be matched on AnimePahe")
    }

    private suspend fun findEpisodeOnSource(
        source: AnimeSearchResult,
        episodeNumber: Double,
        preferredAudio: String,
    ): EpisodeInfo {
        val host = sessions.animeHost().ifBlank { animeHosts.first() }
        val variants = mutableListOf<EpisodeInfo>()
        var page = 1
        var safety = 0

        while (true) {
            val data = releasePage(host, source.session, page)
            val rows = data.optJSONArray("data")
                ?: throw IOException("AnimePahe returned no episode data")

            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val number = item.optDouble("episode", 0.0)
                if (number != episodeNumber) continue

                val epSession = item.optString("session")
                if (epSession.isBlank()) continue

                var audio = item.optString("audio", "jpn").trim().lowercase(Locale.US)
                val title = item.optString("title").let { if (it == "?") "" else it.trim() }
                if (audio == "jpn" && title.contains("dub", true)) audio = "eng"
                if (audio == "eng" && title.contains("sub", true)) audio = "jpn"

                variants += EpisodeInfo(
                    number = number,
                    session = epSession,
                    title = title,
                    fansub = item.optString("fansub").trim(),
                    audio = audio,
                    playUrl = "https://$host/play/${source.session}/$epSession",
                )
            }

            if (variants.isNotEmpty()) {
                return variants.firstOrNull { it.audio == preferredAudio } ?: variants.first()
            }

            val currentPage = data.optInt("current_page", page).coerceAtLeast(page)
            val lastPage = data.optInt("last_page", currentPage).coerceAtLeast(currentPage)
            if (rows.length() == 0 || currentPage >= lastPage) break

            safety++
            if (safety > 60) break
            delay(3_000)
            page = currentPage + 1
        }

        throw IOException("Episode ${episodeNumber.toInt()} is not available on AnimePahe")
    }

    suspend fun search(query: String): List<AnimeSearchResult> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()

        var lastError: Exception? = null
        var verificationError: VerificationRequired? = null
        for (host in animeHosts) {
            try {
                val results = searchOnHost(host, clean)
                sessions.rememberAnimeHost(host)
                return@withContext results
            } catch (e: VerificationRequired) {
                verificationError = verificationError ?: e
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
        }
        verificationError?.let { throw it }
        throw lastError ?: IOException("No AnimePahe host responded")
    }

    suspend fun loadAnime(result: AnimeSearchResult): AnimeDetails = withContext(Dispatchers.IO) {
        val host = sessions.animeHost().ifBlank { animeHosts.first() }
        val normalizedAliases = (result.sourceQueries + result.title)
            .filter { it.isNotBlank() }
            .map(::normalizeTitle)
            .filter { it.isNotBlank() }
            .distinct()

        val variants = mutableListOf<AnimeSearchResult>()
        var searchVerification: VerificationRequired? = null
        var searchFailure: Exception? = null

        val queries = (result.sourceQueries + result.title)
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)

        for (query in queries) {
            try {
                variants += searchOnHost(host, query)
            } catch (e: VerificationRequired) {
                searchVerification = e
                break
            } catch (e: Exception) {
                searchFailure = e
            }

            val exactFound = variants.any { candidate ->
                val normalized = normalizeTitle(candidate.title)
                normalizedAliases.any { alias -> normalized == alias }
            }
            if (exactFound) break
        }

        if (result.session.isBlank() && variants.isEmpty()) {
            searchVerification?.let { throw it }
            searchFailure?.let { throw it }
        }

        val refreshed = variants.firstOrNull { candidate ->
            result.animeId != null && candidate.animeId == result.animeId
        } ?: variants.firstOrNull { candidate ->
            val normalized = normalizeTitle(candidate.title)
            normalizedAliases.any { alias -> normalized == alias }
        } ?: variants.firstOrNull { candidate ->
            val normalized = normalizeTitle(candidate.title)
            normalizedAliases.any { alias ->
                normalized.contains(alias) || alias.contains(normalized)
            }
        } ?: result.takeIf { it.session.isNotBlank() }

        val sourceResult = refreshed
            ?: throw IOException("Could not match '${result.title}' on AnimePahe")

        val displayResult = result.copy(
            session = sourceResult.session,
            animeId = sourceResult.animeId,
            poster = result.poster.ifBlank { sourceResult.poster },
            type = result.type.ifBlank { sourceResult.type },
            episodes = result.episodes.takeIf { it > 0 } ?: sourceResult.episodes,
            status = result.status.ifBlank { sourceResult.status },
        )

        val candidates = buildList {
            add(sourceResult)
            variants.asSequence()
                .filter { candidate ->
                    val normalized = normalizeTitle(candidate.title)
                    normalizedAliases.any { alias ->
                        normalized == alias || normalized.contains(alias) || alias.contains(normalized)
                    }
                }
                .forEach(::add)
            if (result.session.isNotBlank()) add(result)
        }.filter { it.session.isNotBlank() }
            .distinctBy { it.session }

        var firstVerificationError: VerificationRequired? = null
        var lastFailure: Exception? = null

        for (candidate in candidates) {
            val animeSession = candidate.session
            val complete = linkedMapOf<Pair<Double, String>, EpisodeInfo>()

            try {
                var page = 1
                var declaredLastPage = 1
                var expectedEpisodeCount = maxOf(
                    displayResult.episodes,
                    sourceResult.episodes,
                    candidate.episodes,
                )
                var safetyPages = 0

                while (true) {
                    val data = releasePage(host, animeSession, page)
                    val rows = data.optJSONArray("data")
                        ?: throw IOException("AnimePahe release page $page returned no episode data")

                    expectedEpisodeCount = maxOf(
                        expectedEpisodeCount,
                        data.optInt("total", 0),
                    )

                    for (index in 0 until rows.length()) {
                        val item = rows.optJSONObject(index) ?: continue
                        val epSession = item.optString("session")
                        if (epSession.isBlank()) continue

                        var audio = item.optString("audio", "jpn").trim().lowercase(Locale.US)
                        val title = item.optString("title").let { if (it == "?") "" else it.trim() }
                        if (audio == "jpn" && title.contains("dub", true)) audio = "eng"
                        if (audio == "eng" && title.contains("sub", true)) audio = "jpn"

                        val number = item.optDouble("episode", 0.0)
                        if (number <= 0.0) continue
                        val ep = EpisodeInfo(
                            number = number,
                            session = epSession,
                            title = title,
                            fansub = item.optString("fansub").trim(),
                            audio = audio,
                            playUrl = "https://$host/play/$animeSession/$epSession",
                        )
                        complete.putIfAbsent(number to audio, ep)
                    }

                    val currentPage = data.optInt("current_page", page).coerceAtLeast(page)
                    declaredLastPage = maxOf(
                        declaredLastPage,
                        data.optInt("last_page", currentPage).coerceAtLeast(currentPage),
                    )

                    val distinctEpisodes = complete.keys.map { it.first }.distinct().size

                    if (expectedEpisodeCount > 0) {
                        if (distinctEpisodes >= expectedEpisodeCount) break
                        if (rows.length() == 0) {
                            throw IOException(
                                "AnimePahe episode list stopped early on page $currentPage " +
                                    "with $distinctEpisodes of $expectedEpisodeCount episodes"
                            )
                        }
                    } else if (currentPage >= declaredLastPage) {
                        break
                    }

                    safetyPages++
                    if (safetyPages > 60) {
                        throw IOException(
                            "AnimePahe pagination exceeded the safety limit with " +
                                "$distinctEpisodes of $expectedEpisodeCount episodes"
                        )
                    }

                    delay(3_000)
                    page = currentPage + 1
                }

                if (complete.isNotEmpty()) {
                    return@withContext AnimeDetails(
                        result = displayResult,
                        host = host,
                        episodes = complete.values.sortedWith(
                            compareBy<EpisodeInfo> { it.number }.thenBy { it.audio },
                        ),
                    )
                }
            } catch (e: VerificationRequired) {
                if (firstVerificationError == null) firstVerificationError = e
                lastFailure = e
            } catch (e: Exception) {
                lastFailure = e
            }
        }

        firstVerificationError?.let { throw it }
        throw lastFailure ?: IOException("AnimePahe returned no complete episode list")
    }

    suspend fun resolveStream(
        episode: EpisodeInfo,
        requestedQuality: Int,
        requestedAudio: String,
    ): StreamInfo = withContext(Dispatchers.IO) {
        val host = URI(episode.playUrl).host.orEmpty()
        val playHtml = requestText(episode.playUrl, referer = "https://$host/")
        val options = parseReleaseOptions(playHtml)
        if (options.isEmpty()) throw IOException("No stream release was found on this episode")

        val wantsDub = requestedAudio == "eng"
        val matchingAudio = options.filter { it.isDub == wantsDub }.ifEmpty { options }
        val sorted = matchingAudio.sortedByDescending { it.resolution }
        val chosen = sorted.firstOrNull { it.resolution <= requestedQuality } ?: sorted.last()

        resolveKwik(chosen.url, chosen)
            ?: throw IOException("Stream page loaded, but no HLS URL could be extracted")
    }

    suspend fun downloadStream(
        stream: StreamInfo,
        animeTitle: String,
        episode: EpisodeInfo,
        downloadKey: String,
        onProgress: (Float) -> Unit,
    ): Uri = withContext(Dispatchers.IO) {
        val headers = linkedMapOf(
            "User-Agent" to stream.userAgent,
            "Referer" to stream.referer,
        )
        if (stream.cookie.isNotBlank()) headers["Cookie"] = stream.cookie

        var manifestUrl = stream.url
        var manifest = requestBytes(manifestUrl, headers).toString(Charsets.UTF_8)
        val variants = masterVariants(manifest, manifestUrl)
        if (variants.isNotEmpty()) {
            manifestUrl = variants.last()
            manifest = requestBytes(manifestUrl, headers).toString(Charsets.UTF_8)
        }

        val segments = parseSegments(manifest, manifestUrl)
        if (segments.isEmpty()) throw IOException("The HLS playlist did not contain any media segments")

        val expectedDurationUs = segments
            .sumOf { (it.durationSeconds * 1_000_000.0).toLong() }
            .coerceAtLeast(0L)

        val tempDir = File(context.filesDir, "pahe_downloads/$downloadKey")
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            throw IOException("Could not create persistent download workspace")
        }

        val marker = File(tempDir, "manifest.meta")
        val markerValue = listOf(
            segments.size.toString(),
            expectedDurationUs.toString(),
            stream.quality.toString(),
            stream.audio,
        ).joinToString("|")

        if (marker.takeIf { it.exists() }?.readText().orEmpty() != markerValue) {
            tempDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".ts") ||
                    file.name.endsWith(".part") ||
                    file.name == "joined.ts" ||
                    file.name == "episode.mp4"
                ) {
                    file.delete()
                }
            }
            marker.writeText(markerValue)
        }

        val segmentFiles = segments.indices.map { index ->
            File(tempDir, "%06d.ts".format(index))
        }

        val existingCount = segmentFiles.count { it.exists() && it.length() > 0L }
        onProgress(existingCount.toFloat() / segments.size.toFloat())

        val missingIndexes = segments.indices.filter { index ->
            val file = segmentFiles[index]
            !file.exists() || file.length() <= 0L
        }

        if (missingIndexes.isNotEmpty()) {
            val keyCache = missingIndexes
                .mapNotNull { index -> segments[index].keyUrl }
                .distinct()
                .associateWith { keyUrl -> requestBytes(keyUrl, headers) }

            val limiter = Semaphore(6)
            val completed = AtomicInteger(existingCount)
            coroutineScope {
                missingIndexes.map { index ->
                    async {
                        limiter.withPermit {
                            val segment = segments[index]
                            var bytes = requestBytes(segment.url, headers)
                            if (segment.keyUrl != null && segment.iv != null) {
                                val key = keyCache[segment.keyUrl]
                                    ?: throw IOException("Missing AES key for HLS segment")
                                bytes = decryptAes128(bytes, key, segment.iv)
                            }

                            val finalFile = segmentFiles[index]
                            val partFile = File(tempDir, "%06d.part".format(index))
                            partFile.writeBytes(bytes)
                            if (finalFile.exists()) finalFile.delete()
                            if (!partFile.renameTo(finalFile)) {
                                partFile.copyTo(finalFile, overwrite = true)
                                partFile.delete()
                            }

                            val done = completed.incrementAndGet()
                            onProgress(done.toFloat() / segments.size.toFloat())
                        }
                    }
                }.awaitAll()
            }
        }

        val completedFiles = segmentFiles.filter { it.exists() && it.length() > 0L }
        if (completedFiles.size != segments.size) {
            throw IOException(
                "Only ${completedFiles.size} of ${segments.size} HLS segments are available; " +
                    "the download will resume when connectivity returns"
            )
        }

        val joinedTs = File(tempDir, "joined.ts")
        if (joinedTs.exists()) joinedTs.delete()
        joinedTs.outputStream().buffered().use { out ->
            completedFiles.forEach { file ->
                file.inputStream().buffered().use { it.copyTo(out) }
            }
        }

        val inputBytes = completedFiles.sumOf { it.length() }
        if (joinedTs.length() < inputBytes) {
            throw IOException("The joined episode file is incomplete")
        }

        val remuxedMp4 = File(tempDir, "episode.mp4")
        if (remuxedMp4.exists()) remuxedMp4.delete()
        val mp4Ready = remuxSegmentsToMp4(
            segmentFiles = completedFiles,
            segmentDurationsUs = segments.map { (it.durationSeconds * 1_000_000.0).toLong() },
            output = remuxedMp4,
            expectedDurationUs = expectedDurationUs,
            expectedInputBytes = inputBytes,
        )
        val sourceFile = if (mp4Ready) remuxedMp4 else joinedTs
        val extension = if (mp4Ready) "mp4" else "ts"
        val mime = if (mp4Ready) "video/mp4" else "video/mp2t"

        val safeTitle = sanitize(animeTitle).ifBlank { "Anime" }
        val safeEpisode = episode.epLabel.replace(".", "_")
        val suffix = if (stream.audio == "eng") "_DUB" else ""
        val displayName = "$safeTitle - Ep $safeEpisode$suffix - ${stream.quality}p.$extension"

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveModernDownload(sourceFile, displayName, mime)
        } else {
            saveLegacyDownload(sourceFile, displayName)
        }

        tempDir.deleteRecursively()
        uri
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    private fun saveModernDownload(sourceFile: File, displayName: String, mime: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PaheBatcher")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Android could not create the output file")

        try {
            resolver.openOutputStream(uri, "w")?.use { out ->
                sourceFile.inputStream().buffered().use { it.copyTo(out) }
            } ?: throw IOException("Android could not open the output file")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacyDownload(sourceFile: File, displayName: String): Uri {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val outputDir = File(downloads, "PaheBatcher")
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw IOException("Could not create Downloads/PaheBatcher")
        }

        val output = File(outputDir, displayName)
        sourceFile.inputStream().buffered().use { input ->
            output.outputStream().buffered().use { out -> input.copyTo(out) }
        }
        return Uri.fromFile(output)
    }

    private fun searchOnHost(host: String, query: String): List<AnimeSearchResult> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val body = requestText(
            "https://$host/api?m=search&q=$encoded",
            referer = "https://$host/",
        )
        val root = JSONObject(body)
        val rows = root.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val session = item.optString("session")
                val title = item.optString("title")
                if (session.isBlank() || title.isBlank()) continue
                add(
                    AnimeSearchResult(
                        session = session,
                        title = title,
                        poster = normalizePoster(item.optString("poster"), host),
                        type = item.optString("type", "Anime"),
                        episodes = item.optInt("episodes", 0),
                        status = item.optString("status"),
                        animeId = item.optInt("id", 0).takeIf { it > 0 },
                    )
                )
            }
        }
    }

    private suspend fun releasePage(
        host: String,
        session: String,
        page: Int,
    ): JSONObject {
        val urls = listOf(
            "https://$host/api?m=release&id=$session&sort=episode_asc&page=$page",
            "https://$host/api/$session/releases?sort=episode_asc&page=$page",
        )

        var verification: VerificationRequired? = null
        var lastFailure: Exception? = null
        var emptyResponse: JSONObject? = null

        for (url in urls) {
            var retriedRateLimit = false
            while (true) {
                try {
                    val data = JSONObject(requestText(url, referer = "https://$host/"))
                    val rows = data.optJSONArray("data")
                    if (rows != null && rows.length() > 0) {
                        return data
                    }
                    emptyResponse = data
                    break
                } catch (e: VerificationRequired) {
                    verification = verification ?: e
                    lastFailure = e
                    break
                } catch (e: IOException) {
                    lastFailure = e
                    if (!retriedRateLimit && e.message.orEmpty().contains("HTTP 429")) {
                        retriedRateLimit = true
                        delay(12_000)
                        continue
                    }
                    break
                } catch (e: Exception) {
                    lastFailure = e
                    break
                }
            }
        }

        emptyResponse?.let { return it }
        verification?.let { throw it }
        throw lastFailure ?: IOException("AnimePahe release API returned no usable response")
    }

    private fun parseReleaseOptions(html: String): List<ReleaseOption> {
        val doc = Jsoup.parse(html)
        val options = mutableListOf<ReleaseOption>()
        val buttons = doc.select("#resolutionMenu button[data-src], button[data-src]")
        for (button in buttons) {
            val url = button.attr("data-src").trim()
            if (!url.contains("kwik.", true)) continue
            val resolution = button.attr("data-resolution").toIntOrNull()
                ?: Regex("""(\d+)\s*p""", RegexOption.IGNORE_CASE)
                    .find(button.text())?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: continue
            val attrs = button.outerHtml()
            val isDub =
                button.attr("data-audio").equals("eng", true) ||
                button.attr("class").contains("eng", true) ||
                attrs.contains("dub", true)
            val fansub = button.attr("data-fansub").ifBlank {
                button.text().substringBefore("·").trim()
            }
            options += ReleaseOption(resolution, url, isDub, fansub)
        }

        if (options.isNotEmpty()) return options.distinctBy { it.resolution to it.url }

        val fallback = Regex(
            """(?:href|data-src)=["']([^"']*kwik\.[^"']+)["'][^>]*>\s*(?:\S+\s+)?(\d+)p""",
            RegexOption.IGNORE_CASE,
        )
        return fallback.findAll(html).mapNotNull { match ->
            val url = match.groupValues.getOrNull(1).orEmpty()
            val res = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            ReleaseOption(res, url, url.contains("dub", true) || url.contains("eng", true), "")
        }.toList()
    }

    private fun resolveKwik(
        initialUrl: String,
        chosen: ReleaseOption,
    ): StreamInfo? {
        val candidates = buildList {
            add(initialUrl)
            val currentTld = runCatching {
                URI(initialUrl).host.orEmpty().substringAfterLast(".")
            }.getOrDefault("")
            for (tld in listOf("cx", "gg", "si", "me", "net", "in", "cc")) {
                if (tld != currentTld) swapKwikDomain(initialUrl, tld)?.let(::add)
            }
        }.distinct()

        for (url in candidates) {
            try {
                val html = requestText(url, referer = animeReferer())
                val hls = extractM3u8(html) ?: continue
                return StreamInfo(
                    url = hls,
                    cookie = "",
                    userAgent = sessions.animeUserAgent(),
                    referer = url,
                    quality = chosen.resolution,
                    audio = if (chosen.isDub) "eng" else "jpn",
                    fansub = chosen.fansub,
                )
            } catch (_: Exception) {
                // Provider/session cookies are captured automatically by the
                // runtime CookieJar. There is never a second manual verify step.
            }
        }
        return null
    }

    private fun requestAnimeRaw(
        url: String,
        referer: String? = null,
    ): PaheRawResponse {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", sessions.animeUserAgent())
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept", "text/html,application/json,application/vnd.apple.mpegurl")
        referer?.let { builder.header("Referer", it) }
        sessions.animeCookie().takeIf { it.isNotBlank() }?.let {
            builder.header("Cookie", it)
        }

        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return PaheRawResponse(
                code = response.code,
                contentType = response.header("Content-Type").orEmpty(),
                body = body,
            )
        }
    }

    private fun requestText(url: String, referer: String? = null): String {
        return requestBytes(url, buildMap {
            put("Accept", "text/html,application/json,application/vnd.apple.mpegurl")
            referer?.let { put("Referer", it) }
        }).toString(Charsets.UTF_8)
    }

    private fun requestBytes(url: String, extraHeaders: Map<String, String> = emptyMap()): ByteArray {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", sessions.userAgentFor(url))
            .header("Accept-Language", "en-US,en;q=0.9")
        extraHeaders.forEach { (key, value) -> builder.header(key, value) }
        if ("Cookie" !in extraHeaders) {
            sessions.cookieFor(url).takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
        }

        client.newCall(builder.build()).execute().use { response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            val preview = bytes.toString(Charsets.UTF_8)
            val verificationKind = kindFor(url)
            if (
                verificationKind != null &&
                (response.code == 403 || response.code == 503 || looksLikeChallenge(preview))
            ) {
                if (verificationKind == VerificationKind.ANIMEPAHE) {
                    sessions.markAnimeValidated(false)
                }
                throw VerificationRequired(
                    verificationKind,
                    "AnimePahe needs browser verification",
                )
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} from ${URI(url).host}")
            }
            return bytes
        }
    }

    private fun looksLikeChallenge(body: String): Boolean {
        if (body.length > 500_000) return false

        val document = Jsoup.parse(body)
        val title = document.title().trim()
        val visibleText = document.body()?.text().orEmpty().take(2_000)

        val challengeTitle =
            title.contains("Just a moment", true) ||
                title.contains("Attention Required", true)

        val challengeText =
            visibleText.contains("Checking your browser", true) ||
                visibleText.contains("Verify you are human", true) ||
                visibleText.contains("Performing security verification", true) ||
                visibleText.contains("Enable JavaScript and cookies to continue", true)

        val cloudflareShell =
            body.contains("cf-chl-", true) ||
                body.contains("challenge-platform", true) ||
                body.contains("cf-turnstile", true)

        return challengeTitle || (cloudflareShell && challengeText)
    }

    private fun kindFor(url: String): VerificationKind? {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.US) }.getOrDefault("")
        return when {
            host.contains("animepahe") || host == "pahe.win" -> VerificationKind.ANIMEPAHE
            else -> null
        }
    }

    private fun animeReferer(): String {
        val host = sessions.animeHost().ifBlank { animeHosts.first() }
        return "https://$host/"
    }

    private fun extractM3u8(html: String): String? {
        val direct = M3U8_REGEX.find(html)?.groupValues?.getOrNull(1)
        if (!direct.isNullOrBlank()) return cleanJsUrl(direct)

        val doc = Jsoup.parse(html)
        val scripts = doc.select("script")
            .map { script -> script.data().ifBlank { script.html() } }
            .sortedByDescending { it.length }

        for (script in scripts) {
            var current = script
            repeat(6) {
                val inner = INNER_EVAL_REGEX.find(current)?.groupValues?.getOrNull(1)
                if (!inner.isNullOrBlank()) {
                    current = decodeJsEscapes(inner)
                } else {
                    val unpacked = unpackJs(current)
                    if (unpacked != current) current = unpacked
                }
            }
            M3U8_REGEX.find(current)?.groupValues?.getOrNull(1)?.let {
                return cleanJsUrl(it)
            }
        }

        val source = Regex(
            """<source[^>]+src=["']([^"']+\.m3u8[^"']*)["']""",
            RegexOption.IGNORE_CASE,
        ).find(html)?.groupValues?.getOrNull(1)
        return source?.let(::cleanJsUrl)
    }

    private fun unpackJs(packed: String): String {
        val match = PACKER_REGEX.find(packed) ?: return packed
        val payload = match.groupValues[1]
        val base = match.groupValues[2].toIntOrNull() ?: return packed
        val count = match.groupValues[3].toIntOrNull() ?: return packed
        val mapping = match.groupValues[4].split("|")
        val digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        if (base !in 2..digits.length) return packed

        fun encode(value: Int): String {
            return if (value < base) {
                digits[value].toString()
            } else {
                encode(value / base) + digits[value % base]
            }
        }

        val lookup = HashMap<String, String>()
        for (index in 0 until count) {
            val encoded = encode(index)
            lookup[encoded] = mapping.getOrNull(index)?.takeIf { it.isNotEmpty() } ?: encoded
        }
        return Regex("""\b\w+\b""").replace(payload) { word ->
            lookup[word.value] ?: word.value
        }
    }

    private fun decodeJsEscapes(value: String): String {
        var result = value
            .replace("\\/", "/")
            .replace("\\'", "'")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")

        result = Regex("""\\u([0-9a-fA-F]{4})""").replace(result) {
            it.groupValues[1].toInt(16).toChar().toString()
        }
        result = Regex("""\\x([0-9a-fA-F]{2})""").replace(result) {
            it.groupValues[1].toInt(16).toChar().toString()
        }
        return result
    }

    private fun cleanJsUrl(url: String): String =
        url.replace("\\/", "/").trimEnd('\\')

    private fun masterVariants(content: String, baseUrl: String): List<String> {
        val lines = content.lines()
        val out = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            if (line.trim().startsWith("#EXT-X-STREAM-INF") && index + 1 < lines.size) {
                val next = lines[index + 1].trim()
                if (next.isNotBlank() && !next.startsWith("#")) out += resolveUrl(baseUrl, next)
            }
        }
        return out
    }

    private fun parseSegments(content: String, baseUrl: String): List<Segment> {
        val out = mutableListOf<Segment>()
        var keyUrl: String? = null
        var explicitIv: ByteArray? = null
        var sequence = 0L
        var pendingDuration = 0.0

        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    sequence = line.substringAfter(":").toLongOrNull() ?: sequence
                }
                line.startsWith("#EXTINF:") -> {
                    pendingDuration = line
                        .substringAfter(":")
                        .substringBefore(",")
                        .toDoubleOrNull()
                        ?.coerceAtLeast(0.0)
                        ?: 0.0
                }
                line.startsWith("#EXT-X-KEY:") -> {
                    if (line.contains("METHOD=AES-128", true)) {
                        val uri = Regex("""URI="([^"]+)"""").find(line)?.groupValues?.getOrNull(1)
                        keyUrl = uri?.let { resolveUrl(baseUrl, it) }
                        val ivHex = Regex("""IV=0x([0-9a-fA-F]+)""").find(line)?.groupValues?.getOrNull(1)
                        explicitIv = ivHex?.let(::hexIv)
                    } else {
                        keyUrl = null
                        explicitIv = null
                    }
                }
                line.isNotBlank() && !line.startsWith("#") -> {
                    val iv = if (keyUrl != null) explicitIv ?: sequenceIv(sequence) else null
                    out += Segment(
                        url = resolveUrl(baseUrl, line),
                        keyUrl = keyUrl,
                        iv = iv,
                        durationSeconds = pendingDuration,
                    )
                    pendingDuration = 0.0
                    sequence++
                }
            }
        }
        return out
    }

    private fun remuxSegmentsToMp4(
        segmentFiles: List<File>,
        segmentDurationsUs: List<Long>,
        output: File,
        expectedDurationUs: Long,
        expectedInputBytes: Long,
    ): Boolean {
        if (segmentFiles.isEmpty()) return false

        var muxer: MediaMuxer? = null
        var muxerStarted = false

        return try {
            // Discover stable audio/video formats from the first usable segment.
            val formatByMime = linkedMapOf<String, android.media.MediaFormat>()
            for (file in segmentFiles) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(file.absolutePath)
                    for (track in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(track)
                        val mime = format.getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                        if ((mime.startsWith("video/") || mime.startsWith("audio/")) &&
                            mime !in formatByMime
                        ) {
                            formatByMime[mime] = format
                        }
                    }
                } finally {
                    runCatching { extractor.release() }
                }
                if (formatByMime.keys.any { it.startsWith("video/") } &&
                    formatByMime.keys.any { it.startsWith("audio/") }
                ) {
                    break
                }
            }

            if (formatByMime.isEmpty()) return false

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxTrackByMime = formatByMime.mapValues { (_, format) -> muxer.addTrack(format) }
            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            val lastPtsByTrack = mutableMapOf<Int, Long>()
            var segmentBaseUs = 0L

            segmentFiles.forEachIndexed { index, file ->
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(file.absolutePath)
                    val sourceToMux = mutableMapOf<Int, Int>()
                    for (track in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(track)
                        val mime = format.getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                        val muxTrack = muxTrackByMime[mime] ?: continue
                        sourceToMux[track] = muxTrack
                        extractor.selectTrack(track)
                    }

                    if (sourceToMux.isEmpty()) {
                        throw IOException("HLS segment ${index + 1} had no muxable audio/video tracks")
                    }

                    val firstSampleUs = extractor.sampleTime.coerceAtLeast(0L)
                    var maxRelativeUs = 0L

                    while (true) {
                        val sourceTrack = extractor.sampleTrackIndex
                        if (sourceTrack < 0) break

                        val muxTrack = sourceToMux[sourceTrack]
                        if (muxTrack == null) {
                            extractor.advance()
                            continue
                        }

                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break

                        val relativeUs = (extractor.sampleTime - firstSampleUs).coerceAtLeast(0L)
                        var outputPtsUs = segmentBaseUs + relativeUs
                        val previousPts = lastPtsByTrack[muxTrack]
                        if (previousPts != null && outputPtsUs <= previousPts) {
                            outputPtsUs = previousPts + 1L
                        }

                        info.set(
                            0,
                            size,
                            outputPtsUs,
                            extractor.sampleFlags,
                        )
                        muxer.writeSampleData(muxTrack, buffer, info)
                        lastPtsByTrack[muxTrack] = outputPtsUs
                        maxRelativeUs = maxOf(maxRelativeUs, relativeUs)
                        extractor.advance()
                    }

                    val manifestDurationUs = segmentDurationsUs
                        .getOrNull(index)
                        ?.coerceAtLeast(0L)
                        ?: 0L
                    segmentBaseUs += if (manifestDurationUs > 0L) {
                        manifestDurationUs
                    } else {
                        maxRelativeUs + 50_000L
                    }
                } finally {
                    runCatching { extractor.release() }
                }
            }

            muxer.stop()
            muxerStarted = false
            muxer.release()
            muxer = null

            if (!output.exists() || output.length() == 0L) return false

            if (expectedInputBytes > 0L && output.length() < (expectedInputBytes * 60L / 100L)) {
                output.delete()
                return false
            }

            if (expectedDurationUs > 5_000_000L) {
                val actualDurationUs = mediaDurationUs(output)
                if (actualDurationUs <= 0L || actualDurationUs < (expectedDurationUs * 8L / 10L)) {
                    output.delete()
                    return false
                }
            }

            true
        } catch (_: Exception) {
            false
        } finally {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            if (!output.exists() || output.length() == 0L) output.delete()
        }
    }

    private fun mediaDurationUs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            durationMs * 1_000L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decryptAes128(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun sequenceIv(sequence: Long): ByteArray {
        val iv = ByteArray(16)
        var value = sequence
        for (index in 15 downTo 0) {
            iv[index] = (value and 0xFF).toByte()
            value = value ushr 8
        }
        return iv
    }

    private fun hexIv(hex: String): ByteArray {
        val padded = hex.padStart(32, '0').takeLast(32)
        return ByteArray(16) { index ->
            padded.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun resolveUrl(base: String, child: String): String =
        URI(base).resolve(child).toString()

    private fun swapKwikDomain(url: String, tld: String): String? {
        return runCatching {
            val uri = URI(url)
            val host = uri.host ?: return@runCatching null
            val parts = host.split(".").toMutableList()
            if (parts.size < 2) return@runCatching null
            parts[parts.lastIndex] = tld
            URI(uri.scheme, uri.userInfo, parts.joinToString("."), uri.port, uri.path, uri.query, uri.fragment)
                .toString()
        }.getOrNull()
    }

    private fun normalizePoster(value: String, host: String): String = when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "https://$host$value"
        value.startsWith("http") -> value
        value.isBlank() -> ""
        else -> "https://$host/$value"
    }

    private fun normalizeTitle(value: String): String =
        value.lowercase(Locale.US)
            .replace(Regex("""[^\p{L}\p{N}\s]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun sanitize(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(120)

    companion object {
        private val M3U8_REGEX = Regex(
            """(https?://[^\s'"\\>]+(?:uwu\.m3u8|\.m3u8)[^\s'"\\>]*)""",
            setOf(RegexOption.IGNORE_CASE),
        )
        private val INNER_EVAL_REGEX = Regex(
            """eval\s*\(\s*"((?:[^"\\]|\\.)*)"\s*\)""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        private val PACKER_REGEX = Regex(
            """\}\s*\(\s*'(.*)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'(.*?)'\.split\('\|'\)""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
    }
}
