package com.night.sora.youtubemusic

import android.content.Context
import android.net.Uri
import android.util.Log
import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_MUSIC
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_NO_SDK
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_65_10
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.metrolist.innertube.models.YouTubeClient.Companion.IOS
import com.metrolist.innertube.models.YouTubeClient.Companion.IOS_RECENT
import com.metrolist.innertube.models.YouTubeClient.Companion.IPADOS
import com.metrolist.innertube.models.YouTubeClient.Companion.MOBILE
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5
import com.metrolist.innertube.models.YouTubeClient.Companion.VISIONOS
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_REMIX
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object YouTubeMusicCatalog {
    private const val SOURCE_ID = "youtube.music"
    private const val TAG = "SoraYouTubeMusic"
    private const val PLAYER_ATTEMPT_TIMEOUT_MS = 15_000L
    private const val SESSION_PREFS = "sora_youtube_music_session_v1"
    private const val SESSION_COOKIE = "cookie"
    private const val SESSION_USER_AGENT = "userAgent"
    private const val LYRICS_NETWORK_TIMEOUT_MS = 12_000
    private const val LRCLIB_USER_AGENT = "Sora/0.1.1 (https://github.com/Tomex777/Build)"

    @Volatile
    private var poTokenProvider: YouTubePoTokenProvider? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var sessionUserAgent: String? = null

    /**
     * Keep the cheap direct-url path first, but walk the extra anonymous identities
     * already carried by the pinned Spotui Innertube snapshot before escalating to
     * BotGuard/PoToken. YouTube-specific anti-bot logic remains extension-local.
     */
    private val nativePlaybackClients = listOf(
        ANDROID_MUSIC,
        ANDROID_NO_SDK,
        ANDROID_VR_NO_AUTH,
        ANDROID_VR_1_65_10,
        ANDROID_VR_1_43_32,
        TVHTML5,
        IOS_RECENT,
        IOS,
        IPADOS,
        VISIONOS,
        MOBILE,
    )

    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        val prefs = app.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        val storedCookie = prefs.getString(SESSION_COOKIE, null)?.takeIf(String::isNotBlank)
        sessionUserAgent = prefs.getString(SESSION_USER_AGENT, null)?.takeIf(String::isNotBlank)
        YouTube.cookie = storedCookie
        YouTube.useLoginForBrowse = hasSignedInCookie(storedCookie)

        if (poTokenProvider == null) {
            synchronized(this) {
                if (poTokenProvider == null) {
                    poTokenProvider = YouTubePoTokenProvider(app)
                }
            }
        }
        Log.i(TAG, "session restore signedIn=${hasSignedInCookie(storedCookie)}")
    }

    fun browserSession(sourceId: String): String {
        requireSource(sourceId)
        val headers = JSONObject()
            .put("User-Agent", sessionUserAgent?.takeIf(String::isNotBlank) ?: WEB_REMIX.userAgent)
        return JSONObject()
            .put("url", "https://music.youtube.com/")
            .put("title", "YouTube Music sign in")
            .put("headers", headers)
            .toString()
    }

    suspend fun storeSession(sourceId: String, cookieHeader: String, userAgent: String): String {
        requireSource(sourceId)
        val context = appContext ?: error("YouTube Music extension is not initialized")
        val prefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        val cleanCookie = cookieHeader.trim()
        val cleanUserAgent = userAgent.trim().takeIf(String::isNotBlank)

        if (!hasSignedInCookie(cleanCookie)) {
            prefs.edit().clear().apply()
            YouTube.cookie = null
            YouTube.useLoginForBrowse = false
            sessionUserAgent = null
            Log.i(TAG, "session cleared signedIn=false")
            return JSONObject()
                .put("stored", false)
                .put("signedIn", false)
                .put("message", "No signed-in YouTube Music session was found.")
                .toString()
        }

        prefs.edit()
            .putString(SESSION_COOKIE, cleanCookie)
            .putString(SESSION_USER_AGENT, cleanUserAgent)
            .apply()
        sessionUserAgent = cleanUserAgent
        YouTube.cookie = cleanCookie
        YouTube.useLoginForBrowse = true

        val validated = withTimeoutOrNull(PLAYER_ATTEMPT_TIMEOUT_MS) {
            YouTube.validateLogin().getOrDefault(false)
        } ?: false
        Log.i(TAG, "session stored signedIn=true validated=$validated")
        return JSONObject()
            .put("stored", true)
            .put("signedIn", true)
            .put("validated", validated)
            .toString()
    }

    suspend fun browse(sourceId: String): String {
        requireSource(sourceId)
        return search(sourceId, "top songs")
    }

    suspend fun search(sourceId: String, query: String): String {
        requireSource(sourceId)
        ensureVisitorData()
        val clean = query.trim().ifBlank { "top songs" }
        val result = YouTube.search(clean, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
        val songs = result.items.filterIsInstance<SongItem>().take(30)
        return JSONArray().apply {
            songs.forEach { song ->
                val artists = song.artists.joinToString(", ") { it.name }
                val album = song.album?.name.orEmpty()
                val subtitle = listOf(artists, album).filter { it.isNotBlank() }.joinToString(" · ")
                put(
                    JSONObject()
                        .put("id", song.id)
                        .put("title", song.title)
                        .put("subtitle", subtitle)
                        .put("artworkUrl", song.thumbnail)
                        .put("durationSeconds", song.duration ?: 0)
                        .put("explicit", song.explicit)
                )
            }
        }.toString()
    }

    suspend fun details(sourceId: String, id: String): String {
        requireSource(sourceId)
        ensureVisitorData()
        val response = withTimeoutOrNull(PLAYER_ATTEMPT_TIMEOUT_MS) {
            YouTube.player(
                videoId = id,
                client = ANDROID_MUSIC,
            ).getOrThrow()
        } ?: error("YouTube Music metadata request timed out")
        val details = response.videoDetails
        return JSONObject()
            .put("description", listOfNotNull(details?.author, details?.title).joinToString(" · "))
            .put("status", response.playabilityStatus.status)
            .put("durationSeconds", details?.lengthSeconds?.toLongOrNull() ?: 0L)
            .toString()
    }


    suspend fun lyrics(
        sourceId: String,
        id: String,
        title: String,
        artist: String,
        album: String,
    ): String {
        requireSource(sourceId)
        ensureVisitorData()

        val youtubeText = runCatching { youtubeMusicLyrics(id) }
            .onFailure { Log.w(TAG, "YouTube Music lyrics lookup failed id=$id: ${it.message}") }
            .getOrNull()
            ?.takeIf(String::isNotBlank)

        if (youtubeText != null) {
            return lyricsJson(
                trackId = id,
                provider = "youtube_music",
                text = youtubeText,
                syncedText = null,
            )
        }

        val fallback = runCatching {
            lrclibLyrics(
                title = title.trim(),
                artist = artist.trim(),
                album = album.trim(),
            )
        }.onFailure {
            Log.w(TAG, "LRCLIB fallback failed id=$id: ${it.message}")
        }.getOrNull()

        return if (fallback != null) {
            lyricsJson(
                trackId = id,
                provider = "lrclib",
                text = fallback.plainText,
                syncedText = fallback.syncedText,
            )
        } else {
            lyricsJson(trackId = id, provider = "none", text = "", syncedText = null)
        }
    }

    suspend fun streams(sourceId: String, id: String): String {
        requireSource(sourceId)
        ensureVisitorData()

        val signatureTimestamp = runCatching {
            NewPipeExtractor.getSignatureTimestamp(id).getOrNull()
        }.getOrNull()
        Log.i(
            TAG,
            "resolver context id=$id visitor=${!YouTube.visitorData.isNullOrBlank()} signedIn=${hasSignedInCookie(YouTube.cookie)} signatureTimestamp=${signatureTimestamp ?: "none"}",
        )

        val failures = mutableListOf<String>()
        for (client in nativePlaybackClients) {
            val identity = "${client.clientName}/${client.clientVersion}"
            Log.i(TAG, "player start id=$id client=$identity")
            val response = withTimeoutOrNull(PLAYER_ATTEMPT_TIMEOUT_MS) {
                YouTube.player(
                    videoId = id,
                    client = client,
                    signatureTimestamp = signatureTimestamp.takeIf { client.useSignatureTimestamp },
                    authenticated = false,
                ).getOrNull()
            }

            if (response == null) {
                Log.w(TAG, "player timeout-or-null id=$id client=$identity")
                failures += "$identity: timeout/network"
                continue
            }

            val status = response.playabilityStatus.status
            val reason = response.playabilityStatus.reason.orEmpty()
            val returnedVideoId = response.videoDetails?.videoId
            val formats = response.streamingData?.adaptiveFormats.orEmpty()
            Log.i(
                TAG,
                "player result id=$id client=$identity status=$status reason=$reason returned=$returnedVideoId adaptive=${formats.size}",
            )

            if (returnedVideoId != null && returnedVideoId != id) {
                Log.w(TAG, "wrong-video id=$id client=$identity returned=$returnedVideoId")
                failures += "$identity: wrong video"
                continue
            }
            if (status != "OK") {
                failures += "$identity: $status${reason.takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty()}"
                continue
            }

            val audio = directAudioFormats(formats)
            Log.i(
                TAG,
                "direct audio id=$id client=$identity count=${audio.size} mime=${audio.firstOrNull()?.mimeType.orEmpty()}",
            )
            if (audio.isEmpty()) {
                failures += "$identity: no direct audio"
                continue
            }

            return streamsJson(audio, client, poToken = null).also {
                Log.i(TAG, "resolved id=$id client=$identity streams=${audio.size}")
            }
        }

        val visitorData = YouTube.visitorData?.takeIf(String::isNotBlank)
        val provider = poTokenProvider
        var tokenPair: YouTubePoTokenProvider.Tokens? = null
        if (visitorData == null) {
            failures += "WEB_REMIX: missing visitor data"
        } else if (provider == null) {
            failures += "WEB_REMIX: PoToken provider not initialized"
        } else {
            Log.i(TAG, "potoken start id=$id client=${WEB_REMIX.clientName}/${WEB_REMIX.clientVersion}")
            val tokens = provider.tokens(id, visitorData)
            tokenPair = tokens.getOrNull()
            if (tokenPair == null) {
                val message = tokens.exceptionOrNull()?.message.orEmpty().ifBlank { "unknown PoToken error" }
                Log.e(TAG, "potoken failed id=$id reason=$message", tokens.exceptionOrNull())
                failures += "WEB_REMIX PoToken: $message"
            } else {
                Log.i(TAG, "potoken ready id=$id request=true stream=true")
            }
        }

        // Spotui's signed-in fallback uses the real browser session only after the
        // anonymous identities have been challenged. Cookie + SAPISIDHASH are added
        // by Innertube when authenticated=true; the PoTokens remain bound to the
        // current VISITOR_DATA/video pair.
        if (hasSignedInCookie(YouTube.cookie) && tokenPair != null) {
            val client = authenticatedWebClient()
            Log.i(TAG, "authenticated player start id=$id client=${client.clientName}/${client.clientVersion}")
            val response = withTimeoutOrNull(PLAYER_ATTEMPT_TIMEOUT_MS) {
                YouTube.player(
                    videoId = id,
                    client = client,
                    signatureTimestamp = signatureTimestamp,
                    poToken = tokenPair.playerRequestPoToken,
                    authenticated = true,
                ).getOrNull()
            }
            if (response == null) {
                failures += "WEB_REMIX authenticated: timeout/network"
                Log.w(TAG, "authenticated player timeout-or-null id=$id")
            } else {
                val status = response.playabilityStatus.status
                val reason = response.playabilityStatus.reason.orEmpty()
                val returnedVideoId = response.videoDetails?.videoId
                val formats = response.streamingData?.adaptiveFormats.orEmpty()
                Log.i(
                    TAG,
                    "authenticated player result id=$id status=$status reason=$reason returned=$returnedVideoId adaptive=${formats.size}",
                )
                when {
                    returnedVideoId != null && returnedVideoId != id -> {
                        failures += "WEB_REMIX authenticated: wrong video"
                    }
                    status != "OK" -> {
                        failures += "WEB_REMIX authenticated: $status${reason.takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty()}"
                    }
                    else -> {
                        val audio = resolvedWebAudioFormats(formats, id, client)
                        if (audio.isNotEmpty()) {
                            return resolvedStreamsJson(audio, client, tokenPair.streamingDataPoToken).also {
                                Log.i(TAG, "resolved id=$id client=WEB_REMIX authenticated streams=${audio.size} pot=true")
                            }
                        }
                        failures += "WEB_REMIX authenticated: OK but no resolvable audio"
                    }
                }
            }
        }

        // Keep the anonymous browser path for devices/IPs where BotGuard alone is
        // accepted, even when no account session exists.
        if (tokenPair != null) {
            val response = withTimeoutOrNull(PLAYER_ATTEMPT_TIMEOUT_MS) {
                YouTube.player(
                    videoId = id,
                    client = WEB_REMIX,
                    signatureTimestamp = signatureTimestamp,
                    poToken = tokenPair.playerRequestPoToken,
                    authenticated = false,
                ).getOrNull()
            }

            if (response == null) {
                failures += "WEB_REMIX: timeout/network"
                Log.w(TAG, "player timeout-or-null id=$id client=WEB_REMIX")
            } else {
                val status = response.playabilityStatus.status
                val reason = response.playabilityStatus.reason.orEmpty()
                val returnedVideoId = response.videoDetails?.videoId
                val formats = response.streamingData?.adaptiveFormats.orEmpty()
                Log.i(
                    TAG,
                    "player result id=$id client=WEB_REMIX status=$status reason=$reason returned=$returnedVideoId adaptive=${formats.size}",
                )
                when {
                    returnedVideoId != null && returnedVideoId != id -> {
                        failures += "WEB_REMIX: wrong video"
                        Log.w(TAG, "wrong-video id=$id client=WEB_REMIX returned=$returnedVideoId")
                    }
                    status != "OK" -> {
                        failures += "WEB_REMIX: $status${reason.takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty()}"
                    }
                    else -> {
                        val audio = resolvedWebAudioFormats(formats, id, WEB_REMIX)
                        if (audio.isNotEmpty()) {
                            return resolvedStreamsJson(audio, WEB_REMIX, tokenPair.streamingDataPoToken).also {
                                Log.i(TAG, "resolved id=$id client=WEB_REMIX streams=${audio.size} pot=true")
                            }
                        }
                        failures += "WEB_REMIX: OK but no resolvable audio"
                        Log.w(TAG, "WEB_REMIX playable response has no resolvable audio URL")
                    }
                }
            }
        }

        // Spotui also carries NewPipe's StreamInfo extractor. Its extraction path is
        // independent of the Innertube response above, so let it make one final
        // anonymous attempt when YouTube has challenged every API client. Keeping it
        // last avoids replacing URLs that were already minted for a known client.
        Log.i(TAG, "newpipe start id=$id")
        val extracted = runCatching { NewPipeExtractor.newPipePlayer(id) }
        val newPipeStreams = extracted.getOrNull().orEmpty()
        if (newPipeStreams.isNotEmpty()) {
            val audioStreams = newPipeStreams.mapNotNull { (itag, url) ->
                val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return@mapNotNull null
                val mime = uri.getQueryParameter("mime").orEmpty()
                val isKnownAudioItag = itag in setOf(139, 140, 141, 249, 250, 251, 256, 258, 325, 328)
                if (!mime.startsWith("audio/", ignoreCase = true) && !isKnownAudioItag) {
                    return@mapNotNull null
                }
                NewPipeAudioStream(
                    itag = itag,
                    url = url,
                    mimeType = mime.ifBlank { mimeTypeForAudioItag(itag) },
                    bitrate = bitrateForAudioItag(itag),
                    durationMs = ((uri.getQueryParameter("dur")?.toDoubleOrNull() ?: 0.0) * 1000.0).toLong(),
                )
            }.sortedByDescending { it.bitrate }

            if (audioStreams.isNotEmpty()) {
                Log.i(TAG, "newpipe resolved id=$id streams=${audioStreams.size} itags=${audioStreams.joinToString { it.itag.toString() }}")
                return newPipeStreamsJson(audioStreams)
            }
            failures += "NewPipe: streams found but none identified as audio"
            Log.w(TAG, "newpipe returned streams but no audio id=$id total=${newPipeStreams.size}")
        } else {
            val reason = extracted.exceptionOrNull()?.message.orEmpty().ifBlank { "no streams" }
            failures += "NewPipe: $reason"
            Log.w(TAG, "newpipe failed id=$id reason=$reason", extracted.exceptionOrNull())
        }

        error("No YouTube Music playback path resolved direct audio: ${failures.joinToString("; ")}")
    }


    private fun youtubeMusicLyrics(videoId: String): String? {
        val nextBody = youtubeRequestBody().put("videoId", videoId)
        val next = youtubeMusicPost("next", nextBody)
        val browseId = findLyricsBrowseId(next) ?: return null
        val browse = youtubeMusicPost("browse", youtubeRequestBody().put("browseId", browseId))
        return findLyricsDescription(browse)?.trim()?.takeIf(String::isNotBlank)
    }

    private fun youtubeRequestBody(): JSONObject {
        val locale = Locale.getDefault()
        val client = JSONObject()
            .put("clientName", WEB_REMIX.clientName)
            .put("clientVersion", WEB_REMIX.clientVersion)
            .put("hl", locale.language.takeIf(String::isNotBlank) ?: "en")
            .put("gl", locale.country.takeIf(String::isNotBlank) ?: "US")
        YouTube.visitorData?.takeIf(String::isNotBlank)?.let { client.put("visitorData", it) }
        return JSONObject().put("context", JSONObject().put("client", client))
    }

    private fun youtubeMusicPost(endpoint: String, payload: JSONObject): JSONObject {
        val connection = URL("https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
            .openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = LYRICS_NETWORK_TIMEOUT_MS
            connection.readTimeout = LYRICS_NETWORK_TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty(
                "User-Agent",
                sessionUserAgent?.takeIf(String::isNotBlank) ?: WEB_REMIX.userAgent,
            )
            connection.setRequestProperty("X-YouTube-Client-Name", WEB_REMIX.clientId)
            connection.setRequestProperty("X-YouTube-Client-Version", WEB_REMIX.clientVersion)
            connection.setRequestProperty("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
            connection.setRequestProperty("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
            YouTube.visitorData?.takeIf(String::isNotBlank)?.let {
                connection.setRequestProperty("X-Goog-Visitor-Id", it)
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) error("YouTube Music $endpoint HTTP $code")
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun findLyricsBrowseId(value: Any?): String? = when (value) {
        is JSONObject -> {
            val endpoint = value.optJSONObject("browseEndpoint")
            val direct = endpoint
                ?.takeIf { jsonContainsString(it, "MUSIC_PAGE_TYPE_TRACK_LYRICS") }
                ?.optString("browseId")
                ?.takeIf(String::isNotBlank)
            if (direct != null) {
                direct
            } else {
                val keys = value.keys()
                var found: String? = null
                while (keys.hasNext() && found == null) {
                    found = findLyricsBrowseId(value.opt(keys.next()))
                }
                found
            }
        }
        is JSONArray -> {
            var found: String? = null
            for (index in 0 until value.length()) {
                found = findLyricsBrowseId(value.opt(index))
                if (found != null) break
            }
            found
        }
        else -> null
    }

    private fun jsonContainsString(value: Any?, expected: String): Boolean = when (value) {
        is JSONObject -> {
            val keys = value.keys()
            var found = false
            while (keys.hasNext() && !found) {
                found = jsonContainsString(value.opt(keys.next()), expected)
            }
            found
        }
        is JSONArray -> (0 until value.length()).any { jsonContainsString(value.opt(it), expected) }
        is String -> value == expected
        else -> false
    }

    private fun findLyricsDescription(value: Any?): String? = when (value) {
        is JSONObject -> {
            val shelf = value.optJSONObject("musicDescriptionShelfRenderer")
            val direct = shelf?.optJSONObject("description")?.let(::runsText)?.takeIf(String::isNotBlank)
            if (direct != null) {
                direct
            } else {
                val keys = value.keys()
                var found: String? = null
                while (keys.hasNext() && found == null) {
                    found = findLyricsDescription(value.opt(keys.next()))
                }
                found
            }
        }
        is JSONArray -> {
            var found: String? = null
            for (index in 0 until value.length()) {
                found = findLyricsDescription(value.opt(index))
                if (found != null) break
            }
            found
        }
        else -> null
    }

    private fun runsText(description: JSONObject): String {
        val runs = description.optJSONArray("runs") ?: return ""
        return buildString {
            for (index in 0 until runs.length()) {
                val text = runs.optJSONObject(index)?.optString("text").orEmpty()
                if (text.isNotEmpty()) append(text)
            }
        }
    }

    private data class LrclibLyrics(val plainText: String, val syncedText: String?)

    private fun lrclibLyrics(title: String, artist: String, album: String): LrclibLyrics? {
        if (title.isBlank() || artist.isBlank()) return null
        val common = buildString {
            append("track_name=").append(urlEncode(title))
            append("&artist_name=").append(urlEncode(artist))
            if (album.isNotBlank()) append("&album_name=").append(urlEncode(album))
        }

        val exact = lrclibGet("https://lrclib.net/api/get?$common")
        if (exact.first == 200 && exact.second.isNotBlank()) {
            parseLrclibObject(JSONObject(exact.second))?.let { return it }
        }

        if (exact.first != 404 && exact.first !in 200..299) {
            error("LRCLIB /get HTTP ${exact.first}")
        }

        val search = lrclibGet("https://lrclib.net/api/search?$common")
        if (search.first !in 200..299) {
            if (search.first == 404) return null
            error("LRCLIB /search HTTP ${search.first}")
        }
        val results = JSONArray(search.second)
        for (index in 0 until results.length()) {
            val parsed = results.optJSONObject(index)?.let(::parseLrclibObject)
            if (parsed != null) return parsed
        }
        return null
    }

    private fun lrclibGet(url: String): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = LYRICS_NETWORK_TIMEOUT_MS
            connection.readTimeout = LYRICS_NETWORK_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", LRCLIB_USER_AGENT)
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            code to body
        } finally {
            connection.disconnect()
        }
    }

    private fun parseLrclibObject(value: JSONObject): LrclibLyrics? {
        if (value.optBoolean("instrumental", false)) return null
        val plain = jsonString(value, "plainLyrics")
        val synced = jsonString(value, "syncedLyrics")
        val display = plain ?: synced?.let(::stripLrcTimestamps) ?: return null
        if (display.isBlank()) return null
        return LrclibLyrics(display.trim(), synced?.trim()?.takeIf(String::isNotBlank))
    }

    private fun jsonString(value: JSONObject, key: String): String? {
        val raw = value.opt(key)
        return if (raw == null || raw == JSONObject.NULL) null else raw.toString().takeIf(String::isNotBlank)
    }

    private fun stripLrcTimestamps(value: String): String = value
        .lineSequence()
        .map { line -> line.replace(Regex("^\\s*(?:\\[[^]]+])+\\s*"), "") }
        .filter(String::isNotBlank)
        .joinToString("\n")
        .trim()

    private fun lyricsJson(
        trackId: String,
        provider: String,
        text: String,
        syncedText: String?,
    ): String = JSONObject()
        .put("trackId", trackId)
        .put("provider", provider)
        .put("synced", !syncedText.isNullOrBlank())
        .put("text", text)
        .apply {
            if (!syncedText.isNullOrBlank()) put("syncedText", syncedText)
        }
        .toString()

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun directAudioFormats(
        formats: List<com.metrolist.innertube.models.response.PlayerResponse.StreamingData.Format>,
    ): List<com.metrolist.innertube.models.response.PlayerResponse.StreamingData.Format> = formats
        .asSequence()
        .filter { it.isAudio && it.isOriginal && !it.url.isNullOrBlank() }
        .sortedByDescending { format ->
            (format.averageBitrate ?: format.bitrate) +
                if (format.mimeType.startsWith("audio/webm")) 10_000 else 0
        }
        .toList()

    private fun resolvedWebAudioFormats(
        formats: List<com.metrolist.innertube.models.response.PlayerResponse.StreamingData.Format>,
        videoId: String,
        client: YouTubeClient,
    ): List<ResolvedAudioStream> = formats
        .asSequence()
        .filter { it.isAudio && it.isOriginal }
        .mapNotNull { format ->
            val solved = runCatching { NewPipeExtractor.getStreamUrl(format, videoId) }.getOrNull()
                ?: format.url?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            ResolvedAudioStream(
                url = patchClientVersion(solved, client.clientVersion),
                mimeType = format.mimeType,
                bitrate = format.averageBitrate ?: format.bitrate,
                durationMs = format.approxDurationMs?.toLongOrNull() ?: 0L,
            )
        }
        .sortedByDescending { stream ->
            stream.bitrate + if (stream.mimeType.startsWith("audio/webm")) 10_000 else 0
        }
        .toList()

    private fun streamsJson(
        audio: List<com.metrolist.innertube.models.response.PlayerResponse.StreamingData.Format>,
        client: YouTubeClient,
        poToken: String?,
    ): String = JSONArray().apply {
        audio.forEach { format ->
            val baseUrl = format.url.orEmpty()
            val url = appendPoToken(baseUrl, poToken)
            val headersJson = JSONObject().apply {
                client.mediaHeaders().forEach { (name, value) -> put(name, value) }
            }
            put(
                JSONObject()
                    .put("label", qualityLabel(format.mimeType, format.averageBitrate ?: format.bitrate))
                    .put("url", url)
                    .put("headers", headersJson)
                    .put("mimeType", format.mimeType.substringBefore(';'))
                    .put("bitrate", format.averageBitrate ?: format.bitrate)
                    .put("durationMs", format.approxDurationMs?.toLongOrNull() ?: 0L)
            )
        }
    }.toString()

    private fun resolvedStreamsJson(
        audio: List<ResolvedAudioStream>,
        client: YouTubeClient,
        poToken: String?,
    ): String = JSONArray().apply {
        audio.forEach { stream ->
            val headersJson = JSONObject().apply {
                client.mediaHeaders().forEach { (name, value) -> put(name, value) }
            }
            put(
                JSONObject()
                    .put("label", qualityLabel(stream.mimeType, stream.bitrate))
                    .put("url", appendPoToken(stream.url, poToken))
                    .put("headers", headersJson)
                    .put("mimeType", stream.mimeType.substringBefore(';'))
                    .put("bitrate", stream.bitrate)
                    .put("durationMs", stream.durationMs)
            )
        }
    }.toString()

    private fun newPipeStreamsJson(audio: List<NewPipeAudioStream>): String = JSONArray().apply {
        audio.forEach { stream ->
            val headersJson = JSONObject().apply {
                YouTubeClient.forStreamUrl(stream.url).mediaHeaders().forEach { (name, value) -> put(name, value) }
            }
            put(
                JSONObject()
                    .put("label", qualityLabel(stream.mimeType, stream.bitrate))
                    .put("url", stream.url)
                    .put("headers", headersJson)
                    .put("mimeType", stream.mimeType.substringBefore(';'))
                    .put("bitrate", stream.bitrate)
                    .put("durationMs", stream.durationMs)
            )
        }
    }.toString()

    private data class ResolvedAudioStream(
        val url: String,
        val mimeType: String,
        val bitrate: Int,
        val durationMs: Long,
    )

    private data class NewPipeAudioStream(
        val itag: Int,
        val url: String,
        val mimeType: String,
        val bitrate: Int,
        val durationMs: Long,
    )

    private fun authenticatedWebClient(): YouTubeClient = WEB_REMIX.copy(
        userAgent = sessionUserAgent?.takeIf(String::isNotBlank) ?: WEB_REMIX.userAgent,
    )

    private fun hasSignedInCookie(cookie: String?): Boolean = cookie
        ?.split(';')
        ?.any { token ->
            token.substringBefore('=').trim() == "SAPISID" && token.substringAfter('=', "").trim().isNotEmpty()
        } == true

    private fun appendPoToken(url: String, poToken: String?): String {
        if (poToken.isNullOrBlank()) return url
        return "$url${if ('?' in url) '&' else '?'}pot=${Uri.encode(poToken)}"
    }

    private fun patchClientVersion(url: String, clientVersion: String): String =
        if ("cver=" in url) url.replace(Regex("cver=[^&]+"), "cver=$clientVersion") else url

    private fun mimeTypeForAudioItag(itag: Int): String = when (itag) {
        139, 140, 141, 256, 258, 325, 328 -> "audio/mp4"
        else -> "audio/webm"
    }

    private fun bitrateForAudioItag(itag: Int): Int = when (itag) {
        139 -> 48_000
        249 -> 50_000
        250 -> 70_000
        140 -> 128_000
        251 -> 160_000
        141, 256 -> 256_000
        258, 325, 328 -> 320_000
        else -> 128_000
    }

    private suspend fun ensureVisitorData() {
        if (YouTube.visitorData.isNullOrBlank()) {
            YouTube.visitorData = YouTube.visitorData().getOrNull()
        }
    }

    private fun requireSource(sourceId: String) {
        require(sourceId == SOURCE_ID) { "Unsupported YouTube Music source: $sourceId" }
    }

    private fun qualityLabel(mimeType: String, bitrate: Int): String {
        val codec = when {
            mimeType.contains("opus", ignoreCase = true) || mimeType.contains("webm", ignoreCase = true) -> "Opus"
            mimeType.contains("mp4a", ignoreCase = true) || mimeType.contains("mp4", ignoreCase = true) -> "AAC"
            else -> "Audio"
        }
        val kbps = (bitrate / 1000).coerceAtLeast(1)
        return "YouTube Music · $codec · $kbps kbps"
    }
}
