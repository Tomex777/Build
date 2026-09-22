package com.night.extensions.animepahe

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import java.net.URI
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

data class AnimePaheSearchItem(
    val id: String,
    val session: String,
    val title: String,
    val type: String,
    val year: String,
    val episodes: String,
    val status: String,
    val poster: String?,
)

data class AnimePaheDetails(
    val session: String,
    val title: String,
    val poster: String?,
    val summary: String,
    val type: String,
    val status: String,
    val studios: String,
    val season: String,
    val genres: List<String>,
)

data class AnimePaheEpisode(
    val session: String,
    val number: String,
    val title: String,
    val snapshot: String?,
    val duration: String,
    val createdAt: String,
)

data class AnimePaheEpisodePage(
    val items: List<AnimePaheEpisode>,
    val currentPage: Int,
    val lastPage: Int,
)

data class AnimePaheSource(
    val kwikUrl: String,
    val downloadPageUrl: String?,
    val resolution: Int?,
    val audio: String,
    val fansub: String,
)

data class AnimePaheResolvedMedia(
    val url: String,
    val mimeType: String = "video/mp4",
    val headers: Map<String, String>,
)

class AnimePaheVerificationRequired(
    val verificationUrl: String,
    val host: String,
    message: String,
) : IOException(message)

interface AnimePaheSession {
    fun baseUrl(): String
    fun quality(): String
    fun audio(): String
    fun parallelDownloads(): Int
    fun userAgent(): String
    fun cookieForUrl(url: String): String
    fun saveBrowserSession(host: String, cookieHeader: String)
    fun saveConfiguration(values: JSONObject)
    fun absorbResponseCookies(response: Response)
}

