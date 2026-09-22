package com.example.whatsapp.extensions.runtime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class NightIntegrationManifestTest {
    @Test
    fun parsesExtensionAndMcpWithSameEnvelope() {
        val extension = NightIntegrationManifest.fromJson(
            JSONObject()
                .put("id", "animepahe")
                .put("name", "AnimePahe")
                .put("type", "extension")
        )
        val mcp = NightIntegrationManifest.fromJson(
            JSONObject()
                .put("id", "github")
                .put("name", "GitHub")
                .put("type", "mcp")
        )

        assertEquals(NightIntegrationKind.EXTENSION, extension.kind)
        assertEquals(NightIntegrationKind.MCP, mcp.kind)
        assertEquals("animepahe", extension.id)
        assertEquals("github", mcp.id)
    }

    @Test
    fun legacyExtensionDescriptorRemainsCompatible() {
        val manifest = NightIntegrationManifest.fromJson(
            JSONObject()
                .put("extensionId", "legacy.reader")
                .put("extensionName", "Legacy Reader")
        )

        assertEquals(NightIntegrationKind.EXTENSION, manifest.kind)
        assertEquals("legacy.reader", manifest.id)
        assertEquals("Legacy Reader", manifest.displayName)
    }

    @Test
    fun writesOnlyTinyTypeMarkerToDifferentiateRuntime() {
        val json = NightIntegrationManifest(
            id = "github",
            displayName = "GitHub",
            kind = NightIntegrationKind.MCP,
        ).toJson()

        assertEquals("github", json.getString("id"))
        assertEquals("GitHub", json.getString("name"))
        assertEquals("mcp", json.getString("type"))
    }
}
