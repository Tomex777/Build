package com.night.extension.sdk

object NightExtensionProtocol {
    const val ACTION_EXTENSION_SERVICE =
        "com.example.whatsapp.action.NIGHT_EXTENSION_SERVICE"

    const val MSG_DESCRIBE = 1
    const val MSG_EXECUTE_TOOL = 2
    const val MSG_EXECUTE_ACTION = 3
    const val MSG_RESULT = 100

    const val KEY_REQUEST_ID = "requestId"
    const val KEY_OK = "ok"
    const val KEY_ERROR = "error"
    const val KEY_RESULT_JSON = "resultJson"
    const val KEY_EXTENSION_ID = "extensionId"
    const val KEY_CHAT_ID = "chatId"
    const val KEY_TOOL_NAME = "toolName"
    const val KEY_ARGUMENTS_JSON = "argumentsJson"
    const val KEY_MESSAGE_ID = "messageId"
    const val KEY_MESSAGE_TYPE = "messageType"
    const val KEY_ACTION_ID = "actionId"
    const val KEY_PAYLOAD_JSON = "payloadJson"

    const val SUPPORTED_SCHEMA_VERSION = 1
}

object NightExtensionStandardActions {
    const val PLAY_MEDIA = "night.media.play"
    const val DOWNLOAD_MEDIA = "night.media.download"
    const val ADD_TO_LIBRARY = "night.media.library.add"
    const val REMOVE_FROM_LIBRARY = "night.media.library.remove"
    const val ADD_TO_PLAYLIST = "night.media.playlist.add"
    const val REMOVE_FROM_PLAYLIST = "night.media.playlist.remove"
}

data class NightToolRequest(
    val extensionId: String,
    val chatId: String,
    val toolName: String,
    val argumentsJson: String,
)

data class NightActionRequest(
    val extensionId: String,
    val chatId: String,
    val messageId: String,
    val messageType: String,
    val actionId: String,
    val payloadJson: String,
)
