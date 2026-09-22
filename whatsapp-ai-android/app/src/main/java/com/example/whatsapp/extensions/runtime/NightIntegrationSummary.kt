package com.example.whatsapp.extensions.runtime

import com.example.whatsapp.extensions.tools.NightMcpServerRuntimeState

/**
 * Common presentation identity for anything Night can install/connect as an integration.
 *
 * Extensions and MCP servers deliberately keep their own runtimes. This model is the
 * shared shell used by discovery/settings UI; [kind] is the small discriminator that
 * tells Night which runtime adapter owns the integration.
 */
data class NightIntegrationSummary(
    val id: String,
    val displayName: String,
    val kind: NightIntegrationKind,
    val enabled: Boolean,
    val toolCount: Int,
    val capabilities: Set<NightIntegrationCapability> = emptySet(),
    val error: String? = null,
)

fun NightInstalledExtensionSummary.asNightIntegration(): NightIntegrationSummary =
    NightIntegrationSummary(
        id = extensionId,
        displayName = displayName,
        kind = NightIntegrationKind.EXTENSION,
        enabled = enabled,
        toolCount = toolCount,
        capabilities = capabilities,
        error = error,
    )

fun NightMcpServerRuntimeState.asNightIntegration(): NightIntegrationSummary =
    NightIntegrationSummary(
        id = config.id,
        displayName = config.displayName,
        kind = NightIntegrationKind.MCP,
        enabled = config.enabled,
        toolCount = toolCount,
        error = error,
    )
