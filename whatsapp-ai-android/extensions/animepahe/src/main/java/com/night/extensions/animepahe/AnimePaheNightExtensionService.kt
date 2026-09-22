package com.night.extensions.animepahe

import com.night.extension.sdk.NightActionRequest
import com.night.extension.sdk.NightExtensionService
import com.night.extension.sdk.NightExtensionStandardActions
import com.night.extension.sdk.NightMessageTypeDescriptor
import com.night.extension.sdk.NightToolDescriptor
import com.night.extension.sdk.nightAction
import com.night.extension.sdk.nightBrowserSpec
import com.night.extension.sdk.nightConfiguration
import com.night.extension.sdk.nightConfigurationField
import com.night.extension.sdk.nightConfigurationOption
import com.night.extension.sdk.nightConfigurationSection
import com.night.extension.sdk.nightExtensionDescriptor
import com.night.extension.sdk.nightExtensionMessage
import com.night.extension.sdk.nightMetadata
import com.night.extension.sdk.nightRow
import com.night.extension.sdk.NightToolRequest
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

class AnimePaheNightExtensionService : NightExtensionService() {
    private val store by lazy { AnimePaheSessionStore(this) }
    private val client by lazy { AnimePaheClient(store) }

    override fun descriptor(): JSONObject =
        nightExtensionDescriptor(
            extensionId = EXTENSION_ID,
            name = EXTENSION_NAME,
            capabilities =
                listOf(
                    "anime",
                    "video_streaming",
                    "downloads",
                    "media_library",
                ),
            tags =
                listOf(
                    "episodes",
                    "subtitles",
                    "streaming",
                    "downloads",
                ),
            tools =
                listOf(
                    NightToolDescriptor(
                        name = TOOL_SEARCH,
                        description =
                            "Search AnimePahe for anime titles and render matching anime cards.",
                        parameters =
                            objectParameters(
                                "query" to stringProperty(
                                    "Anime title to search for."
                                )
                            ),
                        readOnly = true,
                    ),
                    NightToolDescriptor(
                        name = TOOL_EPISODES,
                        description =
                            "List an anime's AnimePahe episodes for a page and render episode cards.",
                        parameters =
                            objectParameters(
                                "animeSession" to stringProperty(
                                    "AnimePahe anime session id."
                                ),
                                "title" to stringProperty(
                                    "Anime title shown to the user."
                                ),
                                "page" to integerProperty(
                                    "Episode result page, starting at 1."
                                ),
                            ),
                        readOnly = true,
                    ),
                    NightToolDescriptor(
                        name = TOOL_RESOLVE,
                        description =
                            "Resolve an AnimePahe episode into a playable/downloadable source card.",
                        parameters =
                            objectParameters(
                                "animeSession" to stringProperty(
                                    "AnimePahe anime session id."
                                ),
                                "episodeSession" to stringProperty(
                                    "AnimePahe episode session id."
                                ),
                                "title" to stringProperty(
                                    "Anime title."
                                ),
                                "episode" to stringProperty(
                                    "Episode number or label."
                                ),
                            ),
                        readOnly = true,
                    ),
                    NightToolDescriptor(
                        name = TOOL_SETTINGS,
                        description =
                            "Show this extension's provider-owned settings card.",
                        readOnly = true,
                    ),
                ),
            messageTypes =
                listOf(
                    NightMessageTypeDescriptor(
                        messageType = TYPE_ANIME,
                        template = "media_card",
                        description = "AnimePahe anime search result.",
                        whenToUse =
                            "Use for one AnimePahe anime title returned from search.",
                    ),
                    NightMessageTypeDescriptor(
                        messageType = TYPE_EPISODE,
                        template = "media_card",
                        description = "AnimePahe episode result.",
                        whenToUse =
                            "Use for an episode that can be resolved to a media source.",
                    ),
                    NightMessageTypeDescriptor(
                        messageType = TYPE_SOURCE,
                        template = "media_card",
                        description = "Resolved playable/downloadable AnimePahe episode.",
                        whenToUse =
                            "Use after resolving the user's chosen episode.",
                    ),
                    NightMessageTypeDescriptor(
                        messageType = TYPE_SETTINGS,
                        template = "configuration_card",
                        description = "AnimePahe extension settings.",
                        whenToUse =
                            "Use when the user asks to configure this provider.",
                    ),
                    NightMessageTypeDescriptor(
                        messageType = TYPE_VERIFY,
                        template = "browser_card",
                        description = "Manual browser verification for AnimePahe or its file host.",
                        whenToUse =
                            "Use only when a provider request is blocked by browser verification.",
                    ),
                ),
        )

