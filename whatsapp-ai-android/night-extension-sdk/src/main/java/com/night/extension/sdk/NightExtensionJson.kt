package com.night.extension.sdk

import org.json.JSONArray
import org.json.JSONObject

data class NightToolDescriptor(
    val name: String,
    val description: String,
    val parameters: JSONObject =
        JSONObject()
            .put("type", "object")
            .put("properties", JSONObject())
            .put("additionalProperties", false),
    val readOnly: Boolean = false,
)

data class NightMessageTypeDescriptor(
    val messageType: String,
    val template: String,
    val description: String,
    val whenToUse: String = "",
)

fun nightExtensionDescriptor(
    extensionId: String,
    name: String,
    capabilities: Collection<String>,
    tags: Collection<String> = emptyList(),
    tools: Collection<NightToolDescriptor> = emptyList(),
    messageTypes: Collection<NightMessageTypeDescriptor> = emptyList(),
): JSONObject =
    JSONObject()
        .put("schemaVersion", NightExtensionProtocol.SUPPORTED_SCHEMA_VERSION)
        .put("extensionId", extensionId)
        .put("name", name)
        .put("type", "extension")
        .put("capabilities", JSONArray(capabilities.toList()))
        .put("tags", JSONArray(tags.toList()))
        .put(
            "tools",
            JSONArray().apply {
                tools.forEach { tool ->
                    put(
                        JSONObject()
                            .put("name", tool.name)
                            .put("description", tool.description)
                            .put("parameters", tool.parameters)
                            .put("readOnly", tool.readOnly)
                    )
                }
            }
        )
        .put(
            "messageTypes",
            JSONArray().apply {
                messageTypes.forEach { type ->
                    put(
                        JSONObject()
                            .put("messageType", type.messageType)
                            .put("template", type.template)
                            .put("description", type.description)
                            .put("whenToUse", type.whenToUse)
                    )
                }
            }
        )

fun nightAction(
    id: String,
    label: String,
    style: String = "secondary",
    requiresExtension: Boolean = true,
): JSONObject =
    JSONObject()
        .put("id", id)
        .put("label", label)
        .put("style", style)
        .put("requiresExtension", requiresExtension)

fun nightMetadata(
    label: String,
    value: String,
): JSONObject =
    JSONObject()
        .put("label", label)
        .put("value", value)

fun nightRow(
    title: String,
    subtitle: String = "",
    value: String = "",
    iconText: String = "",
): JSONObject =
    JSONObject()
        .put("title", title)
        .put("subtitle", subtitle)
        .put("value", value)
        .put("iconText", iconText)

fun nightExtensionMessage(
    extensionId: String,
    messageType: String,
    template: String,
    extensionName: String,
    title: String,
    subtitle: String = "",
    body: String = "",
    artworkPath: String? = null,
    badge: String = "",
    status: String = "",
    metadata: Collection<JSONObject> = emptyList(),
    rows: Collection<JSONObject> = emptyList(),
    actions: Collection<JSONObject> = emptyList(),
    extensionPayload: JSONObject = JSONObject(),
    configuration: JSONObject? = null,
    browser: JSONObject? = null,
): JSONObject =
    JSONObject()
        .put("schemaVersion", 1)
        .put("extensionId", extensionId)
        .put("messageType", messageType)
        .put("template", template)
        .put("extensionName", extensionName)
        .put("title", title)
        .put("subtitle", subtitle)
        .put("body", body)
        .put("iconText", "")
        .put("artworkPath", artworkPath ?: "")
        .put("badge", badge)
        .put("status", status)
        .put("metadata", JSONArray(metadata.toList()))
        .put("rows", JSONArray(rows.toList()))
        .put("actions", JSONArray(actions.toList()))
        .put("extensionPayload", extensionPayload)
        .apply {
            configuration?.let { put("configuration", it) }
            browser?.let { put("browser", it) }
        }

fun nightBrowserSpec(
    sessionId: String,
    initialUrl: String,
    allowedHosts: Collection<String>,
    title: String,
    verifyActionId: String,
    verifyLabel: String = "Verify",
    userAgent: String? = null,
): JSONObject =
    JSONObject()
        .put("schemaVersion", 1)
        .put("sessionId", sessionId)
        .put("initialUrl", initialUrl)
        .put("allowedHosts", JSONArray(allowedHosts.toList()))
        .put("restrictedToAllowedHosts", true)
        .put("title", title)
        .put("verifyActionId", verifyActionId)
        .put("verifyLabel", verifyLabel)
        .put("javaScriptEnabled", true)
        .put("thirdPartyCookies", true)
        .put("userAgent", userAgent ?: "")
        .put("verificationState", "idle")
        .put("verificationMessage", "")
        .put("verifiedAt", 0L)

fun nightConfiguration(
    id: String,
    sections: Collection<JSONObject>,
    fields: Collection<JSONObject>,
    submitActionId: String = "save_config",
    submitLabel: String = "Save",
    advancedLabel: String = "Advanced",
): JSONObject =
    JSONObject()
        .put("id", id)
        .put("sections", JSONArray(sections.toList()))
        .put("fields", JSONArray(fields.toList()))
        .put("submitActionId", submitActionId)
        .put("submitLabel", submitLabel)
        .put("advancedLabel", advancedLabel)

fun nightConfigurationSection(
    id: String,
    title: String,
    description: String = "",
): JSONObject =
    JSONObject()
        .put("id", id)
        .put("title", title)
        .put("description", description)

fun nightConfigurationField(
    id: String,
    label: String,
    type: String,
    value: String = "",
    description: String = "",
    sectionId: String = "",
    placeholder: String = "",
    suffix: String = "",
    required: Boolean = false,
    advanced: Boolean = false,
    options: Collection<JSONObject> = emptyList(),
    min: Double? = null,
    max: Double? = null,
    step: Double? = null,
): JSONObject =
    JSONObject()
        .put("id", id)
        .put("label", label)
        .put("type", type)
        .put("value", value)
        .put("description", description)
        .put("sectionId", sectionId)
        .put("placeholder", placeholder)
        .put("suffix", suffix)
        .put("required", required)
        .put("advanced", advanced)
        .put("values", JSONArray())
        .put("options", JSONArray(options.toList()))
        .apply {
            min?.let { put("min", it) }
            max?.let { put("max", it) }
            step?.let { put("step", it) }
        }

fun nightConfigurationOption(
    id: String,
    label: String,
    description: String = "",
): JSONObject =
    JSONObject()
        .put("id", id)
        .put("label", label)
        .put("description", description)
