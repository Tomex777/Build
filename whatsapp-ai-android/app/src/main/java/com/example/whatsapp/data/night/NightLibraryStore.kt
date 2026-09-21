package com.example.whatsapp.data.night

import android.content.Context
import android.net.Uri
import com.example.whatsapp.data.NightFileLibrary
import com.example.whatsapp.data.NightLibraryFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Canonical bridge for Night Library metadata.
 *
 * Room is the source of truth used by both the Library UI and Night's AI tools.
 * NightFileLibrary is retained only as the on-device file copier and as a
 * migration source for files imported by older builds.
 */
class NightLibraryStore private constructor(
    context: Context,
    private val repository: NightRepository,
) {
    private val appContext = context.applicationContext

    suspend fun importUri(source: Uri): Result<NightLibraryItemEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val copied = NightFileLibrary.importUri(appContext, source)
                    ?: error("Night could not import this file.")
                entityFromLegacy(copied).also { repository.addLibraryItem(it) }
            }
        }

    suspend fun saveText(
        name: String,
        text: String,
        markdown: Boolean = true,
        sourceChatId: String? = null,
        sourceMessageId: String? = null,
    ): Result<NightLibraryItemEntity> = withContext(Dispatchers.IO) {
        runCatching {
            require(text.isNotBlank()) { "text is required." }
            require(text.length <= MAX_TEXT_CHARS) {
                "Library text files are limited to " + MAX_TEXT_CHARS + " characters."
            }

            val id = java.util.UUID.randomUUID().toString()
            val fileName = normalizedTextName(name, markdown)
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val root = File(appContext.filesDir, "night_library").apply { mkdirs() }
            val target = File(root, id + "_" + safeName)
            target.writeText(text, Charsets.UTF_8)

            NightLibraryItemEntity(
                id = id,
                name = fileName,
                mimeType = if (markdown) "text/markdown" else "text/plain",
                sizeBytes = target.length(),
                localPath = target.absolutePath,
                createdAt = System.currentTimeMillis(),
                sourceChatId = sourceChatId,
                sourceMessageId = sourceMessageId,
            ).also { repository.addLibraryItem(it) }
        }
    }

    suspend fun migrateLegacy(): Int = withContext(Dispatchers.IO) {
        val existingIds = repository.getLibraryItems()
            .asSequence()
            .map { it.id }
            .toMutableSet()

        var migrated = 0
        NightFileLibrary.list(appContext).forEach { legacy ->
            if (
                legacy.id !in existingIds &&
                File(legacy.localPath).let { it.exists() && it.isFile }
            ) {
                repository.addLibraryItem(entityFromLegacy(legacy))
                existingIds += legacy.id
                migrated++
            }
        }
        migrated
    }

    companion object {
        private const val MAX_TEXT_CHARS = 200_000
        @Volatile private var instance: NightLibraryStore? = null

        fun get(context: Context): NightLibraryStore =
            instance ?: synchronized(this) {
                instance ?: NightLibraryStore(
                    context = context.applicationContext,
                    repository = NightRepository.get(context.applicationContext),
                ).also { instance = it }
            }

        internal fun normalizedTextName(name: String, markdown: Boolean): String {
            val fallback = if (markdown) "Night note.md" else "Night note.txt"
            val trimmed = name.trim().ifBlank { fallback }.take(120)
            val lower = trimmed.lowercase()
            return when {
                markdown && (lower.endsWith(".md") || lower.endsWith(".markdown")) -> trimmed
                !markdown && lower.endsWith(".txt") -> trimmed
                markdown -> trimmed + ".md"
                else -> trimmed + ".txt"
            }
        }

        internal fun entityFromLegacy(file: NightLibraryFile): NightLibraryItemEntity =
            NightLibraryItemEntity(
                id = file.id,
                name = file.name,
                mimeType = file.mimeType,
                sizeBytes = file.sizeBytes,
                localPath = file.localPath,
                createdAt = file.createdAt,
            )
    }
}
