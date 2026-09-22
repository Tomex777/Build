package com.example.whatsapp.extensions.runtime

import org.json.JSONObject

/**
 * Minimal common manifest envelope for anything Night presents as an integration.
 *
 * Runtime-specific configuration stays in the extension/MCP adapters. The `type`
 * marker is intentionally the only field the shared shell needs in order to route
 * an integration to the correct adapter.
 */
data class NightIntegrationManifest(
    val id: String,
    val displayName: String,
    val kind: NightIntegrationKind,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("name", displayName)
            .put("type", kind.wireName)

    companion object {
        fun fromJson(
            json: JSONObject,
            legacyExtensionId: String? = null,
            legacyExtensionName: String? = null,
        ): NightIntegrationManifest {
            val kind = NightIntegrationKind.fromWireName(json.optString("type"))
            val id = json.optString("id")
                .trim()
                .ifBlank {
                    json.optString("extensionId")
                        .trim()
                        .ifBlank { legacyExtensionId.orEmpty().trim() }
                }
            require(id.isNotBlank()) { "Night integration id is required." }

            val displayName = json.optString("name")
                .trim()
                .ifBlank {
                    json.optString("extensionName")
                        .trim()
                        .ifBlank {
                            legacyExtensionName.orEmpty().trim().ifBlank { id }
                        }
                }

            return NightIntegrationManifest(
                id = id,
                displayName = displayName,
                kind = kind,
            )
        }
    }
}
