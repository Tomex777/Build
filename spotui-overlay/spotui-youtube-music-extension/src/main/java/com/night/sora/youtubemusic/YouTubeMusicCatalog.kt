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

object YouTubeMusicCatalog {
    private const val SOURCE_ID = "youtube.music"
    private const val TAG = "SoraYouTubeMusic"
    private const val PLAYER_ATTEMPT_TIMEOUT_MS = 15_000L
    private const val SEARCH_TIMEOUT_MS = 12_000L
    private const val SESSION_PREFS = "sora_youtube_music_session_v1"
    private const val SESSION_COOKIE = "cookie"
    private const val SESSION_USER_AGENT = "userAgent"

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
        val result = withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
            YouTube.search(clean, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
        } ?: error("Search is unavailable right now")
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

    suspend fun streams(sourceId: String, id: String): String {
        requireSource(sourceId)
        ensureVisitorData()

        // Keep the native fast path truly fast. Most native identities do not
        // require a signature timestamp, and resolving player.js through NewPipe
        // can block for a long time on challenged/cloud networks. Only pay that
        // cost if we actually reach a client that requires it.
        Log.i(
            TAG,
            "resolver start id=$id visitor=${!YouTube.visitorData.isNullOrBlank()} signedIn=${hasSignedInCookie(YouTube.cookie)}",
        )
        var signatureTimestamp: Int? = null
        var signatureTimestampResolved = false
        fun signatureTimestampFor(client: YouTubeClient): Int? {
            if (!client.useSignatureTimestamp) return null
            if (!signatureTimestampResolved) {
                signatureTimestampResolved = true
                Log.i(TAG, "signature timestamp start id=$id")
                signatureTimestamp = runCatching {
                    NewPipeExtractor.getSignatureTimestamp(id).getOrNull()
                }.getOrNull()
                Log.i(TAG, "signature timestamp result id=$id value=${signatureTimestamp ?: "none"}")
            }
            return signatureTimestamp
        }

        val failures = mutableListOf<String>()
        for (client in nativePlaybackClients) {
            val identity = "${client.clientName}/${client.clientVersion}"
            Log.i(TAG, "player start id=$id client=$identity")
            val response = withTimeoutOrNull(PLAYER_ATTEMPT_TIMEOUT_MS) {
                YouTube.player(
                    videoId = id,
                    client = client,
                    signatureTimestamp = signatureTimestampFor(client),
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
                    signatureTimestamp = signatureTimestampFor(client),
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
                    signatureTimestamp = signatureTimestampFor(WEB_REMIX),
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
            }.sortedByDescending {
                it.bitrate + if (it.mimeType.contains("mp4", ignoreCase = true)) 50_000 else 0
            }

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

    private fun directAudioFormats(
        formats: List<com.metrolist.innertube.models.response.PlayerResponse.StreamingData.Format>,
    ): List<com.metrolist.innertube.models.response.PlayerResponse.StreamingData.Format> = formats
        .asSequence()
        .filter { it.isAudio && it.isOriginal && !it.url.isNullOrBlank() }
        .sortedByDescending { format ->
            (format.averageBitrate ?: format.bitrate) +
                if (format.mimeType.contains("mp4", ignoreCase = true)) 50_000 else 0
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
            stream.bitrate + if (stream.mimeType.contains("mp4", ignoreCase = true)) 50_000 else 0
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
