package app.nami.source.kayoanime

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.net.URLEncoder

class KayoAnimeSource : AnimeHttpSource() {
    override val name: String = "KayoAnime"
    override val lang: String = "en"
    override val baseUrl: String = "https://kayoanime.com"
    override val supportsLatest: Boolean = true
    override val versionId: Int = 1

    override suspend fun getPopularAnime(page: Int): AnimesPage =
        fetchListing(pageUrl(page), query = null)

    override suspend fun getLatestUpdates(page: Int): AnimesPage =
        fetchListing(pageUrl(page), query = null)

    override suspend fun getSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): AnimesPage {
        if (query.isBlank()) return AnimesPage(emptyList(), false)
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = if (page <= 1) {
            "$baseUrl/?s=$encoded"
        } else {
            "$baseUrl/page/$page/?s=$encoded"
        }
        return fetchListing(url, query)
    }

    override fun animeDetailsRequest(anime: SAnime): Request =
        GET(absolute(anime.url), headers)

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.document()
        return SAnime.create().apply {
            url = response.request.url.toString()
            title = document.selectFirst("h1")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: document.title().substringBefore(" - Kayoanime").trim()
            thumbnail_url = document.selectFirst("meta[property=og:image]")
                ?.attr("content")
                ?.takeIf { it.isNotBlank() }
            description = document.select("article p, .entry-content p")
                .map { it.text().trim() }
                .filter { it.length >= 20 }
                .take(4)
                .joinToString("\n\n")
                .takeIf { it.isNotBlank() }
            initialized = true
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val document = client.newCall(GET(absolute(anime.url), headers))
            .awaitSuccess()
            .use { it.document() }

        val driveAnchors = document.select("a[href*='drive.google.com']")
            .mapNotNull { anchor ->
                val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                href.takeIf { it.contains("drive.google.com") }
                    ?.let { it to anchor.text().trim() }
            }
            .distinctBy { it.first }

        val discovered = mutableListOf<DriveFile>()
        val seenFolders = linkedSetOf<String>()
        for ((href, label) in driveAnchors) {
            val fileId = extractDriveFileId(href)
            if (fileId != null) {
                discovered += DriveFile(fileId, label.ifBlank { "KayoAnime file" })
                continue
            }

            val folderId = extractDriveFolderId(href) ?: continue
            runCatching {
                listDriveFolder(
                    folderId = folderId,
                    prefix = label.takeIf { it.isNotBlank() },
                    seenFolders = seenFolders,
                    depth = 0,
                )
            }.onSuccess { discovered += it }
        }

        val playable = discovered
            .distinctBy { it.id }
            .filter { file -> file.extension.lowercase() in PLAYABLE_EXTENSIONS }
            .sortedWith(
                compareBy<DriveFile> { episodeNumber(it.name) ?: Float.MAX_VALUE }
                    .thenBy { it.name.lowercase() },
            )

        return playable.mapIndexed { index, file ->
            SEpisode.create().apply {
                url = encodeEpisode(file)
                name = file.name
                episode_number = episodeNumber(file.name) ?: (index + 1).toFloat()
            }
        }
    }

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val file = decodeEpisode(episode.url)
            ?: error("KayoAnime episode lost its Google Drive file identity")
        val ext = file.extension.lowercase()
        val height = Regex("""(?i)(2160|1440|1080|720|480)p""")
            .find(file.name)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        return listOf(
            Hoster(
                hosterName = "Google Drive",
                videoList = listOf(
                    Video(
                        videoUrl = "https://drive.google.com/uc?export=download&id=" + file.id,
                        videoTitle = buildString {
                            append("Google Drive")
                            height?.let { append(" - ").append(it).append("p") }
                            if (ext.isNotBlank()) append(" - ").append(ext.uppercase())
                        },
                        resolution = height,
                        headers = headers,
                        preferred = true,
                        initialized = true,
                    ),
                ),
            ),
        )
    }

    private suspend fun fetchListing(
        url: String,
        query: String?,
    ): AnimesPage = client.newCall(GET(url, headers)).awaitSuccess().use { response ->
        val document = response.document()
        val results = parseListing(document, query)
        val hasNext = document.selectFirst("a[rel=next], a.next, .pagination a.next") != null
        AnimesPage(results, hasNext)
    }

    private fun parseListing(
        document: Document,
        query: String?,
    ): List<SAnime> {
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

        val unique = linkedMapOf<String, SAnime>()
        anchors.forEach { anchor ->
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val title = anchor.text().trim()
            if (!href.startsWith(baseUrl) || title.length < 3) return@forEach
            if (
                href.contains("/category/") ||
                href.contains("/tag/") ||
                href == "$baseUrl/"
            ) {
                return@forEach
            }

            val container = anchor.parents().firstOrNull {
                it.tagName() == "article" || it.hasClass("item-list")
            }
            val cover = container?.selectFirst("img")?.let(::imageUrl)

            unique.putIfAbsent(
                href,
                SAnime.create().apply {
                    url = href
                    this.title = title
                    thumbnail_url = cover
                },
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

        val driveHeaders = headers.newBuilder()
            .set("User-Agent", DRIVE_FOLDER_USER_AGENT)
            .build()
        val url = "https://drive.google.com/embeddedfolderview?id=" +
            URLEncoder.encode(folderId, "UTF-8")
        val document = client.newCall(GET(url, driveHeaders))
            .awaitSuccess()
            .use { it.document("https://drive.google.com") }

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

    private fun Response.document(base: String = baseUrl): Document =
        Jsoup.parse(body.string(), base)

    private fun imageUrl(element: Element): String? =
        sequenceOf("data-src", "data-lazy-src", "src")
            .map { key -> element.absUrl(key).ifBlank { element.attr(key) } }
            .firstOrNull { it.isNotBlank() }

    private fun pageUrl(page: Int): String =
        if (page <= 1) "$baseUrl/" else "$baseUrl/page/$page/"

    private fun absolute(value: String): String =
        if (value.startsWith("http://") || value.startsWith("https://")) value else baseUrl + value

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
        "gdrive:" + file.id + ":" + URLEncoder.encode(file.name, "UTF-8")

    private fun decodeEpisode(value: String): DriveFile? {
        if (!value.startsWith("gdrive:")) return null
        val payload = value.removePrefix("gdrive:")
        val id = payload.substringBefore(':')
        val encodedName = payload.substringAfter(':', "")
        if (id.isBlank() || encodedName.isBlank()) return null
        return DriveFile(
            id = id,
            name = URLDecoder.decode(encodedName, "UTF-8"),
        )
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
        val PLAYABLE_EXTENSIONS = setOf("mkv", "mp4", "webm", "m4v")
        const val MAX_FOLDER_DEPTH = 4
        const val DRIVE_FOLDER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"
    }
}
