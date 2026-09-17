package com.night.sora.ui.screens

import com.night.sora.extension.ExtensionManager
import com.night.sora.extension.InstalledExtension
import com.night.sora.extension.api.ExtensionContract
import com.night.sora.model.ContentType
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.model.MediaProgressEntry
import com.night.sora.model.PlaybackSession
import com.night.sora.model.ReaderSession
import org.json.JSONObject

/**
 * Re-resolves the exact child item from the source that originally served it.
 * Catalog metadata identity stays separate from consumption-source identity.
 */
internal fun resumeMediaProgress(
    entry: MediaProgressEntry,
    extensions: List<InstalledExtension>,
    manager: ExtensionManager,
    onOpenReader: (ReaderSession) -> Unit,
    onOpenPlayer: (PlaybackSession) -> Unit,
    onFallback: (ExtensionMediaSelection) -> Unit,
) {
    val parent = entry.toMediaSelection()
    val extension = extensions.firstOrNull {
        it.error == null && it.packageName == entry.resumeExtensionPackage
    }
    val source = extension?.descriptor?.sources?.firstOrNull { it.id == entry.resumeSourceId }
    if (extension == null || source == null || entry.itemId.isBlank()) {
        onFallback(parent)
        return
    }

    val payload = JSONObject()
        .put("sourceId", entry.resumeSourceId)
        .put("id", entry.itemId)
        .toString()

    when (entry.contentType) {
        ContentType.MANGA -> {
            manager.call(extension, ExtensionContract.Method.PAGES, payload) { result ->
                val pages = result.getOrNull()?.let(::parseReaderPages).orEmpty()
                if (pages.isEmpty()) {
                    onFallback(parent)
                    return@call
                }
                val pageIndex = (entry.position.toInt() - 1).coerceIn(0, pages.lastIndex)
                onOpenReader(
                    ReaderSession(
                        title = entry.title,
                        chapterTitle = entry.itemLabel.ifBlank { entry.title },
                        sourceName = source.name,
                        pages = pages,
                        initialPage = pageIndex,
                        media = parent,
                        itemId = entry.itemId,
                        consumptionSourceId = entry.resumeSourceId,
                        consumptionExtensionPackage = entry.resumeExtensionPackage,
                    )
                )
            }
        }

        ContentType.ANIME, ContentType.TV, ContentType.MOVIE -> {
            manager.call(extension, ExtensionContract.Method.STREAMS, payload) { result ->
                val streams = result.getOrNull()?.let(::parsePlaybackStreams).orEmpty()
                if (streams.isEmpty()) {
                    onFallback(parent)
                    return@call
                }
                val resumeAt = entry.position.coerceAtLeast(0L)
                onOpenPlayer(
                    PlaybackSession(
                        title = entry.title,
                        episodeTitle = entry.itemLabel.ifBlank { entry.title },
                        sourceName = source.name,
                        streams = streams,
                        initialPositionMs = resumeAt,
                        media = parent,
                        itemId = entry.itemId,
                        consumptionSourceId = entry.resumeSourceId,
                        consumptionExtensionPackage = entry.resumeExtensionPackage,
                    )
                )
            }
        }

        ContentType.MUSIC, ContentType.MEME -> onFallback(parent)
    }
}