    override fun executeTool(
        request: NightToolRequest,
    ): JSONObject {
        val arguments =
            runCatching { JSONObject(request.argumentsJson) }
                .getOrElse { JSONObject() }

        return executeToolByName(
            toolName = request.toolName,
            arguments = arguments,
        )
    }

    override fun executeAction(
        request: NightActionRequest,
    ): JSONObject {
        val payload =
            runCatching { JSONObject(request.payloadJson) }
                .getOrElse { JSONObject() }

        return when (request.actionId) {
            ACTION_EPISODES ->
                runWithVerification(
                    retryTool = TOOL_EPISODES,
                    retryArguments =
                        JSONObject()
                            .put(
                                "animeSession",
                                payload.optString("animeSession"),
                            )
                            .put(
                                "title",
                                payload.optString("title"),
                            )
                            .put("page", 1),
                ) {
                    episodeResult(
                        animeSession = payload.optString("animeSession"),
                        title = payload.optString("title"),
                        page = 1,
                    )
                }

            ACTION_RESOLVE ->
                runWithVerification(
                    retryTool = TOOL_RESOLVE,
                    retryArguments =
                        JSONObject()
                            .put(
                                "animeSession",
                                payload.optString("animeSession"),
                            )
                            .put(
                                "episodeSession",
                                payload.optString("episodeSession"),
                            )
                            .put(
                                "title",
                                payload.optString("title"),
                            )
                            .put(
                                "episode",
                                payload.optString("episode"),
                            ),
                ) {
                    resolveResult(
                        animeSession =
                            payload.optString("animeSession"),
                        episodeSession =
                            payload.optString("episodeSession"),
                        title = payload.optString("title"),
                        episode = payload.optString("episode"),
                    )
                }

            ACTION_VERIFY -> verifyAndRetry(payload)

            ACTION_SAVE_CONFIG -> {
                val values =
                    payload.optJSONObject("values")
                        ?: JSONObject()
                store.saveConfiguration(values)
                JSONObject()
                    .put("ok", true)
                    .put("message", "AnimePahe settings saved.")
            }

            NightExtensionStandardActions.ADD_TO_LIBRARY,
            NightExtensionStandardActions.REMOVE_FROM_LIBRARY,
            NightExtensionStandardActions.ADD_TO_PLAYLIST,
            NightExtensionStandardActions.REMOVE_FROM_PLAYLIST ->
                JSONObject()
                    .put("ok", true)
                    .put("nightHandled", true)

            else ->
                JSONObject()
                    .put("ok", false)
                    .put(
                        "error",
                        "Unknown AnimePahe action: ${request.actionId}",
                    )
        }
    }

    private fun executeToolByName(
        toolName: String,
        arguments: JSONObject,
    ): JSONObject =
        when (toolName) {
            TOOL_SEARCH -> {
                val query = arguments.optString("query").trim()
                runWithVerification(
                    retryTool = TOOL_SEARCH,
                    retryArguments =
                        JSONObject().put("query", query),
                ) {
                    searchResult(query)
                }
            }

            TOOL_EPISODES -> {
                val animeSession =
                    arguments.optString("animeSession").trim()
                val title =
                    arguments.optString("title")
                        .trim()
                        .ifBlank { "Anime" }
                val page =
                    arguments.optInt("page", 1)
                        .coerceAtLeast(1)
                runWithVerification(
                    retryTool = TOOL_EPISODES,
                    retryArguments =
                        JSONObject()
                            .put("animeSession", animeSession)
                            .put("title", title)
                            .put("page", page),
                ) {
                    episodeResult(
                        animeSession = animeSession,
                        title = title,
                        page = page,
                    )
                }
            }

            TOOL_RESOLVE -> {
                val animeSession =
                    arguments.optString("animeSession").trim()
                val episodeSession =
                    arguments.optString("episodeSession").trim()
                val title =
                    arguments.optString("title")
                        .trim()
                        .ifBlank { "Anime" }
                val episode =
                    arguments.optString("episode")
                        .trim()
                        .ifBlank { "Episode" }
                runWithVerification(
                    retryTool = TOOL_RESOLVE,
                    retryArguments =
                        JSONObject()
                            .put("animeSession", animeSession)
                            .put("episodeSession", episodeSession)
                            .put("title", title)
                            .put("episode", episode),
                ) {
                    resolveResult(
                        animeSession = animeSession,
                        episodeSession = episodeSession,
                        title = title,
                        episode = episode,
                    )
                }
            }

            TOOL_SETTINGS -> settingsResult()

            else ->
                JSONObject()
                    .put("ok", false)
                    .put(
                        "error",
                        "Unknown AnimePahe tool: $toolName",
                    )
        }

