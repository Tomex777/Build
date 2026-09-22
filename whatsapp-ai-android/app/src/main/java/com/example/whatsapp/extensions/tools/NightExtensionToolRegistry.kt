package com.example.whatsapp.extensions.tools

import com.example.whatsapp.extensions.runtime.NightIntegrationCapability
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

data class NightExtensionToolDefinition(
    val extensionId: String,
    val name: String,
    val description: String,
    val parameters: JSONObject,
    val readOnly: Boolean = false,
    val capabilities: Set<NightIntegrationCapability> = emptySet(),
) {
    val qualifiedName: String =
        "ext__" + sanitize(extensionId) + "__" + sanitize(name)

    fun toProviderSchema(): JSONObject =
        JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", qualifiedName)
                    .put("description", description)
                    .put("parameters", parameters)
            )

    companion object {
        private fun sanitize(value: String): String =
            value.lowercase()
                .replace(Regex("[^a-z0-9_]+"), "_")
                .trim('_')
                .take(48)
                .ifBlank { "tool" }
    }
}

fun interface NightExtensionToolHandler {
    suspend fun execute(
        chatId: String,
        arguments: JSONObject,
    ): JSONObject
}

/**
 * In-process extension tool registry.
 *
 * External APK discovery/binding can plug into this registry later without changing the
 * model-facing agent protocol. The core only sees namespaced function schemas and JSON results.
 */
object NightExtensionToolRegistry {
    private data class Registered(
        val definition: NightExtensionToolDefinition,
        val handler: NightExtensionToolHandler,
    )

    private val tools = ConcurrentHashMap<String, Registered>()

    fun register(
        definition: NightExtensionToolDefinition,
        handler: NightExtensionToolHandler,
    ) {
        require(definition.extensionId.isNotBlank()) { "extensionId is required." }
        require(definition.name.isNotBlank()) { "tool name is required." }
        tools[definition.qualifiedName] = Registered(definition, handler)
    }

    fun unregisterExtension(extensionId: String) {
        tools.entries.removeIf { it.value.definition.extensionId == extensionId }
    }

    fun schemas(): JSONArray {
        val registered = tools.values.toList()
        val array = JSONArray()
        registered
            .filter { shouldExposeToModel(it.definition, registered) }
            .sortedBy { it.definition.qualifiedName }
            .forEach { array.put(it.definition.toProviderSchema()) }
        return array
    }

    private fun shouldExposeToModel(
        definition: NightExtensionToolDefinition,
        all: List<Registered>,
    ): Boolean {
        if (definition.capabilities.isEmpty()) return true

        val preferredIds =
            NightExtensionPreferenceRouter
                .preferredExtensionIds(definition.capabilities)
        if (preferredIds.isEmpty()) return true

        val preferredEquivalentExists =
            all.any { candidate ->
                candidate.definition.extensionId in preferredIds &&
                    candidate.definition.name == definition.name &&
                    candidate.definition.capabilities
                        .intersect(definition.capabilities)
                        .isNotEmpty()
            }

        if (!preferredEquivalentExists) return true
        return definition.extensionId in preferredIds
    }

    fun extensionIdFor(qualifiedName: String): String? =
        tools[qualifiedName]?.definition?.extensionId

    fun qualifiedNameFor(
        extensionId: String,
        toolName: String,
    ): String? =
        tools.values
            .firstOrNull {
                it.definition.extensionId == extensionId &&
                    it.definition.name == toolName
            }
            ?.definition
            ?.qualifiedName

    fun definition(qualifiedName: String): NightExtensionToolDefinition? =
        tools[qualifiedName]?.definition

    fun isSideEffect(qualifiedName: String): Boolean =
        tools[qualifiedName]?.definition?.readOnly == false

    suspend fun execute(
        qualifiedName: String,
        chatId: String,
        arguments: JSONObject,
    ): JSONObject? {
        val requested = tools[qualifiedName] ?: return null
        val requestedDefinition = requested.definition

        val equivalents =
            tools.values
                .filter { candidate ->
                    candidate.definition.name == requestedDefinition.name &&
                        (
                            candidate.definition.qualifiedName ==
                                requestedDefinition.qualifiedName ||
                                (
                                    requestedDefinition.capabilities.isNotEmpty() &&
                                        candidate.definition.capabilities
                                            .intersect(
                                                requestedDefinition.capabilities
                                            )
                                            .isNotEmpty()
                                )
                        )
                }

        val preferredIds =
            NightExtensionPreferenceRouter
                .preferredExtensionIds(requestedDefinition.capabilities)

        val ordered =
            equivalents.sortedWith(
                compareByDescending<Registered> {
                    it.definition.extensionId in preferredIds
                }
                    .thenByDescending {
                        it.definition.qualifiedName ==
                            requestedDefinition.qualifiedName
                    }
                    .thenBy { it.definition.extensionId }
            )

        var lastError: Throwable? = null
        ordered.forEach { candidate ->
            runCatching {
                candidate.handler.execute(chatId, arguments)
            }.onSuccess { return it }
                .onFailure { lastError = it }
        }

        lastError?.let { throw it }
        return null
    }
}
