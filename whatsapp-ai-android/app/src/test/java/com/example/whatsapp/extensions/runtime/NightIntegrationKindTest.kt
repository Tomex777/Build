package com.example.whatsapp.extensions.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class NightIntegrationKindTest {
    @Test
    fun wireNamesKeepExtensionAndMcpInOneIntegrationFormat() {
        assertEquals(
            NightIntegrationKind.EXTENSION,
            NightIntegrationKind.fromWireName("extension"),
        )
        assertEquals(
            NightIntegrationKind.MCP,
            NightIntegrationKind.fromWireName("MCP"),
        )
    }

    @Test
    fun missingMarkerStaysBackwardCompatibleWithExtensions() {
        assertEquals(
            NightIntegrationKind.EXTENSION,
            NightIntegrationKind.fromWireName(null),
        )
        assertEquals(
            NightIntegrationKind.EXTENSION,
            NightIntegrationKind.fromWireName("unknown"),
        )
    }
}
