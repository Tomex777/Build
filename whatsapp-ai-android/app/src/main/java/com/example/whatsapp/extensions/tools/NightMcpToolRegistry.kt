package com.example.whatsapp.extensions.tools

import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

data class NightMcpToolDefinition(
    val serverId: String,
    val name: String,
    val description: String,
    val parameters: JSONObject,
    val readOnly: Boolean = false,
) {
    init {
        require(serverId.isNotBlank()) { "serverId is required." }
        require(name.isNotBlank()) { "tool name is required." }
        require(description.isNotBlank()) { "tool description is required." }
    }

    val qualifiedName: String =
        "mcp__" + sanitize(serverId) + "__" + sanitize(name)

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

fun interface NightMcpToolHandler {
    suspend fun execute(
        chatId: String,
        arguments: JSONObject,
    ): JSONObject
}

/**
 * MCP-facing tool registry.
 *
 * MCP transports register their discovered tools here. The AI gateway then sees
 * those tools beside Night's built-ins and extension tools, so the model-facing
 * agent loop never needs provider- or transport-specific branching.
 */
object NightMcpToolRegistry {
    private data class Registered(
        val definition: NightMcpToolDefinition,
        val handler: NightMcpToolHandler,
    )

    private val tools = ConcurrentHashMap<String, Registered>()

    fun register(
        definition: NightMcpToolDefinition,
        handler: NightMcpToolHandler,
    ) {
        tools[definition.qualifiedName] = Registered(definition, handler)
    }

    fun unregisterServer(serverId: String) {
        tools.entries.removeIf {
            it.value.definition.serverId == serverId
        }
    }

    fun schemas(): JSONArray =
        JSONArray().apply {
            tools.values
                .sortedBy { it.definition.qualifiedName }
                .forEach { put(it.definition.toProviderSchema()) }
        }

    fun contains(qualifiedName: String): Boolean =
        tools.containsKey(qualifiedName)

    fun isSideEffect(qualifiedName: String): Boolean =
        tools[qualifiedName]?.definition?.readOnly == false

    suspend fun execute(
        qualifiedName: String,
        chatId: String,
        arguments: JSONObject,
    ): JSONObject? =
        tools[qualifiedName]?.handler?.execute(chatId, arguments)
}
