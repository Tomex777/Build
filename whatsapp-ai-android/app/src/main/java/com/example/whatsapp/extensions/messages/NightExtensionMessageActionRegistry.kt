package com.example.whatsapp.extensions.messages

import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

fun interface NightExtensionMessageActionHandler {
    suspend fun execute(
        chatId: String,
        messageId: String,
        messageType: String,
        actionId: String,
        payload: JSONObject,
    ): JSONObject
}

object NightExtensionMessageActionRegistry {
    private val handlers =
        ConcurrentHashMap<String, NightExtensionMessageActionHandler>()

    fun register(
        extensionId: String,
        handler: NightExtensionMessageActionHandler,
    ) {
        require(extensionId.isNotBlank()) { "extensionId is required." }
        handlers[extensionId] = handler
    }

    fun unregister(extensionId: String) {
        handlers.remove(extensionId)
    }

    fun hasHandler(extensionId: String): Boolean =
        handlers.containsKey(extensionId)

    suspend fun execute(
        extensionId: String,
        chatId: String,
        messageId: String,
        messageType: String,
        actionId: String,
        payload: JSONObject,
    ): JSONObject? =
        handlers[extensionId]?.execute(
            chatId,
            messageId,
            messageType,
            actionId,
            payload,
        )
}
