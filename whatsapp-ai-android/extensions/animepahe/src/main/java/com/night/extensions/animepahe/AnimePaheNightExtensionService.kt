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
    private val hlsResolver by lazy { PaheBatcherHlsResolver(store) }
    private val downloadQueue by lazy { AnimePaheDownloadQueue(this) }

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
                        name = TOOL_DETAILS,
                        description =
                            "Load AnimePahe title details and render a detailed anime card.",
                        parameters =
                            objectParameters(
                                "animeSession" to stringProperty(
                                    "AnimePahe anime session id."
                                ),
                                "title" to stringProperty(
                                    "Anime title shown to the user."
                                ),
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
                                "offset" to nonNegativeIntegerProperty(
                                    "Zero-based card offset within the AnimePahe page."
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
                        messageType = TYPE_DETAILS,
                        template = "media_card",
                        description = "Detailed AnimePahe anime card.",
                        whenToUse =
                            "Use after the user opens an AnimePahe search result.",
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
                        messageType = TYPE_DOWNLOAD,
                        template = "media_card",
                        description = "AnimePahe background download status.",
                        whenToUse =
                            "Use after the user starts an AnimePahe episode download.",
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
                        description = "Manual browser verification for AnimePahe.",
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
            ACTION_DETAILS ->
                runWithVerification(
                    retryTool = TOOL_DETAILS,
                    retryArguments =
                        JSONObject()
                            .put(
                                "animeSession",
                                payload.optString("animeSession"),
                            )
                            .put(
                                "title",
                                payload.optString("title"),
                            ),
                ) {
                    detailsResult(
                        animeSession =
                            payload.optString("animeSession"),
                        title =
                            payload.optString("title"),
                    )
                }

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
                        offset = 0,
                    )
                }

            ACTION_EPISODES_NEXT ->
                episodeNavigationResult(
                    payload = payload,
                    direction = 1,
                )

            ACTION_EPISODES_PREVIOUS ->
                episodeNavigationResult(
                    payload = payload,
                    direction = -1,
                )

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

            ACTION_DOWNLOAD -> queueDownload(payload)

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

            TOOL_DETAILS -> {
                val animeSession =
                    arguments.optString("animeSession")
                        .trim()
                val title =
                    arguments.optString("title")
                        .trim()
                        .ifBlank { "Anime" }
                runWithVerification(
                    retryTool = TOOL_DETAILS,
                    retryArguments =
                        JSONObject()
                            .put(
                                "animeSession",
                                animeSession,
                            )
                            .put("title", title),
                ) {
                    detailsResult(
                        animeSession = animeSession,
                        title = title,
                    )
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
                val offset =
                    arguments.optInt("offset", 0)
                        .coerceAtLeast(0)
                runWithVerification(
                    retryTool = TOOL_EPISODES,
                    retryArguments =
                        JSONObject()
                            .put("animeSession", animeSession)
                            .put("title", title)
                            .put("page", page)
                            .put("offset", offset),
                ) {
                    episodeResult(
                        animeSession = animeSession,
                        title = title,
                        page = page,
                        offset = offset,
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

    private fun detailsResult(
        animeSession: String,
        title: String,
    ): JSONObject {
        val details =
            client.details(
                animeSession = animeSession,
                titleHint = title,
            )

        return JSONObject()
            .put("ok", true)
            .put(
                "night_message",
                detailsCard(details),
            )
    }

    private fun episodeResult(
        animeSession: String,
        title: String,
        page: Int,
        offset: Int = 0,
    ): JSONObject {
        val result =
            client.episodes(
                animeSession = animeSession,
                page = page,
            )

        val safeOffset =
            offset.coerceIn(
                0,
                result.items.size.coerceAtLeast(0),
            )
        val chunk =
            result.items
                .drop(safeOffset)
                .take(MAX_MESSAGES_PER_RESULT)

        val messages =
            chunk.map { episode ->
                episodeCard(
                    animeSession = animeSession,
                    animeTitle = title,
                    episode = episode,
                )
            }.toMutableList()

        val hasPrevious =
            safeOffset > 0 ||
                result.currentPage > 1
        val hasNext =
            safeOffset + chunk.size <
                result.items.size ||
                result.currentPage <
                    result.lastPage

        if (hasPrevious || hasNext) {
            messages +=
                episodeNavigationCard(
                    animeSession = animeSession,
                    title = title,
                    page = result.currentPage,
                    offset = safeOffset,
                    pageItemCount =
                        result.items.size,
                    lastPage = result.lastPage,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                )
        }

        return JSONObject()
            .put("ok", true)
            .put(
                "currentPage",
                result.currentPage,
            )
            .put("lastPage", result.lastPage)
            .put("offset", safeOffset)
            .put("pageItemCount", result.items.size)
            .put("hasPrevious", hasPrevious)
            .put("hasNext", hasNext)
            .put(
                "night_messages",
                JSONArray(messages),
            )
    }

    private fun episodeNavigationResult(
        payload: JSONObject,
        direction: Int,
    ): JSONObject {
        val animeSession =
            payload.optString("animeSession")
                .trim()
        val title =
            payload.optString("title")
                .trim()
                .ifBlank { "Anime" }
        val page =
            payload.optInt("page", 1)
                .coerceAtLeast(1)
        val offset =
            payload.optInt("offset", 0)
                .coerceAtLeast(0)
        val pageItemCount =
            payload.optInt(
                "pageItemCount",
                0,
            ).coerceAtLeast(0)
        val lastPage =
            payload.optInt(
                "lastPage",
                page,
            ).coerceAtLeast(page)

        val nextPage: Int
        val nextOffset: Int

        if (direction > 0) {
            if (
                offset + MAX_MESSAGES_PER_RESULT <
                    pageItemCount
            ) {
                nextPage = page
                nextOffset =
                    offset +
                        MAX_MESSAGES_PER_RESULT
            } else {
                nextPage =
                    (page + 1)
                        .coerceAtMost(lastPage)
                nextOffset = 0
            }
        } else {
            if (offset > 0) {
                nextPage = page
                nextOffset =
                    (offset -
                        MAX_MESSAGES_PER_RESULT)
                        .coerceAtLeast(0)
            } else {
                nextPage =
                    (page - 1)
                        .coerceAtLeast(1)
                nextOffset = 0
            }
        }

        return runWithVerification(
            retryTool = TOOL_EPISODES,
            retryArguments =
                JSONObject()
                    .put(
                        "animeSession",
                        animeSession,
                    )
                    .put("title", title)
                    .put("page", nextPage)
                    .put("offset", nextOffset),
        ) {
            episodeResult(
                animeSession = animeSession,
                title = title,
                page = nextPage,
                offset = nextOffset,
            )
        }
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
            hlsResolver.resolve(
                source = selected,
                playUrl =
                    store.baseUrl() +
                        "/play/" +
                        animeSession +
                        "/" +
                        episodeSession,
            )

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

    private fun queueDownload(
        payload: JSONObject,
    ): JSONObject {
        val workId =
            downloadQueue.enqueue(
                payload = payload,
                parallelism = store.parallelDownloads(),
            )

        return JSONObject()
            .put("ok", true)
            .put("workId", workId)
            .put(
                "night_message",
                nightExtensionMessage(
                    extensionId = EXTENSION_ID,
                    messageType = TYPE_DOWNLOAD,
                    template = "media_card",
                    extensionName = EXTENSION_NAME,
                    title =
                        payload.optString("title")
                            .trim()
                            .ifBlank { "AnimePahe download" },
                    subtitle =
                        "Background download queued.",
                    body =
                        "PaheBATCHER resume, AES-128 handling, MP4 remux and TS fallback are active.",
                    badge = "Download",
                    status = "Queued",
                    extensionPayload =
                        JSONObject()
                            .put("workId", workId)
                            .put(
                                "mediaId",
                                payload.optString("mediaId"),
                            ),
                ),
            )
    }

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
        val userAgent =
            browserSession.optString("userAgent").trim()

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
        store.saveBrowserUserAgent(
            userAgent = userAgent,
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
                        id = ACTION_DETAILS,
                        label = "Details",
                        style = "primary",
                    ),
                    nightAction(
                        id = ACTION_EPISODES,
                        label = "Episodes",
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

    private fun detailsCard(
        details: AnimePaheDetails,
    ): JSONObject {
        val payload =
            JSONObject()
                .put(
                    "mediaId",
                    details.session,
                )
                .put(
                    "mediaKind",
                    "anime",
                )
                .put(
                    "animeSession",
                    details.session,
                )
                .put("title", details.title)

        val metadata =
            buildList {
                details.type
                    .takeIf { it.isNotBlank() }
                    ?.let {
                        add(
                            nightMetadata(
                                "Type",
                                it,
                            )
                        )
                    }
                details.status
                    .takeIf { it.isNotBlank() }
                    ?.let {
                        add(
                            nightMetadata(
                                "Status",
                                it,
                            )
                        )
                    }
                details.studios
                    .takeIf { it.isNotBlank() }
                    ?.let {
                        add(
                            nightMetadata(
                                "Studios",
                                it,
                            )
                        )
                    }
                details.season
                    .takeIf { it.isNotBlank() }
                    ?.let {
                        add(
                            nightMetadata(
                                "Season",
                                it,
                            )
                        )
                    }
                details.genres
                    .takeIf { it.isNotEmpty() }
                    ?.let {
                        add(
                            nightMetadata(
                                "Genres",
                                it.joinToString(", "),
                            )
                        )
                    }
            }

        return nightExtensionMessage(
            extensionId = EXTENSION_ID,
            messageType = TYPE_DETAILS,
            template = "media_card",
            extensionName = EXTENSION_NAME,
            title = details.title,
            subtitle =
                listOf(
                    details.type,
                    details.season,
                )
                    .filter { it.isNotBlank() }
                    .joinToString(" • "),
            body = details.summary,
            artworkPath = details.poster,
            badge = details.status,
            metadata = metadata,
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

    private fun episodeNavigationCard(
        animeSession: String,
        title: String,
        page: Int,
        offset: Int,
        pageItemCount: Int,
        lastPage: Int,
        hasPrevious: Boolean,
        hasNext: Boolean,
    ): JSONObject {
        val start =
            if (pageItemCount == 0) {
                0
            } else {
                offset + 1
            }
        val end =
            (offset + MAX_MESSAGES_PER_RESULT)
                .coerceAtMost(pageItemCount)

        val payload =
            JSONObject()
                .put(
                    "animeSession",
                    animeSession,
                )
                .put("title", title)
                .put("page", page)
                .put("offset", offset)
                .put(
                    "pageItemCount",
                    pageItemCount,
                )
                .put("lastPage", lastPage)

        val actions =
            buildList {
                if (hasPrevious) {
                    add(
                        nightAction(
                            id =
                                ACTION_EPISODES_PREVIOUS,
                            label = "Previous",
                        )
                    )
                }
                if (hasNext) {
                    add(
                        nightAction(
                            id =
                                ACTION_EPISODES_NEXT,
                            label = "Next",
                            style = "primary",
                        )
                    )
                }
            }

        return nightExtensionMessage(
            extensionId = EXTENSION_ID,
            messageType = TYPE_EPISODE_PAGE,
            template = "media_card",
            extensionName = EXTENSION_NAME,
            title = title + " episodes",
            subtitle =
                "Page " +
                    page +
                    " of " +
                    lastPage,
            body =
                if (pageItemCount == 0) {
                    "No episodes on this page."
                } else {
                    "Showing " +
                        start +
                        "–" +
                        end +
                        " on this AnimePahe page."
                },
            badge = "Episodes",
            actions = actions,
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
                .put("animeSession", animeSession)
                .put("episodeSession", episodeSession)
                .put("title", title + " • Episode " + episode)
                .put("episode", episode)
                .put("mediaUrl", resolved.url)
                .put("mimeType", resolved.mimeType)
                .put("fileName", fileName)
                .put("quality", source.resolution ?: 0)
                .put("audio", source.audio)
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
                "Resolved through the proven PaheBATCHER HLS path. Night plays the stream; the extension handles resumable downloads.",
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
                        id = ACTION_DOWNLOAD,
                        label = "Download",
                        requiresExtension = true,
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
                                it.first == "offset" ||
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

    private fun nonNegativeIntegerProperty(
        description: String,
    ): JSONObject =
        JSONObject()
            .put("type", "integer")
            .put("minimum", 0)
            .put("description", description)

    companion object {
        const val EXTENSION_ID = "animepahe"
        const val EXTENSION_NAME = "AnimePahe"

        const val TOOL_SEARCH = "search_anime"
        const val TOOL_DETAILS = "get_details"
        const val TOOL_EPISODES = "get_episodes"
        const val TOOL_RESOLVE = "resolve_episode"
        const val TOOL_SETTINGS = "show_settings"

        const val ACTION_DETAILS = "details"
        const val ACTION_EPISODES = "episodes"
        const val ACTION_EPISODES_NEXT = "episodes_next"
        const val ACTION_EPISODES_PREVIOUS = "episodes_previous"
        const val ACTION_RESOLVE = "resolve_episode"
        const val ACTION_VERIFY = "verify_session"
        const val ACTION_DOWNLOAD = "download_episode"
        const val ACTION_SAVE_CONFIG = "save_config"

        const val TYPE_ANIME = "animepahe.anime"
        const val TYPE_DETAILS = "animepahe.details"
        const val TYPE_EPISODE_PAGE = "animepahe.episode_page"
        const val TYPE_EPISODE = "animepahe.episode"
        const val TYPE_SOURCE = "animepahe.source"
        const val TYPE_DOWNLOAD = "animepahe.download_status"
        const val TYPE_SETTINGS = "animepahe.settings"
        const val TYPE_VERIFY = "animepahe.verify"

        private const val MAX_MESSAGES_PER_RESULT = 6
    }
}
