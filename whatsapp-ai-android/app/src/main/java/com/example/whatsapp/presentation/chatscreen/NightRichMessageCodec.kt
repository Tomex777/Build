package com.example.whatsapp.presentation.chatscreen

import com.example.whatsapp.data.browser.NightBrowserSpecCodec
import org.json.JSONArray
import org.json.JSONObject

object NightRichMessageCodec {
    const val TYPE_BUTTONS = "buttons"
    const val TYPE_FILE_RESULT = "file_result"
    const val TYPE_ANIME = "anime"
    const val TYPE_RICH_LINK = "rich_link"
    const val TYPE_LYRICS = "lyrics"
    const val TYPE_GENERATED_IMAGE = "generated_image"
    const val TYPE_CREATION = "creation"
    const val TYPE_IMAGE_SEARCH = "image_search"
    const val TYPE_DOWNLOAD = "download"
    const val TYPE_TOOL = "tool"
    const val TYPE_BROWSER = "browser"

    fun decode(
        type: String,
        id: String,
        text: String,
        payloadJson: String,
        time: String,
        mine: Boolean,
    ): RichResultMessage? {
        val payload = runCatching { JSONObject(payloadJson) }.getOrNull() ?: JSONObject()

        return when (type) {
            TYPE_BUTTONS -> ButtonResultMessage(
                id = id,
                title = payload.optString("title").ifBlank { text },
                body = payload.optString("body"),
                actions = decodeActions(payload.optJSONArray("actions")),
                time = time,
            )

            TYPE_FILE_RESULT -> FileResultMessage(
                id = id,
                name = payload.optString("name").ifBlank { text.ifBlank { "File" } },
                detail = payload.optString("detail"),
                time = time,
            )

            TYPE_ANIME -> AnimeResultMessage(
                id = id,
                title = payload.optString("title").ifBlank { text.ifBlank { "Anime" } },
                episode = payload.optString("episode"),
                quality = payload.optString("quality"),
                size = payload.optString("size"),
                time = time,
                coverPath = payload.optString("coverPath").takeIf { it.isNotBlank() },
                mediaType = payload.optString("mediaType").ifBlank { "TV" },
                status = payload.optString("status").ifBlank { "Ongoing" },
                description = payload.optString("description"),
                primaryActionLabel = payload.optString("primaryActionLabel")
                    .ifBlank { "Play Episode 1" },
            )

            TYPE_RICH_LINK -> LinkPreviewMessage(
                id = id,
                title = payload.optString("title").ifBlank { text.ifBlank { "Link" } },
                description = payload.optString("description"),
                domain = payload.optString("domain"),
                thumbnailPath = payload.optString("thumbnailPath").takeIf { it.isNotBlank() },
                mine = mine,
                time = time,
            )

            TYPE_LYRICS -> LyricsResultMessage(
                id = id,
                title = payload.optString("title").ifBlank { text.ifBlank { "Lyrics" } },
                artist = payload.optString("artist"),
                lyrics = payload.optString("lyrics").ifBlank { text },
                source = payload.optString("source"),
                time = time,
            )

            TYPE_GENERATED_IMAGE -> GeneratedImageResultMessage(
                id = id,
                title = payload.optString("title").ifBlank { text.ifBlank { "Generated image" } },
                detail = payload.optString("detail"),
                localPath = payload.optString("localPath").takeIf { it.isNotBlank() },
                time = time,
            )

            TYPE_CREATION -> CreationResultMessage(
                id = id,
                kind = runCatching {
                    CreationKind.valueOf(payload.optString("kind"))
                }.getOrDefault(CreationKind.Image),
                state = runCatching {
                    CreationState.valueOf(payload.optString("state"))
                }.getOrDefault(CreationState.Working),
                title = payload.optString("title").ifBlank { text.ifBlank { "Creating" } },
                detail = payload.optString("detail"),
                progress = payload.takeIf { it.has("progress") }
                    ?.optDouble("progress")
                    ?.toFloat()
                    ?.coerceIn(0f, 1f),
                localPath = payload.optString("localPath").takeIf { it.isNotBlank() },
                fileName = payload.optString("fileName").takeIf { it.isNotBlank() },
                time = time,
            )

            TYPE_IMAGE_SEARCH -> ImageSearchResultMessage(
                id = id,
                source = payload.optString("source").ifBlank { text.ifBlank { "Images" } },
                resultCount = payload.optInt("resultCount", 0).coerceAtLeast(0),
                time = time,
            )

            TYPE_DOWNLOAD -> DownloadResultMessage(
                id = id,
                title = payload.optString("title").ifBlank { text.ifBlank { "Download" } },
                detail = payload.optString("detail"),
                progress = payload.optDouble("progress", 0.0)
                    .toFloat()
                    .coerceIn(0f, 1f),
                time = time,
            )

            TYPE_TOOL -> ToolResultMessage(
                id = id,
                toolName = payload.optString("toolName").ifBlank { "Tool" },
                title = payload.optString("title").ifBlank { text.ifBlank { "Tool result" } },
                subtitle = payload.optString("subtitle"),
                time = time,
                iconText = payload.optString("iconText"),
                actions = decodeActions(payload.optJSONArray("actions")),
            )

            TYPE_BROWSER -> NightBrowserSpecCodec.decode(
                payload.optJSONObject("browser")
            )?.let { spec ->
                BrowserResultMessage(
                    id = id,
                    spec = spec,
                    time = time,
                    sourceLabel = payload.optString("sourceLabel"),
                )
            }

            else -> null
        }
    }

