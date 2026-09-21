package com.example.whatsapp.presentation.chatscreen

import com.example.whatsapp.extensions.messages.ExtensionMessageCodec
import org.json.JSONArray
import org.json.JSONObject

object NightMessageBlockCodec {
    const val SCHEMA_VERSION = 1
    private const val MAX_BLOCKS = 24
    private const val MAX_TABLE_ROWS = 40
    private const val MAX_OPTIONS = 24
    private const val MAX_SOURCES = 20
    private const val MAX_ACTIONS = 6

    fun encode(blocks: List<NightMessageBlock>): String =
        JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put(
                "blocks",
                JSONArray().apply {
                    blocks.take(MAX_BLOCKS).forEach { block ->
                        encodeBlock(block)?.let(::put)
                    }
                }
            )
            .toString()

    fun decode(raw: String): List<NightMessageBlock> =
        runCatching {
            val json = JSONObject(raw)
            val array = json.optJSONArray("blocks") ?: return@runCatching emptyList()
            buildList {
                for (index in 0 until minOf(array.length(), MAX_BLOCKS)) {
                    decodeBlock(array.optJSONObject(index))?.let(::add)
                }
            }
        }.getOrElse { emptyList() }

    fun previewText(blocks: List<NightMessageBlock>): String {
        val first = blocks.firstOrNull() ?: return "Structured message"
        return when (first) {
            is NightTextBlock -> first.title.ifBlank { first.text.take(100) }
            is NightCodeBlock -> first.title.ifBlank { "Code" }
            is NightCopyBlock -> first.title.ifBlank { first.label }
            is NightTableBlock -> first.title.ifBlank { "Table" }
            is NightProgressBlock -> first.title
            is NightToolBlock -> first.title
            is NightErrorBlock -> first.title
            is NightSourcesBlock -> first.title
            is NightConfirmationBlock -> first.title
            is NightPermissionBlock -> first.title
            is NightQuestionBlock -> first.title
            is NightDiffBlock -> first.title
            is NightConnectionBlock -> first.title
            is NightExtensionBlock -> first.snapshot.title
        }.ifBlank { "Structured message" }
    }

    private fun encodeBlock(block: NightMessageBlock): JSONObject? =
        when (block) {
            is NightTextBlock -> base("text", block.blockId)
                .put("text", block.text)
                .put("title", block.title)

            is NightCodeBlock -> base("code", block.blockId)
                .put("code", block.code)
                .put("language", block.language)
                .put("title", block.title)

            is NightCopyBlock -> base("copy", block.blockId)
                .put("text", block.text)
                .put("label", block.label)
                .put("title", block.title)

            is NightTableBlock -> base("table", block.blockId)
                .put("title", block.title)
                .put("columns", JSONArray(block.columns))
                .put(
                    "rows",
                    JSONArray().apply {
                        block.rows.take(MAX_TABLE_ROWS).forEach { row ->
                            put(JSONArray(row))
                        }
                    }
                )

            is NightProgressBlock -> base("progress", block.blockId)
                .put("title", block.title)
                .put("detail", block.detail)
                .put("state", block.state.name)
                .put("primaryActionId", block.primaryActionId ?: "")
                .put("primaryActionLabel", block.primaryActionLabel ?: "")
                .apply { block.progress?.let { put("progress", it.coerceIn(0f, 1f)) } }

            is NightToolBlock -> base("tool", block.blockId)
                .put("toolName", block.toolName)
                .put("title", block.title)
                .put("subtitle", block.subtitle)
                .put("iconText", block.iconText)
                .put("actions", encodeActions(block.actions))

            is NightErrorBlock -> base("error", block.blockId)
                .put("title", block.title)
                .put("detail", block.detail)
                .put("retryActionId", block.retryActionId ?: "")

            is NightSourcesBlock -> base("sources", block.blockId)
                .put("title", block.title)
                .put(
                    "sources",
                    JSONArray().apply {
                        block.sources.take(MAX_SOURCES).forEach { source ->
                            put(
                                JSONObject()
                                    .put("id", source.id)
                                    .put("title", source.title)
                                    .put("subtitle", source.subtitle)
                                    .put("url", source.url)
                            )
                        }
                    }
                )

            is NightConfirmationBlock -> base("confirmation", block.blockId)
                .put("title", block.title)
                .put("detail", block.detail)
                .put("destructive", block.destructive)
                .put("confirmAction", encodeAction(block.confirmAction))
                .apply {
                    block.cancelAction?.let { put("cancelAction", encodeAction(it)) }
                }

            is NightPermissionBlock -> base("permission", block.blockId)
                .put("title", block.title)
                .put("detail", block.detail)
                .put("allowActionId", block.allowActionId)
                .put(
                    "destructiveConfirmationRequired",
                    block.destructiveConfirmationRequired
                )
                .put(
                    "options",
                    JSONArray().apply {
                        block.options.take(MAX_OPTIONS).forEach { option ->
                            put(
                                JSONObject()
                                    .put("id", option.id)
                                    .put("label", option.label)
                                    .put("description", option.description)
                                    .put("selected", option.selected)
                            )
                        }
                    }
                )

            is NightQuestionBlock -> base("question", block.blockId)
                .put("title", block.title)
                .put("detail", block.detail)
                .put("multiple", block.multiple)
                .put("allowCustom", block.allowCustom)
                .put(
                    "options",
                    JSONArray().apply {
                        block.options.take(MAX_OPTIONS).forEach { option ->
                            put(
                                JSONObject()
                                    .put("id", option.id)
                                    .put("label", option.label)
                                    .put("selected", option.selected)
                                    .put("previewPath", option.previewPath ?: "")
                            )
                        }
                    }
                )

            is NightDiffBlock -> base("diff", block.blockId)
                .put("title", block.title)
                .put("before", block.before)
                .put("after", block.after)

            is NightConnectionBlock -> base("connection", block.blockId)
                .put("service", block.service)
                .put("title", block.title)
                .put("detail", block.detail)
                .put("connected", block.connected)
                .apply { block.action?.let { put("action", encodeAction(it)) } }

            is NightExtensionBlock -> base("extension", block.blockId)
                .put("extensionAvailable", block.extensionAvailable)
                .put(
                    "snapshot",
                    runCatching {
                        JSONObject(ExtensionMessageCodec.encode(block.snapshot))
                    }.getOrElse { JSONObject() }
                )
        }

