package com.example.whatsapp.data.night

import com.example.whatsapp.extensions.messages.ExtensionMessageCodec
import com.example.whatsapp.extensions.messages.NightExtensionMessageTypeRegistry
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

object NightExtensionMessageEmitter {
    private const val MAX_MESSAGES_PER_TOOL_RESULT = 6

    suspend fun persistFromToolResult(
        repository: NightRepository,
        chatId: String,
        ownerExtensionId: String,
        result: JSONObject,
    ): JSONObject {
        val candidates = mutableListOf<JSONObject>()

        result.optJSONObject("night_message")?.let(candidates::add)

        val multiple = result.optJSONArray("night_messages")
        if (multiple != null) {
            for (
                index in 0 until minOf(
                    multiple.length(),
                    MAX_MESSAGES_PER_TOOL_RESULT,
                )
            ) {
                multiple.optJSONObject(index)?.let(candidates::add)
            }
        }

        if (candidates.isEmpty()) return result

        val emitted = JSONArray()

        candidates.take(MAX_MESSAGES_PER_TOOL_RESULT).forEach { raw ->
            val snapshot = ExtensionMessageCodec.decode(raw.toString())
                ?: error("Extension returned an invalid Night message.")

            require(snapshot.extensionId == ownerExtensionId) {
                "Extension " + ownerExtensionId +
                    " cannot emit messages owned by " + snapshot.extensionId + "."
            }
            require(snapshot.hasValidNamespace()) {
                "Extension message type must be namespaced by its extension id."
            }
            require(
                NightExtensionMessageTypeRegistry.isAllowed(
                    extensionId = ownerExtensionId,
                    messageType = snapshot.messageType,
                    template = snapshot.template,
                )
            ) {
                "Extension message type " + snapshot.messageType +
                    " is not declared for template " + snapshot.template.wireName + "."
            }

            val messageId = UUID.randomUUID().toString()
            repository.appendMessage(
                NightMessageEntity(
                    id = messageId,
                    chatId = chatId,
                    role = "assistant",
                    type = "extension",
                    text = snapshot.title,
                    createdAt = System.currentTimeMillis(),
                    payloadJson = ExtensionMessageCodec.encode(snapshot),
                )
            )

            emitted.put(
                JSONObject()
                    .put("message_id", messageId)
                    .put("message_type", snapshot.messageType)
                    .put("title", snapshot.title)
                    .put("rendered", true)
            )
        }

        return JSONObject(result.toString())
            .put("night_rendered_messages", emitted)
    }
}
