package com.example.whatsapp.extensions.tools

import com.example.whatsapp.extensions.runtime.NightIntegrationCapability
import com.example.whatsapp.extensions.runtime.NightIntegrationKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightIntegrationToolRegistryTest {
    @Test
    fun extensionAndMcpShareMetadataAndSchemaFacade() {
        val extension = NightExtensionToolDefinition(
            extensionId = "anime",
            name = "search",
            description = "Search anime.",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()),
            readOnly = true,
        )
        val mcp = NightMcpToolDefinition(
            serverId = "github",
            name = "create_issue",
            description = "Create an issue.",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()),
            readOnly = false,
        )

        try {
            NightExtensionToolRegistry.register(extension) { _, _ ->
                JSONObject().put("ok", true)
            }
            NightMcpToolRegistry.register(mcp) { _, _ ->
                JSONObject().put("ok", true)
            }

            val extensionMetadata =
                NightIntegrationToolRegistry.metadata(extension.qualifiedName)
            val mcpMetadata =
                NightIntegrationToolRegistry.metadata(mcp.qualifiedName)

            assertNotNull(extensionMetadata)
            assertNotNull(mcpMetadata)
            assertEquals(
                NightIntegrationKind.EXTENSION,
                extensionMetadata?.kind,
            )
            assertEquals("anime", extensionMetadata?.integrationId)
            assertTrue(extensionMetadata?.readOnly == true)
            assertFalse(
                NightIntegrationToolRegistry.isSideEffect(
                    extension.qualifiedName,
                ),
            )

            assertEquals(
                NightIntegrationKind.MCP,
                mcpMetadata?.kind,
            )
            assertEquals("github", mcpMetadata?.integrationId)
            assertFalse(mcpMetadata?.readOnly == true)
            assertTrue(
                NightIntegrationToolRegistry.isSideEffect(
                    mcp.qualifiedName,
                ),
            )

            val schemas = NightIntegrationToolRegistry.schemas()
            val names =
                (0 until schemas.length())
                    .map {
                        schemas
                            .getJSONObject(it)
                            .getJSONObject("function")
                            .getString("name")
                    }
                    .toSet()
            assertTrue(extension.qualifiedName in names)
            assertTrue(mcp.qualifiedName in names)

            val extensionPrompt = NightExtensionToolRegistry.promptSummary()
            assertTrue(extensionPrompt.contains("anime / search"))
            assertTrue(extensionPrompt.contains(extension.qualifiedName))
            assertTrue(extensionPrompt.contains("Search anime."))
        } finally {
            NightExtensionToolRegistry.unregisterExtension("anime")
            NightMcpToolRegistry.unregisterServer("github")
        }
    }

    @Test
    fun preferredCapabilityProviderOwnsEquivalentModelTool() {
        val capability = NightIntegrationCapability.require("unreal_engine")
        val preferred =
            NightExtensionToolDefinition(
                extensionId = "unreal.primary",
                name = "search_assets",
                description = "Search Unreal assets.",
                parameters = JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject()),
                readOnly = true,
                capabilities = setOf(capability),
            )
        val fallback =
            NightExtensionToolDefinition(
                extensionId = "unreal.fallback",
                name = "search_assets",
                description = "Search Unreal assets.",
                parameters = JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject()),
                readOnly = true,
                capabilities = setOf(capability),
            )

        try {
            NightExtensionPreferenceRouter.setPreferred(
                capability,
                preferred.extensionId,
            )
            NightExtensionToolRegistry.register(preferred) { _, _ ->
                JSONObject().put("provider", "preferred")
            }
            NightExtensionToolRegistry.register(fallback) { _, _ ->
                JSONObject().put("provider", "fallback")
            }

            val schemas = NightExtensionToolRegistry.schemas()
            val names =
                (0 until schemas.length())
                    .map {
                        schemas.getJSONObject(it)
                            .getJSONObject("function")
                            .getString("name")
                    }
                    .toSet()

            assertTrue(preferred.qualifiedName in names)
            assertFalse(fallback.qualifiedName in names)
        } finally {
            NightExtensionToolRegistry.unregisterExtension(preferred.extensionId)
            NightExtensionToolRegistry.unregisterExtension(fallback.extensionId)
            NightExtensionPreferenceRouter.clearForTests()
        }
    }

    @Test
    fun preferredExtensionToolFallsBackToEquivalentProvider() {
        val capability = NightIntegrationCapability.require("unreal_engine")
        val preferred =
            NightExtensionToolDefinition(
                extensionId = "unreal.primary",
                name = "resolve_asset",
                description = "Resolve an Unreal asset.",
                parameters = JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject()),
                capabilities = setOf(capability),
            )
        val fallback =
            NightExtensionToolDefinition(
                extensionId = "unreal.fallback",
                name = "resolve_asset",
                description = "Resolve an Unreal asset.",
                parameters = JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject()),
                capabilities = setOf(capability),
            )

        try {
            NightExtensionPreferenceRouter.setPreferred(
                capability,
                preferred.extensionId,
            )
            NightExtensionToolRegistry.register(preferred) { _, _ ->
                error("primary unavailable")
            }
            NightExtensionToolRegistry.register(fallback) { _, _ ->
                JSONObject()
                    .put("ok", true)
                    .put("provider", "fallback")
            }

            val result =
                kotlinx.coroutines.runBlocking {
                    NightExtensionToolRegistry.execute(
                        qualifiedName = preferred.qualifiedName,
                        chatId = "chat",
                        arguments = JSONObject(),
                    )
                }

            assertEquals("fallback", result?.optString("provider"))
        } finally {
            NightExtensionToolRegistry.unregisterExtension(preferred.extensionId)
            NightExtensionToolRegistry.unregisterExtension(fallback.extensionId)
            NightExtensionPreferenceRouter.clearForTests()
        }
    }

    @Test
    fun legacyExtensionToolsRemainSideEffectsByDefault() {
        val definition = NightExtensionToolDefinition(
            extensionId = "legacy",
            name = "do_work",
            description = "Legacy extension action.",
            parameters = JSONObject()
                .put("type", "object")
                .put("properties", JSONObject()),
        )

        try {
            NightExtensionToolRegistry.register(definition) { _, _ ->
                JSONObject().put("ok", true)
            }

            assertTrue(
                NightIntegrationToolRegistry.isSideEffect(
                    definition.qualifiedName,
                ),
            )
        } finally {
            NightExtensionToolRegistry.unregisterExtension("legacy")
        }
    }
}
