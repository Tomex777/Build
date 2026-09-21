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
        @Volatile private var instance: NightLibraryStore? = null

        fun get(context: Context): NightLibraryStore =
            instance ?: synchronized(this) {
                instance ?: NightLibraryStore(
                    context = context.applicationContext,
                    repository = NightRepository.get(context.applicationContext),
                ).also { instance = it }
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