    private fun decodeBlock(json: JSONObject?): NightMessageBlock? {
        json ?: return null
        val blockId = json.optString("blockId").trim()
        if (blockId.isBlank()) return null

        return when (json.optString("type")) {
            "text" -> NightTextBlock(
                blockId = blockId,
                text = json.optString("text"),
                title = json.optString("title"),
            )

            "code" -> NightCodeBlock(
                blockId = blockId,
                code = json.optString("code"),
                language = json.optString("language"),
                title = json.optString("title"),
            )

            "copy" -> NightCopyBlock(
                blockId = blockId,
                text = json.optString("text"),
                label = json.optString("label").ifBlank { "Copy" },
                title = json.optString("title"),
            )

            "table" -> NightTableBlock(
                blockId = blockId,
                title = json.optString("title"),
                columns = strings(json.optJSONArray("columns")),
                rows = buildList {
                    val rows = json.optJSONArray("rows")
                    if (rows != null) {
                        for (index in 0 until minOf(rows.length(), MAX_TABLE_ROWS)) {
                            add(strings(rows.optJSONArray(index)))
                        }
                    }
                },
            )

            "progress" -> NightProgressBlock(
                blockId = blockId,
                title = json.optString("title"),
                detail = json.optString("detail"),
                progress = json
                    .takeIf { it.has("progress") }
                    ?.optDouble("progress")
                    ?.toFloat()
                    ?.coerceIn(0f, 1f),
                state = runCatching {
                    NightProgressState.valueOf(json.optString("state"))
                }.getOrDefault(NightProgressState.Downloading),
                primaryActionId = json.optString("primaryActionId")
                    .takeIf { it.isNotBlank() },
                primaryActionLabel = json.optString("primaryActionLabel")
                    .takeIf { it.isNotBlank() },
            )

            "tool" -> NightToolBlock(
                blockId = blockId,
                toolName = json.optString("toolName"),
                title = json.optString("title"),
                subtitle = json.optString("subtitle"),
                iconText = json.optString("iconText"),
                actions = decodeActions(json.optJSONArray("actions")),
            )

            "error" -> NightErrorBlock(
                blockId = blockId,
                title = json.optString("title"),
                detail = json.optString("detail"),
                retryActionId = json.optString("retryActionId")
                    .takeIf { it.isNotBlank() },
            )

            "sources" -> NightSourcesBlock(
                blockId = blockId,
                title = json.optString("title").ifBlank { "Sources" },
                sources = buildList {
                    val array = json.optJSONArray("sources")
                    if (array != null) {
                        for (index in 0 until minOf(array.length(), MAX_SOURCES)) {
                            val item = array.optJSONObject(index) ?: continue
                            val id = item.optString("id").trim()
                            val title = item.optString("title").trim()
                            if (id.isNotBlank() && title.isNotBlank()) {
                                add(
                                    NightSourceItem(
                                        id = id,
                                        title = title,
                                        subtitle = item.optString("subtitle"),
                                        url = item.optString("url"),
                                    )
                                )
                            }
                        }
                    }
                },
            )

            "confirmation" -> {
                val confirm = decodeAction(json.optJSONObject("confirmAction"))
                    ?: return null
                NightConfirmationBlock(
                    blockId = blockId,
                    title = json.optString("title"),
                    detail = json.optString("detail"),
                    confirmAction = confirm,
                    cancelAction = decodeAction(json.optJSONObject("cancelAction")),
                    destructive = json.optBoolean("destructive", false),
                )
            }

            "permission" -> NightPermissionBlock(
                blockId = blockId,
                title = json.optString("title"),
                detail = json.optString("detail"),
                options = buildList {
                    val array = json.optJSONArray("options")
                    if (array != null) {
                        for (index in 0 until minOf(array.length(), MAX_OPTIONS)) {
                            val item = array.optJSONObject(index) ?: continue
                            val id = item.optString("id").trim()
                            val label = item.optString("label").trim()
                            if (id.isNotBlank() && label.isNotBlank()) {
                                add(
                                    NightPermissionOption(
                                        id = id,
                                        label = label,
                                        description = item.optString("description"),
                                        selected = item.optBoolean("selected", false),
                                    )
                                )
                            }
                        }
                    }
                },
                allowActionId = json.optString("allowActionId")
                    .ifBlank { "allow" },
                destructiveConfirmationRequired =
                    json.optBoolean("destructiveConfirmationRequired", false),
            )

            "question" -> NightQuestionBlock(
                blockId = blockId,
                title = json.optString("title"),
                detail = json.optString("detail"),
                options = buildList {
                    val array = json.optJSONArray("options")
                    if (array != null) {
                        for (index in 0 until minOf(array.length(), MAX_OPTIONS)) {
                            val item = array.optJSONObject(index) ?: continue
                            val id = item.optString("id").trim()
                            val label = item.optString("label").trim()
                            if (id.isNotBlank() && label.isNotBlank()) {
                                add(
                                    NightQuestionOption(
                                        id = id,
                                        label = label,
                                        selected = item.optBoolean("selected", false),
                                        previewPath = item.optString("previewPath")
                                            .takeIf { it.isNotBlank() },
                                    )
                                )
                            }
                        }
                    }
                },
                multiple = json.optBoolean("multiple", false),
                allowCustom = json.optBoolean("allowCustom", true),
            )

            "diff" -> NightDiffBlock(
                blockId = blockId,
                title = json.optString("title").ifBlank { "Changes" },
                before = json.optString("before"),
                after = json.optString("after"),
            )

            "connection" -> NightConnectionBlock(
                blockId = blockId,
                service = json.optString("service"),
                title = json.optString("title"),
                detail = json.optString("detail"),
                connected = json.optBoolean("connected", false),
                action = decodeAction(json.optJSONObject("action")),
            )

            "extension" -> {
                val snapshotJson = json.optJSONObject("snapshot") ?: return null
                val snapshot = ExtensionMessageCodec.decode(snapshotJson.toString())
                    ?: return null
                NightExtensionBlock(
                    blockId = blockId,
                    snapshot = snapshot,
                    extensionAvailable = json.optBoolean(
                        "extensionAvailable",
                        true,
                    ),
                )
            }

            else -> null
        }
    }

