package com.example.whatsapp.extensions.messages

import org.json.JSONArray
import org.json.JSONObject

object NightExtensionMessageApi {
    const val SUPPORTED_SCHEMA_VERSION = 1
    const val MAX_METADATA = 6
    const val MAX_ROWS = 8
    const val MAX_ACTIONS = 3
}

enum class ExtensionCardTemplate(val wireName: String) {
    Content("content_card"),
    Media("media_card"),
    File("file_card"),
    List("list_card"),
    Choice("choice_card"),
    Progress("progress_card"),
    Status("status_card"),
    Entity("entity_card"),
    Gallery("gallery_card"),
    CustomData("custom_data_card");

    companion object {
        fun fromWireName(value: String): ExtensionCardTemplate =
            entries.firstOrNull { it.wireName == value } ?: Content
    }
}

enum class ExtensionActionStyle(val wireName: String) {
    Primary("primary"),
    Secondary("secondary"),
    Destructive("destructive");

    companion object {
        fun fromWireName(value: String): ExtensionActionStyle =
            entries.firstOrNull { it.wireName == value } ?: Secondary
    }
}

data class ExtensionCardMetadata(
    val label: String,
    val value: String = "",
)

data class ExtensionCardRow(
    val title: String,
    val subtitle: String = "",
    val value: String = "",
    val iconText: String = "",
)

data class ExtensionCardAction(
    val id: String,
    val label: String,
    val style: ExtensionActionStyle = ExtensionActionStyle.Secondary,
    val requiresExtension: Boolean = true,
)

data class ExtensionMessageSnapshot(
    val schemaVersion: Int = NightExtensionMessageApi.SUPPORTED_SCHEMA_VERSION,
    val extensionId: String,
    val messageType: String,
    val template: ExtensionCardTemplate,
    val extensionName: String,
    val title: String,
    val subtitle: String = "",
    val body: String = "",
    val iconText: String = "",
    val artworkPath: String? = null,
    val badge: String = "",
    val status: String = "",
    val progress: Float? = null,
    val metadata: List<ExtensionCardMetadata> = emptyList(),
    val rows: List<ExtensionCardRow> = emptyList(),
    val actions: List<ExtensionCardAction> = emptyList(),
    val extensionPayloadJson: String = "{}",
) {
    fun hasValidNamespace(): Boolean =
        extensionId.isNotBlank() &&
            messageType.startsWith(extensionId + ".") &&
            messageType.length > extensionId.length + 1
}

object ExtensionMessageCodec {
    fun encode(snapshot: ExtensionMessageSnapshot): String {
        val json = JSONObject()
            .put("schemaVersion", snapshot.schemaVersion)
            .put("extensionId", snapshot.extensionId)
            .put("messageType", snapshot.messageType)
            .put("template", snapshot.template.wireName)
            .put("extensionName", snapshot.extensionName)
            .put("title", snapshot.title)
            .put("subtitle", snapshot.subtitle)
            .put("body", snapshot.body)
            .put("iconText", snapshot.iconText)
            .put("artworkPath", snapshot.artworkPath ?: "")
            .put("badge", snapshot.badge)
            .put("status", snapshot.status)

        snapshot.progress?.let { json.put("progress", it.coerceIn(0f, 1f)) }

        json.put(
            "metadata",
            JSONArray().apply {
                snapshot.metadata
                    .take(NightExtensionMessageApi.MAX_METADATA)
                    .forEach { item ->
                        put(
                            JSONObject()
                                .put("label", item.label)
                                .put("value", item.value)
                        )
                    }
            }
        )

        json.put(
            "rows",
            JSONArray().apply {
                snapshot.rows
                    .take(NightExtensionMessageApi.MAX_ROWS)
                    .forEach { row ->
                        put(
                            JSONObject()
                                .put("title", row.title)
                                .put("subtitle", row.subtitle)
                                .put("value", row.value)
                                .put("iconText", row.iconText)
                        )
                    }
            }
        )

        json.put(
            "actions",
            JSONArray().apply {
                snapshot.actions
                    .take(NightExtensionMessageApi.MAX_ACTIONS)
                    .forEach { action ->
                        put(
                            JSONObject()
                                .put("id", action.id)
                                .put("label", action.label)
                                .put("style", action.style.wireName)
                                .put("requiresExtension", action.requiresExtension)
                        )
                    }
            }
        )

        json.put(
            "extensionPayload",
            runCatching { JSONObject(snapshot.extensionPayloadJson) }
                .getOrElse { JSONObject() }
        )

        return json.toString()
    }