    private fun searchResult(
        query: String,
    ): JSONObject {
        val results = client.search(query)
        val messages =
            results.take(MAX_MESSAGES_PER_RESULT)
                .map(::animeCard)

        return JSONObject()
            .put("ok", true)
            .put("query", query)
            .put("count", results.size)
            .put("night_messages", JSONArray(messages))
            .put(
                "moreAvailable",
                results.size > messages.size,
            )
    }

    private fun episodeResult(
        animeSession: String,
        title: String,
        page: Int,
    ): JSONObject {
        val result =
            client.episodes(
                animeSession = animeSession,
                page = page,
            )
        val messages =
            result.items
                .take(MAX_MESSAGES_PER_RESULT)
                .map { episode ->
                    episodeCard(
                        animeSession = animeSession,
                        animeTitle = title,
                        episode = episode,
                    )
                }

        return JSONObject()
            .put("ok", true)
            .put("currentPage", result.currentPage)
            .put("lastPage", result.lastPage)
            .put(
                "hasNextPage",
                result.currentPage < result.lastPage,
            )
            .put("night_messages", JSONArray(messages))
    }

    private fun resolveResult(
        animeSession: String,
        episodeSession: String,
        title: String,
        episode: String,
    ): JSONObject {
        val sources =
            client.sources(
                animeSession = animeSession,
                episodeSession = episodeSession,
            )
        val selected =
            client.selectPreferredSource(sources)
        val resolved =
            client.resolveDirectMp4(selected)

        return JSONObject()
            .put("ok", true)
            .put(
                "night_message",
                sourceCard(
                    animeSession = animeSession,
                    episodeSession = episodeSession,
                    title = title,
                    episode = episode,
                    source = selected,
                    resolved = resolved,
                )
            )
    }

    private fun settingsResult(): JSONObject =
        JSONObject()
            .put("ok", true)
            .put("night_message", settingsCard())

    private fun verifyAndRetry(
        payload: JSONObject,
    ): JSONObject {
        val browserSession =
            payload.optJSONObject("browserSession")
                ?: return JSONObject()
                    .put("verified", false)
                    .put(
                        "message",
                        "Night did not provide a browser session.",
                    )
        val host = browserSession.optString("host").trim()
        val cookieHeader =
            browserSession.optString("cookieHeader").trim()

        if (host.isBlank() || cookieHeader.isBlank()) {
            return JSONObject()
                .put("verified", false)
                .put(
                    "message",
                    "No verified browser cookies were available yet.",
                )
        }

        store.saveBrowserSession(
            host = host,
            cookieHeader = cookieHeader,
        )

        val extensionPayload =
            payload.optJSONObject("extensionPayload")
                ?: JSONObject()
        val retryTool =
            extensionPayload.optString("retryTool").trim()
        val retryArguments =
            extensionPayload.optJSONObject("retryArguments")
                ?: JSONObject()

        val retried =
            if (retryTool.isNotBlank()) {
                executeToolByName(
                    toolName = retryTool,
                    arguments = retryArguments,
                )
            } else {
                JSONObject().put("ok", true)
            }

        return JSONObject(retried.toString())
            .put("verified", true)
            .put("status", "verified")
            .put(
                "message",
                "Session verified. AnimePahe retried the blocked request.",
            )
    }

