package app.nami.source.kayoanime

import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeEpisode
import app.nami.domain.AnimeRef
import app.nami.domain.AnimeSearchResult
import app.nami.domain.EpisodeRef
import app.nami.domain.ResolvedMedia
import app.nami.source.NAMI_EXTENSION_API_VERSION
import app.nami.source.NamiAnimeSource
import app.nami.source.NamiSourceErrorKind
import app.nami.source.NamiSourceException
import app.nami.source.SourceCapabilities
import app.nami.source.SourceMetadata
import app.nami.source.SourceOrigin
import app.nami.source.SourcePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.URLEncoder

/** First-party KayoAnime source implemented against Nami's stable source API. */
class KayoAnimeSource(
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
    private val packagedAsExtension: Boolean = true,
) : NamiAnimeSource {

    override val metadata = SourceMetadata(
        // Preserve the pre-port source key so existing library, history and download rows remain
        // attached to KayoAnime when the extension moves onto Nami's API.
        id = "$EXTENSION_ID:en",
        name = "KayoAnime",
        language = "en",
        origin = SourceOrigin.NATIVE_NAMI,
        extensionName = "KayoAnime",
        homeUrl = BASE_URL,
        capabilities = SourceCapabilities(
            browsable = true,
            popular = true,
            latest = true,
            downloadable = true,
        ),
        extensionPackage = if (packagedAsExtension) EXTENSION_ID else null,
        extensionVersion = if (packagedAsExtension) EXTENSION_VERSION else null,
        extensionApiVersion = if (packagedAsExtension) NAMI_EXTENSION_API_VERSION else null,
    )

    override suspend fun search(query: String, page: Int): SourcePage<AnimeSearchResult> {
        if (query.isBlank()) return SourcePage(emptyList(), hasNextPage = false)
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = if (page <= 1) {
            "$BASE_URL/?s=$encoded"
        } else {
            "$BASE_URL/page/$page/?s=$encoded"
        }
        return fetchListing(url, query.trim())
    }

    override suspend fun popular(page: Int): SourcePage<AnimeSearchResult> =
        fetchListing(pageUrl(page), query = null)

    override suspend fun latest(page: Int): SourcePage<AnimeSearchResult> =
        fetchListing(pageUrl(page), query = null)

    override suspend fun details(anime: AnimeRef): AnimeDetails =
        details(anime, sourceState = null)

    override suspend fun details(anime: AnimeRef, sourceState: String?): AnimeDetails {
        val url = sourceState?.takeIf(::isKayoUrl) ?: absolute(anime.sourceAnimeId)
        val document = loadDocument(url, referer = BASE_URL)
        val pageTitle = document.selectFirst("h1")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: document.title().substringBefore(" - Kayoanime").trim()
        if (pageTitle.isBlank()) {
            throw NamiSourceException(
                kind = NamiSourceErrorKind.NOT_FOUND,
                message = "KayoAnime did not return anime details for this page.",
            )
        }

        return AnimeDetails(
            ref = AnimeRef(metadata.id, url),
            title = pageTitle,
            coverUrl = document.selectFirst("meta[property=og:image]")
                ?.attr("content")
                ?.takeIf { it.isNotBlank() },
            description = document.select("article p, .entry-content p")
                .map { it.text().trim() }
                .filter { it.length >= 20 }
                .take(4)
                .joinToString("\n\n")
                .takeIf { it.isNotBlank() },
            sourceState = url,
        )
    }

    override suspend fun episodes(anime: AnimeRef): List<AnimeEpisode> =
        episodes(anime, sourceState = null)

    override suspend fun episodes(
        anime: AnimeRef,
        sourceState: String?,
    ): List<AnimeEpisode> {
        val animeUrl = sourceState?.takeIf(::isKayoUrl) ?: absolute(anime.sourceAnimeId)
        val document = loadDocument(animeUrl, referer = BASE_URL)
        val driveAnchors = document.select("a[href*=\"drive.google.com\"]")
            .mapNotNull { anchor ->
                val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                href.takeIf { it.contains("drive.google.com") }
                    ?.let { it to anchor.text().trim() }
            }
            .distinctBy { it.first }

        val discovered = mutableListOf<DriveFile>()
        val folderFailures = mutableListOf<Throwable>()
        val seenFolders = linkedSetOf<String>()
        for ((href, label) in driveAnchors) {
            val fileId = extractDriveFileId(href)
            if (fileId != null) {
                discovered += DriveFile(fileId, label.ifBlank { "KayoAnime file" })
                continue
            }

            val folderId = extractDriveFolderId(href) ?: continue
            try {
                discovered += listDriveFolder(
                    folderId = folderId,
                    prefix = label.takeIf { it.isNotBlank() },
                    seenFolders = seenFolders,
                    depth = 0,
                )
            } catch (failure: NamiSourceException) {
                folderFailures += failure
            }
        }

        val playable = discovered
            .distinctBy { it.id }
            .filter { file -> file.extension.lowercase() in PLAYABLE_EXTENSIONS }
            .sortedWith(
                compareBy<DriveFile> { episodeNumber(it.name) ?: Float.MAX_VALUE }
                    .thenBy { it.name.lowercase() },
            )

        if (playable.isEmpty() && folderFailures.isNotEmpty()) throw folderFailures.first()

        return playable.mapIndexed { index, file ->
            val episodeId = encodeEpisode(file)
            AnimeEpisode(
                ref = EpisodeRef(
                    sourceId = metadata.id,
                    sourceAnimeId = anime.sourceAnimeId,
                    sourceEpisodeId = episodeId,
                ),
                title = file.name,
                number = episodeNumber(file.name)?.toDouble() ?: (index + 1).toDouble(),
                sourceState = episodeId,
            )
        }
    }

    override suspend fun resolve(episode: EpisodeRef): List<ResolvedMedia> =
        resolve(episode, sourceState = null)

    override suspend fun resolve(
        episode: EpisodeRef,
        sourceState: String?,
    ): List<ResolvedMedia> {
        val file = sourceState?.let(::decodeEpisode)
            ?: decodeEpisode(episode.sourceEpisodeId)
            ?: throw NamiSourceException(
                kind = NamiSourceErrorKind.STREAM_UNAVAILABLE,
                message = "KayoAnime could not resolve this Google Drive episode.",
            )
        val extension = file.extension.lowercase()
        val height = Regex("""(?i)(2160|1440|1080|720|480)p""")
            .find(file.name)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val headers = mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
        )

        return listOf(
            ResolvedMedia(
                url = "https://drive.google.com/uc?export=download&id=${file.id}",
                mimeType = when (extension) {
                    "mkv" -> "video/x-matroska"
                    "webm" -> "video/webm"
                    "m4v" -> "video/mp4"
                    "mp4" -> "video/mp4"
                    else -> null
                },
                quality = height?.let { "${it}p" },
                headers = headers,
                hosterName = "Google Drive",
            ),
        )
    }

    private suspend fun fetchListing(
        url: String,
        query: String?,
    ): SourcePage<AnimeSearchResult> {
        val document = loadDocument(url, referer = BASE_URL)
        val results = parseListing(document, query)
        val hasNext = document.selectFirst("a[rel=next], a.next, .pagination a.next") != null
        return SourcePage(results, hasNextPage = hasNext)
    }

    private fun parseListing(
        document: Document,
        query: String?,
    ): List<AnimeSearchResult> {
        val anchors = document.select(
            "article h1 a, article h2 a, article h3 a, " +
                ".item-list h2 a, .item-list h3 a, .post-box-title a, .entry-title a",
        ).toMutableList()

        if (anchors.isEmpty() && !query.isNullOrBlank()) {
            val tokens = query.replace(":", " ")
                .split(Regex("""\s+"""))
                .filter { it.length >= 3 }
            anchors += document.select("a[href]").filter { anchor ->
                tokens.any { token -> anchor.text().contains(token, ignoreCase = true) }
            }
        }

        val unique = linkedMapOf<String, AnimeSearchResult>()
        anchors.forEach { anchor ->
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val title = anchor.text().trim()
            if (!href.startsWith(BASE_URL) || title.length < 3) return@forEach
            if (
                href.contains("/category/") ||
                href.contains("/tag/") ||
                href == "$BASE_URL/"
            ) {
                return@forEach
            }

            val container = anchor.parents().firstOrNull {
                it.tagName() == "article" || it.hasClass("item-list")
            }
            val cover = container?.selectFirst("img")?.let(::imageUrl)

            unique.putIfAbsent(
                href,
                AnimeSearchResult(
                    ref = AnimeRef(metadata.id, href),
                    title = title,
                    coverUrl = cover,
                    sourceState = href,
                ),
            )
        }
        return unique.values.toList()
    }

    private suspend fun listDriveFolder(
        folderId: String,
        prefix: String?,
        seenFolders: MutableSet<String>,
        depth: Int,
    ): List<DriveFile> {
        if (depth > MAX_FOLDER_DEPTH || !seenFolders.add(folderId)) return emptyList()

        val url = "https://drive.google.com/embeddedfolderview?id=" +
            URLEncoder.encode(folderId, "UTF-8")
        val document = loadDocument(
            url = url,
            referer = "https://drive.google.com/",
            userAgent = DRIVE_FOLDER_USER_AGENT,
        )

        val files = mutableListOf<DriveFile>()
        for (anchor in document.select("a[href]")) {
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val label = anchor.text().trim()

            val fileId = extractDriveFileId(href)
            if (fileId != null) {
                val name = listOfNotNull(prefix, label.takeIf { it.isNotBlank() })
                    .joinToString(" • ")
                    .ifBlank { fileId }
                files += DriveFile(fileId, name)
                continue
            }

            val childFolder = extractDriveFolderId(href) ?: continue
            val childPrefix = listOfNotNull(prefix, label.takeIf { it.isNotBlank() })
                .joinToString(" • ")
                .takeIf { it.isNotBlank() }
            files += listDriveFolder(
                folderId = childFolder,
                prefix = childPrefix,
                seenFolders = seenFolders,
                depth = depth + 1,
            )
        }
        return files
    }

    private suspend fun loadDocument(
        url: String,
        referer: String,
        userAgent: String = DEFAULT_USER_AGENT,
    ): Document = try {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Referer", referer)
                .build()
            client.newCall(request).execute().use { response ->
                val html = response.body.string()
                if (!response.isSuccessful) {
                    val needsVerification = response.code == 403 ||
                        response.code == 503 &&
                        Regex("(?i)captcha|verify|challenge|cloudflare").containsMatchIn(html)
                    throw NamiSourceException(
                        kind = if (needsVerification) {
                            NamiSourceErrorKind.VERIFICATION_REQUIRED
                        } else {
                            NamiSourceErrorKind.TEMPORARY
                        },
                        message = if (needsVerification) {
                            "KayoAnime requires browser verification. Open the source page and retry."
                        } else {
                            "KayoAnime is temporarily unavailable (HTTP ${response.code})."
                        },
                    )
                }
                Jsoup.parse(html, url)
            }
        }
    } catch (failure: NamiSourceException) {
        throw failure
    } catch (failure: SocketTimeoutException) {
        throw NamiSourceException(
            kind = NamiSourceErrorKind.TIMEOUT,
            message = "KayoAnime took too long to respond.",
            cause = failure,
        )
    } catch (failure: IOException) {
        throw NamiSourceException(
            kind = NamiSourceErrorKind.NETWORK,
            message = "Could not connect to KayoAnime. Check your connection and retry.",
            cause = failure,
        )
    }

    private fun imageUrl(element: Element): String? =
        sequenceOf("data-src", "data-lazy-src", "src")
            .map { key -> element.absUrl(key).ifBlank { element.attr(key) } }
            .firstOrNull { it.isNotBlank() }

    private fun pageUrl(page: Int): String =
        if (page <= 1) "$BASE_URL/" else "$BASE_URL/page/$page/"

    private fun absolute(value: String): String =
        if (value.startsWith("https://")) value else "$BASE_URL$value"

    private fun isKayoUrl(value: String): Boolean =
        value.startsWith("$BASE_URL/")

    private fun extractDriveFolderId(url: String): String? =
        Regex("""/drive/(?:u/\d+/)?folders/([A-Za-z0-9_-]{10,})""")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)

    private fun extractDriveFileId(url: String): String? =
        Regex("""/file/d/([A-Za-z0-9_-]{10,})""")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)

    private fun encodeEpisode(file: DriveFile): String =
        "gdrive:${file.id}:${URLEncoder.encode(file.name, "UTF-8")}"

    private fun decodeEpisode(value: String): DriveFile? {
        if (!value.startsWith("gdrive:")) return null
        val payload = value.removePrefix("gdrive:")
        val id = payload.substringBefore(':')
        val encodedName = payload.substringAfter(':', "")
        if (id.isBlank() || encodedName.isBlank()) return null
        return runCatching {
            DriveFile(id, URLDecoder.decode(encodedName, "UTF-8"))
        }.getOrNull()
    }

    private fun episodeNumber(name: String): Float? =
        Regex("""(?i)(?:episode|ep)[ ._\-]*(\d{1,3}(?:\.\d+)?)""")
            .find(name)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFloatOrNull()
            ?: Regex("""(?<!\d)(\d{1,3})(?!\d)""")
                .findAll(name)
                .mapNotNull { it.groupValues.getOrNull(1)?.toFloatOrNull() }
                .lastOrNull()

    private data class DriveFile(
        val id: String,
        val name: String,
    ) {
        val extension: String
            get() = name.substringAfterLast('.', "")
    }

    private companion object {
        const val EXTENSION_ID = "app.nami.source.kayoanime"
        const val EXTENSION_VERSION = "1.0.0"
        const val BASE_URL = "https://kayoanime.com"
        const val MAX_FOLDER_DEPTH = 4
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"
        const val DRIVE_FOLDER_USER_AGENT = DEFAULT_USER_AGENT
        val PLAYABLE_EXTENSIONS = setOf("mkv", "mp4", "webm", "m4v")
    }
}