    private fun base(type: String, blockId: String): JSONObject =
        JSONObject()
            .put("type", type)
            .put("blockId", blockId)

    private fun encodeActions(actions: List<NightBlockAction>): JSONArray =
        JSONArray().apply {
            actions.take(MAX_ACTIONS).forEach { put(encodeAction(it)) }
        }

    private fun encodeAction(action: NightBlockAction): JSONObject =
        JSONObject()
            .put("id", action.id)
            .put("label", action.label)
            .put("style", action.style.name)
            .put("enabled", action.enabled)

    private fun decodeActions(array: JSONArray?): List<NightBlockAction> =
        buildList {
            if (array != null) {
                for (index in 0 until minOf(array.length(), MAX_ACTIONS)) {
                    decodeAction(array.optJSONObject(index))?.let(::add)
                }
            }
        }

    private fun decodeAction(json: JSONObject?): NightBlockAction? {
        json ?: return null
        val id = json.optString("id").trim()
        val label = json.optString("label").trim()
        if (id.isBlank() || label.isBlank()) return null

        return NightBlockAction(
            id = id,
            label = label,
            style = runCatching {
                NightBlockActionStyle.valueOf(json.optString("style"))
            }.getOrDefault(NightBlockActionStyle.Secondary),
            enabled = json.optBoolean("enabled", true),
        )
    }

    private fun strings(array: JSONArray?): List<String> =
        buildList {
            if (array != null) {
                for (index in 0 until array.length()) {
                    add(array.optString(index))
                }
            }
        }
}