class AnimePaheSessionStore(
    context: Context,
) : AnimePaheSession {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            "night_animepahe",
            Context.MODE_PRIVATE,
        )

    override fun baseUrl(): String =
        prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL)
            .orEmpty()
            .trim()
            .trimEnd('/')
            .ifBlank { DEFAULT_BASE_URL }

    override fun quality(): String =
        prefs.getString(KEY_QUALITY, "auto")
            .orEmpty()
            .trim()
            .lowercase(Locale.US)
            .ifBlank { "auto" }

    override fun audio(): String =
        prefs.getString(KEY_AUDIO, "sub")
            .orEmpty()
            .trim()
            .lowercase(Locale.US)
            .ifBlank { "sub" }

    override fun parallelDownloads(): Int =
        prefs.getString(KEY_PARALLEL_DOWNLOADS, "2")
            .orEmpty()
            .toIntOrNull()
            ?.coerceIn(1, 6)
            ?: 2

    override fun userAgent(): String =
        prefs.getString(KEY_USER_AGENT, DEFAULT_USER_AGENT)
            .orEmpty()
            .trim()
            .ifBlank { DEFAULT_USER_AGENT }

    override fun saveConfiguration(values: JSONObject) {
        val base =
            values.optString("base_url")
                .trim()
                .trimEnd('/')
                .takeIf {
                    it.startsWith("https://") &&
                        runCatching { URI(it).host.orEmpty() }
                            .getOrDefault("")
                            .isNotBlank()
                }
                ?: baseUrl()
        val quality =
            values.optString("quality")
                .trim()
                .lowercase(Locale.US)
                .takeIf { it in setOf("auto", "1080", "720", "360") }
                ?: quality()
        val audio =
            values.optString("audio")
                .trim()
                .lowercase(Locale.US)
                .takeIf { it in setOf("sub", "eng", "kor", "chi") }
                ?: audio()
        val parallel =
            values.optString("parallel_downloads")
                .toIntOrNull()
                ?.coerceIn(1, 6)
                ?: parallelDownloads()
        val ua =
            values.optString("user_agent")
                .trim()
                .takeIf { it.length in 8..512 }
                ?: userAgent()

        prefs.edit()
            .putString(KEY_BASE_URL, base)
            .putString(KEY_QUALITY, quality)
            .putString(KEY_AUDIO, audio)
            .putString(KEY_PARALLEL_DOWNLOADS, parallel.toString())
            .putString(KEY_USER_AGENT, ua)
            .apply()
    }

    override fun saveBrowserSession(
        host: String,
        cookieHeader: String,
    ) {
        val normalized = normalizeHost(host)
        if (normalized.isBlank()) return
        val editor =
            prefs.edit()
                .putString(
                    cookieKey(normalized),
                    cookieHeader.trim().take(16_000),
                )

        if (
            normalized.startsWith("animepahe.") ||
            normalized.contains(".animepahe.")
        ) {
            editor.putString(
                KEY_BASE_URL,
                "https://" + normalized,
            )
        }

        editor.apply()
    }

    fun saveBrowserUserAgent(
        userAgent: String,
    ) {
        val normalized =
            userAgent.trim()
                .takeIf {
                    it.length in 8..512
                }
                ?: return

        prefs.edit()
            .putString(
                KEY_USER_AGENT,
                normalized,
            )
            .apply()
    }

    override fun cookieForUrl(url: String): String {
        val host = hostOf(url)
        if (host.isBlank()) return ""
        return prefs.getString(cookieKey(host), "")
            .orEmpty()
            .trim()
    }

    override fun absorbResponseCookies(response: Response) {
        val host = response.request.url.host.lowercase(Locale.US)
        val cookies =
            response.headers("Set-Cookie")
                .map { it.substringBefore(';').trim() }
                .filter { it.contains('=') && it.isNotBlank() }
        if (cookies.isEmpty()) return

        val merged = linkedMapOf<String, String>()
        cookieForUrl(response.request.url.toString())
            .split(';')
            .map(String::trim)
            .filter { it.contains('=') }
            .forEach { part ->
                merged[part.substringBefore('=').trim()] =
                    part.substringAfter('=', "").trim()
            }
        cookies.forEach { part ->
            merged[part.substringBefore('=').trim()] =
                part.substringAfter('=', "").trim()
        }

        val header =
            merged.entries.joinToString("; ") { (name, value) ->
                "$name=$value"
            }
        prefs.edit()
            .putString(cookieKey(host), header.take(16_000))
            .apply()
    }

    private fun cookieKey(host: String): String =
        "cookie::" + normalizeHost(host)

    private fun hostOf(url: String): String =
        runCatching { URI(url).host.orEmpty() }
            .getOrDefault("")
            .let(::normalizeHost)

    private fun normalizeHost(host: String): String =
        host.trim()
            .trimEnd('.')
            .lowercase(Locale.US)

    companion object {
        const val DEFAULT_BASE_URL = "https://animepahe.pw"
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"

        private const val KEY_BASE_URL = "base_url"
        private const val KEY_QUALITY = "quality"
        private const val KEY_AUDIO = "audio"
        private const val KEY_PARALLEL_DOWNLOADS = "parallel_downloads"
        private const val KEY_USER_AGENT = "user_agent"
    }
}

