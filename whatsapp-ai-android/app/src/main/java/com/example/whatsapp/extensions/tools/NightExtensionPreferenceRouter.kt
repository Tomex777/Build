package com.example.whatsapp.extensions.tools

import com.example.whatsapp.extensions.runtime.NightIntegrationCapability
import java.util.concurrent.ConcurrentHashMap

/**
 * Small in-memory routing snapshot shared by extension discovery and the
 * model-facing tool facade. Preferences remain persisted by
 * NightExternalExtensionManager; this object only supplies the current process
 * with fast routing decisions.
 */
object NightExtensionPreferenceRouter {
    private val preferredByCapability =
        ConcurrentHashMap<String, String>()

    fun replace(
        values: Map<NightIntegrationCapability, String>,
    ) {
        preferredByCapability.clear()
        values.forEach { (capability, extensionId) ->
            setPreferred(capability, extensionId)
        }
    }

    fun setPreferred(
        capability: NightIntegrationCapability,
        extensionId: String?,
    ) {
        val id = extensionId?.trim().orEmpty()
        if (id.isBlank()) {
            preferredByCapability.remove(capability.wireName)
        } else {
            preferredByCapability[capability.wireName] = id
        }
    }

    fun preferredExtensionId(
        capability: NightIntegrationCapability,
    ): String? =
        preferredByCapability[capability.wireName]
            ?.takeIf { it.isNotBlank() }

    fun preferredExtensionIds(
        capabilities: Set<NightIntegrationCapability>,
    ): Set<String> =
        capabilities
            .mapNotNull(::preferredExtensionId)
            .toSet()

    internal fun clearForTests() {
        preferredByCapability.clear()
    }
}