    private inline fun runWithVerification(
        retryTool: String,
        retryArguments: JSONObject,
        block: () -> JSONObject,
    ): JSONObject =
        try {
            block()
        } catch (challenge: AnimePaheVerificationRequired) {
            verificationResult(
                challenge = challenge,
                retryTool = retryTool,
                retryArguments = retryArguments,
            )
        }

    private fun verificationResult(
        challenge: AnimePaheVerificationRequired,
        retryTool: String,
        retryArguments: JSONObject,
    ): JSONObject {
        val host =
            challenge.host
                .trim()
                .ifBlank {
                    runCatching {
                        URI(challenge.verificationUrl)
                            .host
                            .orEmpty()
                    }.getOrDefault("")
                }
        val allowedHosts =
            listOfNotNull(
                host.takeIf { it.isNotBlank() },
                runCatching {
                    URI(store.baseUrl()).host.orEmpty()
                }.getOrNull()
                    ?.takeIf { it.isNotBlank() },
            ).distinct()

        return JSONObject()
            .put("ok", false)
            .put("verificationRequired", true)
            .put(
                "night_message",
                nightExtensionMessage(
                    extensionId = EXTENSION_ID,
                    messageType = TYPE_VERIFY,
                    template = "browser_card",
                    extensionName = EXTENSION_NAME,
                    title = "Verify AnimePahe",
                    subtitle =
                        "Complete the browser check, then tap Verify.",
                    body = challenge.message.orEmpty(),
                    badge = "Verification",
                    status = "Waiting",
                    actions =
                        listOf(
                            nightAction(
                                id = ACTION_VERIFY,
                                label = "Verify",
                                style = "primary",
                            )
                        ),
                    extensionPayload =
                        JSONObject()
                            .put("retryTool", retryTool)
                            .put(
                                "retryArguments",
                                retryArguments,
                            ),
                    browser =
                        nightBrowserSpec(
                            sessionId =
                                "animepahe." +
                                    host.replace(
                                        Regex("[^A-Za-z0-9._-]"),
                                        "_",
                                    ),
                            initialUrl =
                                challenge.verificationUrl,
                            allowedHosts = allowedHosts,
                            title = "AnimePahe verification",
                            verifyActionId = ACTION_VERIFY,
                            verifyLabel = "Verify",
                            userAgent = store.userAgent(),
                        ),
                )
            )
    }

