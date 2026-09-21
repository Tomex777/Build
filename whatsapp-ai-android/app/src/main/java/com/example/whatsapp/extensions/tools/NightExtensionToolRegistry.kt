package com.example.whatsapp.extensions.tools

import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

data class NightExtensionToolDefinition(
    val extensionId: String,
    val name: String,
    val description: String,
    val parameters: JSONObject,
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
        val array = JSONArray()
        tools.values
            .sortedBy { it.definition.qualifiedName }
            .forEach { array.put(it.definition.toProviderSchema()) }
        return array
    }

    fun extensionIdFor(qualifiedName: String): String? =
        tools[qualifiedName]?.definition?.extensionId

    suspend fun execute(
        qualifiedName: String,
        chatId: String,
        arguments: JSONObject,
    ): JSONObject? {
        val registered = tools[qualifiedName] ?: return null
        return registered.handler.execute(chatId, arguments)
    }
}