class AnimePaheClient(
    private val session: AnimePaheSession,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build(),
) {
    fun search(query: String): List<AnimePaheSearchItem> {
        val safeQuery = query.trim().take(180)
        require(safeQuery.isNotBlank()) { "Search query is required." }
        val url =
            session.baseUrl()
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("api")
                .addQueryParameter("m", "search")
                .addQueryParameter("q", safeQuery)
                .build()
                .toString()

        val json = JSONObject(getText(url, session.baseUrl() + "/"))
        val data = json.optJSONArray("data") ?: return emptyList()

        return buildList {
            for (index in 0 until minOf(data.length(), 24)) {
                val item = data.optJSONObject(index) ?: continue
                val animeSession = item.optString("session").trim()
                val title = item.optString("title").trim()
                if (animeSession.isBlank() || title.isBlank()) continue
                add(
                    AnimePaheSearchItem(
                        id = item.optString("id").trim(),
                        session = animeSession,
                        title = title,
                        type = item.optString("type").trim(),
                        year = item.optString("year").trim(),
                        episodes = item.optString("episodes").trim(),
                        status = item.optString("status").trim(),
                        poster =
                            item.optString("poster")
                                .trim()
                                .takeIf { it.startsWith("http") },
                    )
                )
            }
        }
    }

    fun details(
        animeSession: String,
        titleHint: String,
    ): AnimePaheDetails {
        val safeSession = animeSession.trim()
        require(safeSession.isNotBlank()) {
            "Anime session is required."
        }

        val url =
            session.baseUrl() +
                "/anime/" +
                safeSession
        val html =
            getText(
                url = url,
                referer = session.baseUrl() + "/",
            )
        val document =
            org.jsoup.Jsoup.parse(
                html,
                session.baseUrl() + "/",
            )

        fun infoValue(label: String): String {
            val row =
                document.selectFirst(
                    "div.col-sm-4.anime-info p:contains(" +
                        label +
                        ":)"
                ) ?: return ""
            val linkText =
                row.select("a")
                    .joinToString(", ") {
                        it.text().trim()
                    }
                    .trim()
            if (linkText.isNotBlank()) {
                return linkText
            }
            return row.text()
                .substringAfter(label + ":", "")
                .trim()
        }

        val parsedTitle =
            document.selectFirst(
                "div.title-wrapper > h1 > span"
            )?.text()
                ?.trim()
                .orEmpty()
                .ifBlank {
                    titleHint.trim()
                        .ifBlank { "Anime" }
                }

        val poster =
            document.selectFirst(
                "div.anime-poster a"
            )?.absUrl("href")
                ?.trim()
                ?.takeIf { it.startsWith("http") }

        val summary =
            document.selectFirst(
                "div.anime-summary"
            )?.text()
                ?.trim()
                .orEmpty()

        val genres =
            document.select(
                "div.anime-genre ul li"
            )
                .map {
                    it.text().trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        return AnimePaheDetails(
            session = safeSession,
            title = parsedTitle,
            poster = poster,
            summary = summary,
            type = infoValue("Type"),
            status = infoValue("Status"),
            studios = infoValue("Studios"),
            season = infoValue("Season"),
            genres = genres,
        )
    }

    fun episodes(
        animeSession: String,
        page: Int,
    ): AnimePaheEpisodePage {
        val safeSession = animeSession.trim()
        require(safeSession.isNotBlank()) { "Anime session is required." }
        val safePage = page.coerceAtLeast(1)
        val url =
            session.baseUrl()
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("api")
                .addQueryParameter("m", "release")
                .addQueryParameter("id", safeSession)
                .addQueryParameter("sort", "episode_asc")
                .addQueryParameter("page", safePage.toString())
                .build()
                .toString()

        val json = JSONObject(getText(url, session.baseUrl() + "/"))
        val data = json.optJSONArray("data")
        val items =
            buildList {
                if (data != null) {
                    for (index in 0 until data.length()) {
                        val item = data.optJSONObject(index) ?: continue
                        val episodeSession = item.optString("session").trim()
                        if (episodeSession.isBlank()) continue
                        val number =
                            item.optString("episode")
                                .trim()
                                .ifBlank { (index + 1).toString() }
                        add(
                            AnimePaheEpisode(
                                session = episodeSession,
                                number = number,
                                title =
                                    item.optString("title")
                                        .trim()
                                        .ifBlank { "Episode $number" },
                                snapshot =
                                    item.optString("snapshot")
                                        .trim()
                                        .takeIf { it.startsWith("http") },
                                duration = item.optString("duration").trim(),
                                createdAt =
                                    item.optString("created_at").trim(),
                            )
                        )
                    }
                }
            }

        return AnimePaheEpisodePage(
            items = items,
            currentPage =
                json.optInt("current_page", safePage)
                    .coerceAtLeast(1),
            lastPage =
                json.optInt("last_page", safePage)
                    .coerceAtLeast(safePage),
        )
    }

    fun sources(
        animeSession: String,
        episodeSession: String,
    ): List<AnimePaheSource> {
        val anime = animeSession.trim()
        val episode = episodeSession.trim()
        require(anime.isNotBlank() && episode.isNotBlank()) {
            "Anime and episode sessions are required."
        }
        val playUrl =
            session.baseUrl() + "/play/" + anime + "/" + episode
        val html = getText(playUrl, session.baseUrl() + "/")

        val buttons =
            BUTTON_REGEX.findAll(html)
                .mapNotNull { match ->
                    val tag = match.value
                    val attrs =
                        ATTR_REGEX.findAll(tag)
                            .associate {
                                it.groupValues[1].lowercase(Locale.US) to
                                    htmlDecode(it.groupValues[2])
                            }
                    val src = attrs["data-src"].orEmpty().trim()
                    if (!src.startsWith("http")) return@mapNotNull null
                    AnimePaheSource(
                        kwikUrl = src,
                        downloadPageUrl = null,
                        resolution =
                            attrs["data-resolution"]
                                ?.filter(Char::isDigit)
                                ?.toIntOrNull(),
                        audio =
                            attrs["data-audio"]
                                ?.trim()
                                .orEmpty()
                                .ifBlank { "sub" },
                        fansub = attrs["data-fansub"].orEmpty().trim(),
                    )
                }
                .toList()

        val downloadLinks =
            DOWNLOAD_HREF_REGEX.findAll(html)
                .map { htmlDecode(it.groupValues[1]) }
                .filter { it.startsWith("http") }
                .toList()

        return buttons.mapIndexed { index, source ->
            source.copy(
                downloadPageUrl =
                    downloadLinks.getOrNull(index)
                        ?.takeIf { it.isNotBlank() },
            )
        }
    }

    fun selectPreferredSource(
        sources: List<AnimePaheSource>,
    ): AnimePaheSource {
        require(sources.isNotEmpty()) { "No playable sources were found." }

        val preferredAudio = session.audio()
        val languageMatches =
            sources.filter { source ->
                when (preferredAudio) {
                    "sub" ->
                        source.audio.equals("jpn", true) ||
                            source.audio.equals("sub", true) ||
                            source.audio.isBlank()
                    else -> source.audio.contains(preferredAudio, true)
                }
            }.ifEmpty { sources }

        val desired =
            session.quality()
                .takeIf { it != "auto" }
                ?.toIntOrNull()

        val sorted =
            languageMatches
                .sortedByDescending {
                    it.resolution ?: 0
                }

        return if (desired == null) {
            sorted.first()
        } else {
            sorted.firstOrNull {
                (it.resolution ?: 0) <= desired
            } ?: sorted.last()
        }
    }

    private fun getText(
        url: String,
        referer: String,
    ): String {
        execute(
            requestFor(
                url = url,
                referer = referer,
            )
        ).use { response ->
            val body = response.body?.string().orEmpty()
            if (requiresVerification(response.code, body)) {
                throw verification(
                    url,
                    "AnimePahe needs browser verification.",
                )
            }
            if (!response.isSuccessful) {
                throw IOException(
                    "AnimePahe returned HTTP ${response.code}."
                )
            }
            return body
        }
    }

    private fun requestFor(
        url: String,
        referer: String,
    ): Request {
        val builder =
            Request.Builder()
                .url(url)
                .header("User-Agent", session.userAgent())
                .header("Referer", referer)
                .header("Accept", "*/*")
        session.cookieForUrl(url)
            .takeIf { it.isNotBlank() }
            ?.let { builder.header("Cookie", it) }
        return builder.build()
    }

    private fun execute(
        request: Request,
        client: OkHttpClient = this.client,
    ): Response {
        val response = client.newCall(request).execute()
        session.absorbResponseCookies(response)
        return response
    }

    private fun verification(
        url: String,
        message: String,
    ): AnimePaheVerificationRequired {
        val host =
            runCatching { URI(url).host.orEmpty() }
                .getOrDefault("")
        return AnimePaheVerificationRequired(
            verificationUrl = url,
            host = host,
            message = message,
        )
    }

    private fun requiresVerification(
        code: Int,
        body: String?,
    ): Boolean {
        if (code == 403 || code == 419) return true
        if (code != 200 && code != 503) return false
        return looksLikeChallenge(body.orEmpty())
    }

    private fun looksLikeChallenge(body: String): Boolean {
        if (body.length > 500_000) return false

        val document = org.jsoup.Jsoup.parse(body)
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

    companion object {
        private val BUTTON_REGEX =
            Regex("""(?is)<button\b[^>]*data-src\s*=\s*"[^"]+"[^>]*>""")
        private val ATTR_REGEX =
            Regex("""(?i)(data-[a-z0-9_-]+)\s*=\s*"([^"]*)"""")
        private val DOWNLOAD_HREF_REGEX =
            Regex(
                """(?i)href\s*=\s*"(https?://(?:pahe\.win|kwik\.[^/"]+)[^"]*)""""
            )

        private fun htmlDecode(value: String): String =
            value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
    }
}
