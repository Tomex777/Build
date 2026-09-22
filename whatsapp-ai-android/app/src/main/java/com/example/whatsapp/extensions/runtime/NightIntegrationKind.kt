package com.example.whatsapp.extensions.runtime

/**
 * Small discriminator shared by Night integrations.
 *
 * Extensions and MCP connections intentionally share Night's integration UX;
 * this marker selects the runtime adapter without creating a second ecosystem.
 */
enum class NightIntegrationKind(val wireName: String) {
    EXTENSION("extension"),
    MCP("mcp");

    companion object {
        fun fromWireName(value: String?): NightIntegrationKind =
            entries.firstOrNull { it.wireName == value?.trim()?.lowercase() }
                ?: EXTENSION
    }
}
