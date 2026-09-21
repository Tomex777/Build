package com.example.whatsapp.extensions.messages

import org.json.JSONArray
import org.json.JSONObject

object NightExtensionMessageApi {
    const val SUPPORTED_SCHEMA_VERSION = 1
    const val MAX_METADATA = 6
    const val MAX_ROWS = 8
    const val MAX_ACTIONS = 3
    const val MAX_CONFIGURATION_FIELDS = 24
    const val MAX_CONFIGURATION_OPTIONS = 16
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
    CustomData("custom_data_card"),
    Configuration("configuration_card"),
    Browser("browser_card");

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


enum class ExtensionConfigurationFieldType(val wireName: String) {
    Toggle("toggle"),
    SingleChoice("single_choice"),
    MultiChoice("multi_choice"),
    Number("number"),
    Range("range"),
    Text("text"),
    Action("action");

    companion object {
        fun fromWireName(value: String): ExtensionConfigurationFieldType =
            entries.firstOrNull { it.wireName == value } ?: Text
    }
}

data class ExtensionConfigurationOption(
    val id: String,
    val label: String,
    val description: String = "",
)

data class ExtensionConfigurationField(
    val id: String,
    val label: String,
    val type: ExtensionConfigurationFieldType,
    val description: String = "",
    val value: String = "",
    val values: List<String> = emptyList(),
    val options: List<ExtensionConfigurationOption> = emptyList(),
    val placeholder: String = "",
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val advanced: Boolean = false,
    val actionLabel: String = "",
    val taskOverride: Boolean = false,
)

data class ExtensionConfiguration(
    val id: String,
    val fields: List<ExtensionConfigurationField>,
    val submitActionId: String = "save_config",
    val submitLabel: String = "Save",
    val advancedLabel: String = "Advanced",
)


data class ExtensionBrowser(
    val sessionId: String,
    val url: String,
    val allowedHosts: List<String> = emptyList(),
    val javaScriptEnabled: Boolean = true,
    val thirdPartyCookiesEnabled: Boolean = true,
    val userAgent: String = "",
    val inlineHeightDp: Int = 320,
) {
    fun normalizedAllowedHosts(): List<String> =
        allowedHosts
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(16)

    fun isValid(): Boolean {
        if (sessionId.isBlank() || sessionId.length > 96) return false
        val parsed = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase()
        val host = parsed.host?.lowercase()
        if (scheme !in setOf("http", "https") || host.isNullOrBlank()) return false
        val allowed = normalizedAllowedHosts()
        return allowed.isEmpty() || allowed.any { candidate ->
            host == candidate || host.endsWith("." + candidate)
        }
    }
}

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
    val configuration: ExtensionConfiguration? = null,
    val browser: ExtensionBrowser? = null,
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

        snapshot.configuration?.let { configuration ->
            json.put(
                "configuration",
                JSONObject()
                    .put("id", configuration.id)
                    .put("submitActionId", configuration.submitActionId)
                    .put("submitLabel", configuration.submitLabel)
                    .put("advancedLabel", configuration.advancedLabel)
                    .put(
                        "fields",
                        JSONArray().apply {
                            configuration.fields
                                .take(NightExtensionMessageApi.MAX_CONFIGURATION_FIELDS)
                                .forEach { field ->
                                    put(
                                        JSONObject()
                                            .put("id", field.id)
                                            .put("label", field.label)
                                            .put("type", field.type.wireName)
                                            .put("description", field.description)
                                            .put("value", field.value)
                                            .put("values", JSONArray(field.values))
                                            .put("placeholder", field.placeholder)
                                            .put("advanced", field.advanced)
                                            .put("actionLabel", field.actionLabel)
                                            .put("taskOverride", field.taskOverride)
                                            .apply {
                                                field.min?.let { put("min", it) }
                                                field.max?.let { put("max", it) }
                                                field.step?.let { put("step", it) }
                                            }
                                            .put(
                                                "options",
                                                JSONArray().apply {
                                                    field.options
                                                        .take(NightExtensionMessageApi.MAX_CONFIGURATION_OPTIONS)
                                                        .forEach { option ->
                                                            put(
                                                                JSONObject()
                                                                    .put("id", option.id)
                                                                    .put("label", option.label)
                                                                    .put("description", option.description)
                                                            )
                                                        }
                                                }
                                            )
                                    )
                                }
                        }
                    )
            )
        }