    fun typeOf(message: RichResultMessage): String? =
        when (message) {
            is ButtonResultMessage -> TYPE_BUTTONS
            is FileResultMessage -> TYPE_FILE_RESULT
            is AnimeResultMessage -> TYPE_ANIME
            is LinkPreviewMessage -> TYPE_RICH_LINK
            is LyricsResultMessage -> TYPE_LYRICS
            is GeneratedImageResultMessage -> TYPE_GENERATED_IMAGE
            is CreationResultMessage -> TYPE_CREATION
            is ImageSearchResultMessage -> TYPE_IMAGE_SEARCH
            is DownloadResultMessage -> TYPE_DOWNLOAD
            is ToolResultMessage -> TYPE_TOOL
            is BrowserResultMessage -> TYPE_BROWSER
            is MangaResultMessage,
            is ChoiceResultMessage,
            is ExtensionResultMessage -> null
        }

    fun encodePayload(message: RichResultMessage): String =
        when (message) {
            is ButtonResultMessage -> JSONObject()
                .put("title", message.title)
                .put("body", message.body)
                .put("actions", encodeActions(message.actions))

            is FileResultMessage -> JSONObject()
                .put("name", message.name)
                .put("detail", message.detail)

            is AnimeResultMessage -> JSONObject()
                .put("title", message.title)
                .put("episode", message.episode)
                .put("quality", message.quality)
                .put("size", message.size)
                .put("coverPath", message.coverPath ?: "")
                .put("mediaType", message.mediaType)
                .put("status", message.status)
                .put("description", message.description)
                .put("primaryActionLabel", message.primaryActionLabel)

            is LinkPreviewMessage -> JSONObject()
                .put("title", message.title)
                .put("description", message.description)
                .put("domain", message.domain)
                .put("thumbnailPath", message.thumbnailPath ?: "")

            is LyricsResultMessage -> JSONObject()
                .put("title", message.title)
                .put("artist", message.artist)
                .put("lyrics", message.lyrics)
                .put("source", message.source)

            is GeneratedImageResultMessage -> JSONObject()
                .put("title", message.title)
                .put("detail", message.detail)
                .put("localPath", message.localPath ?: "")

            is CreationResultMessage -> JSONObject()
                .put("kind", message.kind.name)
                .put("state", message.state.name)
                .put("title", message.title)
                .put("detail", message.detail)
                .put("localPath", message.localPath ?: "")
                .put("fileName", message.fileName ?: "")
                .apply { message.progress?.let { put("progress", it.coerceIn(0f, 1f)) } }

            is ImageSearchResultMessage -> JSONObject()
                .put("source", message.source)
                .put("resultCount", message.resultCount.coerceAtLeast(0))

            is DownloadResultMessage -> JSONObject()
                .put("title", message.title)
                .put("detail", message.detail)
                .put("progress", message.progress.coerceIn(0f, 1f))

            is ToolResultMessage -> JSONObject()
                .put("toolName", message.toolName)
                .put("title", message.title)
                .put("subtitle", message.subtitle)
                .put("iconText", message.iconText)
                .put("actions", encodeActions(message.actions))

            is BrowserResultMessage -> JSONObject()
                .put("browser", NightBrowserSpecCodec.encode(message.spec))
                .put("sourceLabel", message.sourceLabel)

            is MangaResultMessage,
            is ChoiceResultMessage,
            is ExtensionResultMessage -> JSONObject()
        }.toString()

    fun previewText(message: RichResultMessage): String =
        when (message) {
            is ButtonResultMessage -> message.title
            is FileResultMessage -> message.name
            is AnimeResultMessage -> message.title
            is MangaResultMessage -> message.title
            is ChoiceResultMessage -> message.title
            is LinkPreviewMessage -> message.title
            is LyricsResultMessage -> message.title
            is GeneratedImageResultMessage -> message.title
            is CreationResultMessage -> message.title
            is ImageSearchResultMessage -> message.source + " images"
            is DownloadResultMessage -> message.title
            is ToolResultMessage -> message.title
            is BrowserResultMessage -> message.spec.title
            is ExtensionResultMessage -> message.snapshot.title
        }.ifBlank { "Rich message" }

    private fun encodeActions(actions: List<MessageAction>): JSONArray =
        JSONArray().apply {
            actions.take(6).forEach { action ->
                put(
                    JSONObject()
                        .put("id", action.id)
                        .put("label", action.label)
                )
            }
        }

    private fun decodeActions(array: JSONArray?): List<MessageAction> =
        buildList {
            if (array != null) {
                for (index in 0 until minOf(array.length(), 6)) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val label = item.optString("label").trim()
                    if (id.isNotBlank() && label.isNotBlank()) {
                        add(MessageAction(id, label))
                    }
                }
            }
        }
}
