package com.example.whatsapp.extensions.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun arbitraryCapabilityIdsDoNotRequireNightAppConstants() {
        val manifest =
            NightIntegrationManifest.fromJson(
                JSONObject()
                    .put("id", "unreal.assets")
                    .put("name", "Unreal Engine Assets")
                    .put("type", "extension")
                    .put(
                        "capabilities",
                        JSONArray()
                            .put("unreal_engine")
                            .put("asset_import"),
                    )
            )

        assertTrue(
            NightIntegrationCapability.require("unreal_engine") in
                manifest.capabilities
        )
        assertTrue(
            NightIntegrationCapability.require("asset_import") in
                manifest.capabilities
        )
    }

    @Test
    fun matchingExtensionNamesCanYieldSharedCapabilityHints() {
        val first =
            NightIntegrationManifest.fromJson(
                JSONObject()
                    .put("id", "unreal.one")
                    .put("name", "Unreal Engine Tools")
                    .put("type", "extension")
            )
        val second =
            NightIntegrationManifest.fromJson(
                JSONObject()
                    .put("id", "unreal.two")
                    .put("name", "Unreal Engine Assets")
                    .put("type", "extension")
            )

        val shared = first.capabilities.intersect(second.capabilities)
        assertTrue(
            NightIntegrationCapability.require("unreal_engine") in shared
        )
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
