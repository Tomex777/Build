package com.example.whatsapp.extensions.runtime

import com.example.whatsapp.extensions.tools.NightMcpServerConfig
import com.example.whatsapp.extensions.tools.NightMcpServerRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightIntegrationSummaryTest {
    @Test
    fun extensionUsesSharedShellWithExtensionMarker() {
        val integration = NightInstalledExtensionSummary(
            extensionId = "animepahe",
            displayName = "AnimePahe",
            packageName = "night.extension.animepahe",
            serviceName = "ExtensionService",
            toolCount = 4,
            messageTypeCount = 2,
            enabled = true,
        ).asNightIntegration()

        assertEquals(NightIntegrationKind.EXTENSION, integration.kind)
        assertEquals("animepahe", integration.id)
        assertEquals(4, integration.toolCount)
        assertTrue(integration.enabled)
    }

    @Test
    fun mcpUsesSameShellWithOnlyMcpMarkerDifferent() {
        val integration = NightMcpServerRuntimeState(
            config = NightMcpServerConfig(
                id = "github",
                displayName = "GitHub",
                endpoint = "https://mcp.example.com/github",
                secretAlias = "mcp_server_github",
                enabled = false,
                createdAt = 1L,
                updatedAt = 2L,
            ),
            hasBearerToken = true,
            connected = false,
            toolCount = 12,
            error = "Offline",
        ).asNightIntegration()

        assertEquals(NightIntegrationKind.MCP, integration.kind)
        assertEquals("github", integration.id)
        assertEquals(12, integration.toolCount)
        assertFalse(integration.enabled)
        assertEquals("Offline", integration.error)
    }
}
