package com.example.whatsapp.extensions.messages

object NightExtensionStandardActions {
    const val ADD_TO_LIBRARY = "night.media.library.add"
    const val REMOVE_FROM_LIBRARY = "night.media.library.remove"
    const val ADD_TO_PLAYLIST = "night.media.playlist.add"
    const val REMOVE_FROM_PLAYLIST = "night.media.playlist.remove"

    fun isMediaCollectionAction(actionId: String): Boolean =
        actionId == ADD_TO_LIBRARY ||
            actionId == REMOVE_FROM_LIBRARY ||
            actionId == ADD_TO_PLAYLIST ||
            actionId == REMOVE_FROM_PLAYLIST
}