    private fun animeCard(
        item: AnimePaheSearchItem,
    ): JSONObject {
        val payload =
            JSONObject()
                .put(
                    "mediaId",
                    item.id.ifBlank { item.session },
                )
                .put("mediaKind", "anime")
                .put("animeSession", item.session)
                .put("title", item.title)

        return nightExtensionMessage(
            extensionId = EXTENSION_ID,
            messageType = TYPE_ANIME,
            template = "media_card",
            extensionName = EXTENSION_NAME,
            title = item.title,
            subtitle =
                listOf(item.type, item.year)
                    .filter { it.isNotBlank() }
                    .joinToString(" • "),
            body =
                item.episodes
                    .takeIf { it.isNotBlank() }
                    ?.let { "$it episodes" }
                    .orEmpty(),
            artworkPath = item.poster,
            badge = item.status,
            metadata =
                listOfNotNull(
                    item.type
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            nightMetadata(
                                "Type",
                                it,
                            )
                        },
                    item.year
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            nightMetadata(
                                "Year",
                                it,
                            )
                        },
                    item.episodes
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            nightMetadata(
                                "Episodes",
                                it,
                            )
                        },
                ),
            actions =
                listOf(
                    nightAction(
                        id = ACTION_EPISODES,
                        label = "Episodes",
                        style = "primary",
                    ),
                    nightAction(
                        id =
                            NightExtensionStandardActions
                                .ADD_TO_LIBRARY,
                        label = "Add to Library",
                        requiresExtension = false,
                    ),
                    nightAction(
                        id =
                            NightExtensionStandardActions
                                .ADD_TO_PLAYLIST,
                        label = "Add to Playlist",
                        requiresExtension = false,
                    ),
                ),
            extensionPayload = payload,
        )
    }

    private fun episodeCard(
        animeSession: String,
        animeTitle: String,
        episode: AnimePaheEpisode,
    ): JSONObject {
        val payload =
            JSONObject()
                .put(
                    "mediaId",
                    animeSession + ":" + episode.session,
                )
                .put("mediaKind", "anime_episode")
                .put("animeSession", animeSession)
                .put(
                    "episodeSession",
                    episode.session,
                )
                .put("title", animeTitle)
                .put("episode", episode.number)

        return nightExtensionMessage(
            extensionId = EXTENSION_ID,
            messageType = TYPE_EPISODE,
            template = "media_card",
            extensionName = EXTENSION_NAME,
            title =
                animeTitle +
                    " • Episode " +
                    episode.number,
            subtitle = episode.title,
            artworkPath = episode.snapshot,
            badge = episode.duration,
            metadata =
                listOfNotNull(
                    episode.duration
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            nightMetadata(
                                "Duration",
                                it,
                            )
                        },
                    episode.createdAt
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            nightMetadata(
                                "Released",
                                it,
                            )
                        },
                ),
            actions =
                listOf(
                    nightAction(
                        id = ACTION_RESOLVE,
                        label = "Watch",
                        style = "primary",
                    )
                ),
            extensionPayload = payload,
        )
    }

    private fun sourceCard(
        animeSession: String,
        episodeSession: String,
        title: String,
        episode: String,
        source: AnimePaheSource,
        resolved: AnimePaheResolvedMedia,
    ): JSONObject {
        val headers =
            JSONObject().apply {
                resolved.headers.forEach {
                        (key, value),
                    ->
                    put(key, value)
                }
            }
        val quality =
            source.resolution
                ?.let { "${it}p" }
                .orEmpty()
        val safeEpisode =
            episode
                .replace(
                    Regex("[^A-Za-z0-9._ -]+"),
                    "_",
                )
                .ifBlank { "episode" }
        val fileName =
            (
                title
                    .replace(
                        Regex("[^A-Za-z0-9._ -]+"),
                        "_",
                    )
                    .ifBlank { "Anime" }
                ) +
                " - " +
                safeEpisode +
                (
                    quality
                        .takeIf { it.isNotBlank() }
                        ?.let { " $it" }
                        .orEmpty()
                    ) +
                ".mp4"

        val payload =
            JSONObject()
                .put(
                    "mediaId",
                    animeSession + ":" + episodeSession,
                )
                .put("mediaKind", "anime_episode")
                .put("title", title + " • Episode " + episode)
                .put("episode", episode)
                .put("mediaUrl", resolved.url)
                .put("mimeType", resolved.mimeType)
                .put("fileName", fileName)
                .put("headers", headers)

        return nightExtensionMessage(
            extensionId = EXTENSION_ID,
            messageType = TYPE_SOURCE,
            template = "media_card",
            extensionName = EXTENSION_NAME,
            title = title + " • Episode " + episode,
            subtitle =
                listOf(
                    quality,
                    source.audio,
                    source.fansub,
                )
                    .filter { it.isNotBlank() }
                    .joinToString(" • "),
            body =
                "Resolved by AnimePahe. Playback and downloading are handled by Night.",
            badge =
                quality.ifBlank { "Ready" },
            status = "Ready",
            actions =
                listOf(
                    nightAction(
                        id =
                            NightExtensionStandardActions
                                .PLAY_MEDIA,
                        label = "Play",
                        style = "primary",
                        requiresExtension = false,
                    ),
                    nightAction(
                        id =
                            NightExtensionStandardActions
                                .DOWNLOAD_MEDIA,
                        label = "Download",
                        requiresExtension = false,
                    ),
                ),
            extensionPayload = payload,
        )
    }

    private fun settingsCard(): JSONObject {
        val configuration =
            nightConfiguration(
                id = "animepahe",
                sections =
                    listOf(
                        nightConfigurationSection(
                            id = "playback",
                            title = "Playback",
                            description =
                                "Defaults used when resolving an episode.",
                        ),
                        nightConfigurationSection(
                            id = "downloads",
                            title = "Downloads",
                            description =
                                "Provider-owned download defaults.",
                        ),
                        nightConfigurationSection(
                            id = "advanced",
                            title = "Advanced",
                            description =
                                "Connection settings for this extension only.",
                        ),
                    ),
                fields =
                    listOf(
                        nightConfigurationField(
                            id = "quality",
                            label = "Preferred quality",
                            type = "single_choice",
                            value = store.quality(),
                            sectionId = "playback",
                            options =
                                listOf(
                                    nightConfigurationOption(
                                        "auto",
                                        "Auto",
                                    ),
                                    nightConfigurationOption(
                                        "1080",
                                        "1080p",
                                    ),
                                    nightConfigurationOption(
                                        "720",
                                        "720p",
                                    ),
                                    nightConfigurationOption(
                                        "360",
                                        "360p",
                                    ),
                                ),
                        ),
                        nightConfigurationField(
                            id = "audio",
                            label = "Preferred audio",
                            type = "single_choice",
                            value = store.audio(),
                            sectionId = "playback",
                            options =
                                listOf(
                                    nightConfigurationOption(
                                        "sub",
                                        "Original / sub",
                                    ),
                                    nightConfigurationOption(
                                        "eng",
                                        "English",
                                    ),
                                    nightConfigurationOption(
                                        "kor",
                                        "Korean",
                                    ),
                                    nightConfigurationOption(
                                        "chi",
                                        "Chinese",
                                    ),
                                ),
                        ),
                        nightConfigurationField(
                            id = "parallel_downloads",
                            label = "Parallel downloads",
                            type = "number",
                            value =
                                store.parallelDownloads()
                                    .toString(),
                            sectionId = "downloads",
                            suffix = "downloads",
                            min = 1.0,
                            max = 6.0,
                            step = 1.0,
                        ),
                        nightConfigurationField(
                            id = "base_url",
                            label = "Provider base URL",
                            type = "text",
                            value = store.baseUrl(),
                            sectionId = "advanced",
                            advanced = true,
                        ),
                        nightConfigurationField(
                            id = "user_agent",
                            label = "Browser user agent",
                            type = "text",
                            value = store.userAgent(),
                            sectionId = "advanced",
                            advanced = true,
                        ),
                    ),
                submitActionId = ACTION_SAVE_CONFIG,
            )

        return nightExtensionMessage(
            extensionId = EXTENSION_ID,
            messageType = TYPE_SETTINGS,
            template = "configuration_card",
            extensionName = EXTENSION_NAME,
            title = "AnimePahe settings",
            subtitle =
                "These settings belong to this extension, not Night.",
            configuration = configuration,
        )
    }

    private fun objectParameters(
        vararg properties: Pair<String, JSONObject>,
    ): JSONObject =
        JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject().apply {
                    properties.forEach {
                            (name, schema),
                        ->
                        put(name, schema)
                    }
                },
            )
            .put(
                "required",
                JSONArray(
                    properties
                        .filterNot {
                            it.first == "page" ||
                                it.first == "title"
                        }
                        .map { it.first }
                ),
            )
            .put("additionalProperties", false)

    private fun stringProperty(
        description: String,
    ): JSONObject =
        JSONObject()
            .put("type", "string")
            .put("description", description)

    private fun integerProperty(
        description: String,
    ): JSONObject =
        JSONObject()
            .put("type", "integer")
            .put("minimum", 1)
            .put("description", description)

    companion object {
        const val EXTENSION_ID = "animepahe"
        const val EXTENSION_NAME = "AnimePahe"

        const val TOOL_SEARCH = "search_anime"
        const val TOOL_EPISODES = "get_episodes"
        const val TOOL_RESOLVE = "resolve_episode"
        const val TOOL_SETTINGS = "show_settings"

        const val ACTION_EPISODES = "episodes"
        const val ACTION_RESOLVE = "resolve_episode"
        const val ACTION_VERIFY = "verify_session"
        const val ACTION_SAVE_CONFIG = "save_config"

        const val TYPE_ANIME = "animepahe.anime"
        const val TYPE_EPISODE = "animepahe.episode"
        const val TYPE_SOURCE = "animepahe.source"
        const val TYPE_SETTINGS = "animepahe.settings"
        const val TYPE_VERIFY = "animepahe.verify"

        private const val MAX_MESSAGES_PER_RESULT = 6
    }
}
