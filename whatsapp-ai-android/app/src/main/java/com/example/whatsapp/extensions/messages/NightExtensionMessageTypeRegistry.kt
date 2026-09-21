package com.example.whatsapp.extensions.messages

import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

data class NightExtensionMessageTypeDefinition(
    val extensionId: String,
    val messageType: String,
    val template: ExtensionCardTemplate,
    val description: String,
    val whenToUse: String = "",
) {
    init {
        require(extensionId.isNotBlank()) { "extensionId is required." }
        require(
            messageType.startsWith(extensionId + ".") &&
                messageType.length > extensionId.length + 1
        ) {
            "messageType must be namespaced by extensionId."
        }
        require(description.isNotBlank()) { "description is required." }
    }

    fun toPromptJson(): JSONObject =
        JSONObject()
            .put("extensionId", extensionId)
            .put("messageType", messageType)
            .put("template", template.wireName)
            .put("description", description)
            .put("whenToUse", whenToUse)
}

object NightExtensionMessageTypeRegistry {
    private val definitions =
        ConcurrentHashMap<String, NightExtensionMessageTypeDefinition>()

    fun register(definition: NightExtensionMessageTypeDefinition) {
        definitions[key(definition.extensionId, definition.messageType)] = definition
    }

    fun registerAll(items: Collection<NightExtensionMessageTypeDefinition>) {
        items.forEach(::register)
    }

    fun unregisterExtension(extensionId: String) {
        definitions.entries.removeIf {
            it.value.extensionId == extensionId
        }
    }

    fun definitionsFor(extensionId: String): List<NightExtensionMessageTypeDefinition> =
        definitions.values
            .filter { it.extensionId == extensionId }
            .sortedBy { it.messageType }

    fun all(): List<NightExtensionMessageTypeDefinition> =
        definitions.values.sortedWith(
            compareBy<NightExtensionMessageTypeDefinition> { it.extensionId }
                .thenBy { it.messageType }
        )

    fun isAllowed(
        extensionId: String,
        messageType: String,
        template: ExtensionCardTemplate,
    ): Boolean {
        val extensionDefinitions = definitionsFor(extensionId)

        // Backward compatibility for extensions that predate explicit declarations:
        // namespace ownership remains mandatory, while a registered extension becomes strict.
        if (extensionDefinitions.isEmpty()) {
            return messageType.startsWith(extensionId + ".")
        }

        return extensionDefinitions.any {
            it.messageType == messageType && it.template == template
        }
    }

    fun promptJson(): JSONArray =
        JSONArray().apply {
            all().forEach { put(it.toPromptJson()) }
        }

    fun promptSummary(): String {
        val items = all()
        if (items.isEmpty()) return ""

        return buildString {
            append("Installed extensions declare these Night message types. ")
            append("Use the message type selected by the extension; do not invent or rename it:\n")
            items.forEach { item ->
                append("- ")
                append(item.messageType)
                append(" [")
                append(item.template.wireName)
                append("]: ")
                append(item.description)
                if (item.whenToUse.isNotBlank()) {
                    append(" Use when: ")
                    append(item.whenToUse)
                }
                append("\n")
            }
        }.trim()
    }

    private fun key(extensionId: String, messageType: String): String =
        extensionId + "::" + messageType
}