        snapshot.browser?.takeIf { it.isValid() }?.let { browser ->
            json.put(
                "browser",
                JSONObject()
                    .put("sessionId", browser.sessionId.trim())
                    .put("url", browser.url.trim())
                    .put("allowedHosts", JSONArray(browser.normalizedAllowedHosts()))
                    .put("javaScriptEnabled", browser.javaScriptEnabled)
                    .put("thirdPartyCookiesEnabled", browser.thirdPartyCookiesEnabled)
                    .put("userAgent", browser.userAgent.trim().take(512))
                    .put("inlineHeightDp", browser.inlineHeightDp.coerceIn(240, 480))
            )
        }

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

        val configuration = json.optJSONObject("configuration")?.let { configurationJson ->
            val configurationId = configurationJson.optString("id").trim()
            if (configurationId.isBlank()) {
                null
            } else {
                val fields = mutableListOf<ExtensionConfigurationField>()
                val fieldsJson = configurationJson.optJSONArray("fields")
                if (fieldsJson != null) {
                    for (
                        index in 0 until minOf(
                            fieldsJson.length(),
                            NightExtensionMessageApi.MAX_CONFIGURATION_FIELDS,
                        )
                    ) {
                        val item = fieldsJson.optJSONObject(index) ?: continue
                        val fieldId = item.optString("id").trim()
                        val label = item.optString("label").trim()
                        if (fieldId.isBlank() || label.isBlank()) continue

                        val values = buildList {
                            val valuesJson = item.optJSONArray("values")
                            if (valuesJson != null) {
                                for (valueIndex in 0 until valuesJson.length()) {
                                    val value = valuesJson.optString(valueIndex).trim()
                                    if (value.isNotBlank()) add(value)
                                }
                            }
                        }

                        val options = buildList {
                            val optionsJson = item.optJSONArray("options")
                            if (optionsJson != null) {
                                for (
                                    optionIndex in 0 until minOf(
                                        optionsJson.length(),
                                        NightExtensionMessageApi.MAX_CONFIGURATION_OPTIONS,
                                    )
                                ) {
                                    val option = optionsJson.optJSONObject(optionIndex) ?: continue
                                    val optionId = option.optString("id").trim()
                                    val optionLabel = option.optString("label").trim()
                                    if (optionId.isNotBlank() && optionLabel.isNotBlank()) {
                                        add(
                                            ExtensionConfigurationOption(
                                                id = optionId,
                                                label = optionLabel,
                                                description = option.optString("description").trim(),
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        fields += ExtensionConfigurationField(
                            id = fieldId,
                            label = label,
                            type = ExtensionConfigurationFieldType.fromWireName(
                                item.optString("type")
                            ),
                            description = item.optString("description").trim(),
                            value = item.optString("value"),
                            values = values,
                            options = options,
                            placeholder = item.optString("placeholder"),
                            min = item.takeIf { it.has("min") }?.optDouble("min"),
                            max = item.takeIf { it.has("max") }?.optDouble("max"),
                            step = item.takeIf { it.has("step") }?.optDouble("step"),
                            advanced = item.optBoolean("advanced", false),
                            actionLabel = item.optString("actionLabel").trim(),
                            taskOverride = item.optBoolean("taskOverride", false),
                        )
                    }
                }

                ExtensionConfiguration(
                    id = configurationId,
                    fields = fields,
                    submitActionId = configurationJson.optString(
                        "submitActionId",
                        "save_config",
                    ).ifBlank { "save_config" },
                    submitLabel = configurationJson.optString(
                        "submitLabel",
                        "Save",
                    ).ifBlank { "Save" },
                    advancedLabel = configurationJson.optString(
                        "advancedLabel",
                        "Advanced",
                    ).ifBlank { "Advanced" },
                )
            }
        }

        val browser = json.optJSONObject("browser")?.let { browserJson ->
            val candidate = ExtensionBrowser(
                sessionId = browserJson.optString("sessionId").trim(),
                url = browserJson.optString("url").trim(),
                allowedHosts = buildList {
                    val hosts = browserJson.optJSONArray("allowedHosts")
                    if (hosts != null) {
                        for (index in 0 until minOf(hosts.length(), 16)) {
                            val host = hosts.optString(index).trim().lowercase()
                            if (host.isNotBlank()) add(host)
                        }
                    }
                },
                javaScriptEnabled = browserJson.optBoolean("javaScriptEnabled", true),
                thirdPartyCookiesEnabled =
                    browserJson.optBoolean("thirdPartyCookiesEnabled", true),
                userAgent = browserJson.optString("userAgent").trim().take(512),
                inlineHeightDp = browserJson.optInt("inlineHeightDp", 320)
                    .coerceIn(240, 480),
            )
            candidate.takeIf { it.isValid() }
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
            configuration = configuration,
            browser = browser,
        )
    }.getOrNull()
}