    fun decode(raw: String): ExtensionMessageSnapshot? = runCatching {
        val json = JSONObject(raw)
        val extensionId = json.optString("extensionId").trim()
        val messageType = json.optString("messageType").trim()
        val title = json.optString("title").trim()
        if (extensionId.isBlank() || messageType.isBlank() || title.isBlank()) {
            return@runCatching null
        }

        val metadata = mutableListOf<ExtensionCardMetadata>()
        val metadataJson = json.optJSONArray("metadata")
        if (metadataJson != null) {
            for (index in 0 until minOf(metadataJson.length(), NightExtensionMessageApi.MAX_METADATA)) {
                val item = metadataJson.optJSONObject(index) ?: continue
                val label = item.optString("label").trim()
                if (label.isNotBlank()) {
                    metadata += ExtensionCardMetadata(
                        label = label,
                        value = item.optString("value").trim(),
                    )
                }
            }
        }

        val rows = mutableListOf<ExtensionCardRow>()
        val rowsJson = json.optJSONArray("rows")
        if (rowsJson != null) {
            for (index in 0 until minOf(rowsJson.length(), NightExtensionMessageApi.MAX_ROWS)) {
                val item = rowsJson.optJSONObject(index) ?: continue
                val rowTitle = item.optString("title").trim()
                if (rowTitle.isNotBlank()) {
                    rows += ExtensionCardRow(
                        title = rowTitle,
                        subtitle = item.optString("subtitle").trim(),
                        value = item.optString("value").trim(),
                        iconText = item.optString("iconText").trim(),
                    )
                }
            }
        }

        val actions = mutableListOf<ExtensionCardAction>()
        val actionsJson = json.optJSONArray("actions")
        if (actionsJson != null) {
            for (index in 0 until minOf(actionsJson.length(), NightExtensionMessageApi.MAX_ACTIONS)) {
                val item = actionsJson.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val label = item.optString("label").trim()
                if (id.isNotBlank() && label.isNotBlank()) {
                    actions += ExtensionCardAction(
                        id = id,
                        label = label,
                        style = ExtensionActionStyle.fromWireName(item.optString("style")),
                        requiresExtension = item.optBoolean("requiresExtension", true),
                    )
                }
            }
        }

        ExtensionMessageSnapshot(
            schemaVersion = json.optInt("schemaVersion", 1).coerceAtLeast(1),
            extensionId = extensionId,
            messageType = messageType,
            template = ExtensionCardTemplate.fromWireName(json.optString("template")),
            extensionName = json.optString("extensionName").trim().ifBlank { extensionId },
            title = title,
            subtitle = json.optString("subtitle").trim(),
            body = json.optString("body").trim(),
            iconText = json.optString("iconText").trim().take(2),
            artworkPath = json.optString("artworkPath").trim().takeIf { it.isNotBlank() },
            badge = json.optString("badge").trim(),
            status = json.optString("status").trim(),
            progress = if (json.has("progress")) {
                json.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f)
            } else {
                null
            },
            metadata = metadata,
            rows = rows,
            actions = actions,
            extensionPayloadJson = json.optJSONObject("extensionPayload")?.toString() ?: "{}",
        )
    }.getOrNull()
}
