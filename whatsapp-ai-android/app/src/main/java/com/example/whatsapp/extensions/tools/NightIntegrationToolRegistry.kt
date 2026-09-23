package com.example.whatsapp.extensions.tools

import com.example.whatsapp.extensions.runtime.NightIntegrationKind
import org.json.JSONArray
import org.json.JSONObject

data class NightIntegrationToolMetadata(
    val qualifiedName: String,
    val integrationId: String,
    val kind: NightIntegrationKind,
    val readOnly: Boolean,
)

/**
 * Shared model-facing facade for Night extension and MCP tools.
 *
 * The runtimes remain separate, but discovery, side-effect classification and
 * execution routing now go through one integration contract.
 */
object NightIntegrationToolRegistry {
    fun metadata(qualifiedName: String): NightIntegrationToolMetadata? {
        NightExtensionToolRegistry.definition(qualifiedName)?.let { definition ->
            return NightIntegrationToolMetadata(
                qualifiedName = qualifiedName,
                integrationId = definition.extensionId,
                kind = NightIntegrationKind.EXTENSION,
                readOnly = definition.readOnly,
            )
        }

        NightMcpToolRegistry.definition(qualifiedName)?.let { definition ->
            return NightIntegrationToolMetadata(
                qualifiedName = qualifiedName,
                integrationId = definition.serverId,
                kind = NightIntegrationKind.MCP,
                readOnly = definition.readOnly,
            )
        }

        return null
    }

    fun isSideEffect(qualifiedName: String): Boolean =
        metadata(qualifiedName)?.readOnly == false

    fun schemas(): JSONArray =
        JSONArray().apply {
            appendSchemas(NightExtensionToolRegistry.schemas())
            appendSchemas(NightMcpToolRegistry.schemas())
        }

    /** Describes integrations actually registered and available to this request. */
    fun promptSummary(): String {
        val registeredSchemas = schemas()
        if (registeredSchemas.length() == 0) return ""

        return buildString {
            append("Enabled Night integrations available in this conversation:\n")
            for (index in 0 until registeredSchemas.length()) {
                val function = registeredSchemas.optJSONObject(index)
                    ?.optJSONObject("function") ?: continue
                val name = function.optString("name").trim()
                val description = function.optString("description").trim()
                if (name.isBlank() || description.isBlank()) continue

                val integration = metadata(name)
                append("- [")
                append(integration?.kind?.wireName ?: "integration")
                append(" ")
                append(integration?.integrationId ?: "")
                append("] ")
                append(name)
                append(": ")
                append(description)
                append("\n")
            }
            append(
                "When the user refers to an extension, match their request to these installed " +
                    "integration tools and call the relevant tool. Do not claim an integration is " +
                    "missing or disabled when its tool is listed here."
            )
        }.trim()
    }

    suspend fun execute(
        qualifiedName: String,
        chatId: String,
        arguments: JSONObject,
    ): JSONObject? =
        when (metadata(qualifiedName)?.kind) {
            NightIntegrationKind.EXTENSION ->
                NightExtensionToolRegistry.execute(
                    qualifiedName = qualifiedName,
                    chatId = chatId,
                    arguments = arguments,
                )

            NightIntegrationKind.MCP ->
                NightMcpToolRegistry.execute(
                    qualifiedName = qualifiedName,
                    chatId = chatId,
                    arguments = arguments,
                )

            null -> null
        }

    private fun JSONArray.appendSchemas(source: JSONArray) {
        for (index in 0 until source.length()) {
            put(source.getJSONObject(index))
        }
    }
}
