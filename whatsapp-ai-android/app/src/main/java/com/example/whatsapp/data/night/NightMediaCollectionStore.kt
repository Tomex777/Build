package com.example.whatsapp.data.night

import android.content.Context
import com.example.whatsapp.extensions.messages.ExtensionMessageSnapshot
import com.example.whatsapp.extensions.messages.NightExtensionStandardActions
import org.json.JSONArray
import org.json.JSONObject

data class NightMediaCollectionItem(
    val extensionId: String,
    val mediaId: String,
    val mediaKind: String,
    val title: String,
    val subtitle: String = "",
    val artworkPath: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
) {
    val key: String
        get() = extensionId + "::" + mediaId
}

object NightMediaCollectionStore {
    private const val PREFS = "night_media_collection"
    private const val KEY_LIBRARY = "library"
    private const val KEY_PLAYLISTS = "playlists"
    private const val DEFAULT_PLAYLIST = "My playlist"
    private const val MAX_LIBRARY_ITEMS = 1000
    private const val MAX_PLAYLIST_ITEMS = 500

    fun library(context: Context): List<NightMediaCollectionItem> =
        readItems(
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LIBRARY, "[]")
                .orEmpty()
        )

    fun playlist(
        context: Context,
        name: String = DEFAULT_PLAYLIST,
    ): List<NightMediaCollectionItem> {
        val playlists =
            runCatching {
                JSONObject(
                    context.applicationContext
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getString(KEY_PLAYLISTS, "{}")
                        .orEmpty()
                )
            }.getOrElse { JSONObject() }

        return readItems(
            playlists.optJSONArray(normalizePlaylistName(name))?.toString()
                ?: "[]"
        )
    }

    fun handleAction(
        context: Context,
        snapshot: ExtensionMessageSnapshot,
        actionId: String,
    ): String {
        val item = snapshot.toMediaCollectionItem()
        val payload =
            runCatching { JSONObject(snapshot.extensionPayloadJson) }
                .getOrElse { JSONObject() }
        val playlistName =
            payload.optString("playlist")
                .trim()
                .ifBlank { DEFAULT_PLAYLIST }

        return when (actionId) {
            NightExtensionStandardActions.ADD_TO_LIBRARY -> {
                addToLibrary(context, item)
                "Added to media library."
            }

            NightExtensionStandardActions.REMOVE_FROM_LIBRARY -> {
                removeFromLibrary(context, item.key)
                "Removed from media library."
            }

            NightExtensionStandardActions.ADD_TO_PLAYLIST -> {
                addToPlaylist(context, playlistName, item)
                "Added to " + playlistName + "."
            }

            NightExtensionStandardActions.REMOVE_FROM_PLAYLIST -> {
                removeFromPlaylist(context, playlistName, item.key)
                "Removed from " + playlistName + "."
            }

            else -> error("Unknown Night media collection action.")
        }
    }

    fun addToLibrary(
        context: Context,
        item: NightMediaCollectionItem,
    ) {
        val updated =
            listOf(item) +
                library(context).filterNot { it.key == item.key }

        saveLibrary(
            context,
            updated.take(MAX_LIBRARY_ITEMS),
        )
    }

    fun removeFromLibrary(
        context: Context,
        itemKey: String,
    ) {
        saveLibrary(
            context,
            library(context).filterNot { it.key == itemKey },
        )
    }

    fun addToPlaylist(
        context: Context,
        name: String,
        item: NightMediaCollectionItem,
    ) {
        val safeName = normalizePlaylistName(name)
        val updated =
            listOf(item) +
                playlist(context, safeName)
                    .filterNot { it.key == item.key }

        savePlaylist(
            context,
            safeName,
            updated.take(MAX_PLAYLIST_ITEMS),
        )
    }

    fun removeFromPlaylist(
        context: Context,
        name: String,
        itemKey: String,
    ) {
        val safeName = normalizePlaylistName(name)
        savePlaylist(
            context,
            safeName,
            playlist(context, safeName)
                .filterNot { it.key == itemKey },
        )
    }

    private fun ExtensionMessageSnapshot.toMediaCollectionItem():
        NightMediaCollectionItem {
        val payload =
            runCatching { JSONObject(extensionPayloadJson) }
                .getOrElse { JSONObject() }

        val mediaId =
            payload.optString("mediaId")
                .trim()
                .ifBlank {
                    payload.optString("id")
                        .trim()
                }
                .ifBlank {
                    messageType + "::" + title
                }

        val mediaKind =
            payload.optString("mediaKind")
                .trim()
                .lowercase()
                .ifBlank {
                    when {
                        messageType.contains("anime", ignoreCase = true) ->
                            "anime"
                        messageType.contains("manga", ignoreCase = true) ->
                            "manga"
                        messageType.contains("music", ignoreCase = true) ||
                            messageType.contains("track", ignoreCase = true) ->
                            "music"
                        else -> "media"
                    }
                }

        return NightMediaCollectionItem(
            extensionId = extensionId,
            mediaId = mediaId.take(240),
            mediaKind = mediaKind.take(48),
            title = title.take(240),
            subtitle = subtitle.take(320),
            artworkPath = artworkPath?.take(4096),
        )
    }

    private fun saveLibrary(
        context: Context,
        items: List<NightMediaCollectionItem>,
    ) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(
                KEY_LIBRARY,
                encodeItems(items).toString(),
            )
            .apply()
    }

    private fun savePlaylist(
        context: Context,
        name: String,
        items: List<NightMediaCollectionItem>,
    ) {
        val prefs =
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val playlists =
            runCatching {
                JSONObject(prefs.getString(KEY_PLAYLISTS, "{}").orEmpty())
            }.getOrElse { JSONObject() }

        playlists.put(name, encodeItems(items))
        prefs.edit()
            .putString(KEY_PLAYLISTS, playlists.toString())
            .apply()
    }

    private fun readItems(raw: String): List<NightMediaCollectionItem> =
        runCatching {
            val array = JSONArray(raw.ifBlank { "[]" })
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val extensionId = item.optString("extensionId").trim()
                    val mediaId = item.optString("mediaId").trim()
                    val title = item.optString("title").trim()
                    if (
                        extensionId.isBlank() ||
                        mediaId.isBlank() ||
                        title.isBlank()
                    ) {
                        continue
                    }

                    add(
                        NightMediaCollectionItem(
                            extensionId = extensionId,
                            mediaId = mediaId,
                            mediaKind =
                                item.optString("mediaKind")
                                    .ifBlank { "media" },
                            title = title,
                            subtitle = item.optString("subtitle"),
                            artworkPath =
                                item.optString("artworkPath")
                                    .takeIf { it.isNotBlank() },
                            addedAt = item.optLong(
                                "addedAt",
                                System.currentTimeMillis(),
                            ),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())

    private fun encodeItems(
        items: List<NightMediaCollectionItem>,
    ): JSONArray =
        JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject()
                        .put("extensionId", item.extensionId)
                        .put("mediaId", item.mediaId)
                        .put("mediaKind", item.mediaKind)
                        .put("title", item.title)
                        .put("subtitle", item.subtitle)
                        .put("artworkPath", item.artworkPath ?: "")
                        .put("addedAt", item.addedAt)
                )
            }
        }

    private fun normalizePlaylistName(raw: String): String =
        raw.trim()
            .take(80)
            .ifBlank { DEFAULT_PLAYLIST }
}
