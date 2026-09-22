package com.example.whatsapp.extensions.tools

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
        } finally {
            NightExtensionToolRegistry.unregisterExtension("anime")
            NightMcpToolRegistry.unregisterServer("github")
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
