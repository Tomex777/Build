package com.example.whatsapp.extensions.messages

import java.nio.charset.StandardCharsets
import java.util.Base64
import org.json.JSONObject

data class ExtensionConfigurationSubmission(
    val configurationId: String,
    val actionId: String,
    val valuesJson: String,
)

object ExtensionConfigurationActionCodec {
    private const val PREFIX = "extension_config:"

    fun encode(
        configurationId: String,
        actionId: String,
        values: JSONObject,
    ): String {
        val payload = JSONObject()
            .put("configurationId", configurationId)
            .put("actionId", actionId)
            .put("values", values)
            .toString()

        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.toByteArray(StandardCharsets.UTF_8))

        return PREFIX + encoded
    }

    fun decode(value: String): ExtensionConfigurationSubmission? {
        if (!value.startsWith(PREFIX)) return null

        return runCatching {
            val raw = value.removePrefix(PREFIX)
            val json = JSONObject(
                String(
                    Base64.getUrlDecoder().decode(raw),
                    StandardCharsets.UTF_8,
                )
            )
            val configurationId = json.optString("configurationId").trim()
            val actionId = json.optString("actionId").trim()
            val values = json.optJSONObject("values") ?: JSONObject()

            if (configurationId.isBlank() || actionId.isBlank()) {
                null
            } else {
                ExtensionConfigurationSubmission(
                    configurationId = configurationId,
                    actionId = actionId,
                    valuesJson = values.toString(),
                )
            }
        }.getOrNull()
    }
}

fun ExtensionMessageSnapshot.withConfigurationValues(
    values: JSONObject,
): ExtensionMessageSnapshot {
    val current = configuration ?: return this

    val updatedFields = current.fields.map { field ->
        if (!values.has(field.id)) return@map field

        when (field.type) {
            ExtensionConfigurationFieldType.MultiChoice -> {
                val array = values.optJSONArray(field.id)
                field.copy(
                    values = buildList {
                        if (array != null) {
                            for (index in 0 until array.length()) {
                                val value = array.optString(index).trim()
                                if (value.isNotBlank()) add(value)
                            }
                        }
                    }
                )
            }

            else -> field.copy(
                value = values.opt(field.id)?.toString().orEmpty(),
            )
        }
    }

    return copy(
        configuration = current.copy(fields = updatedFields),
    )
}
