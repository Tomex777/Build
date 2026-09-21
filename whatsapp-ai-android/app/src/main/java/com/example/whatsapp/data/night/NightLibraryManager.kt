package com.example.whatsapp.data.night

import android.content.Context
import android.net.Uri
import com.example.whatsapp.data.NightFileLibrary
import com.example.whatsapp.data.NightLibraryFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NightLibraryManager private constructor(
    context: Context,
    private val repository: NightRepository,
) {
    private val appContext = context.applicationContext

    suspend fun syncFromDisk(): List<NightLibraryItemEntity> =
        withContext(Dispatchers.IO) {
            val diskItems = NightFileLibrary.list(appContext)
            val diskById = diskItems.associateBy { it.id }

            diskItems.forEach { file ->
                repository.addLibraryItem(file.toEntity())
            }

            repository.getLibraryItems()
                .filter { item ->
                    !File(item.localPath).exists() &&
                        item.id !in diskById
                }
                .forEach { stale ->
                    repository.deleteLibraryItem(stale.id)
                }

            repository.getLibraryItems()
        }

    suspend fun importUris(
        sources: List<Uri>,
    ): List<NightLibraryItemEntity> = withContext(Dispatchers.IO) {
        sources.mapNotNull { source ->
            NightFileLibrary.importUri(appContext, source)
        }.map { file ->
            file.toEntity().also { repository.addLibraryItem(it) }
        }
    }

    suspend fun saveText(
        name: String,
        text: String,
        mimeType: String = "text/plain",
    ): NightLibraryItemEntity = withContext(Dispatchers.IO) {
        val safeName = sanitizeFileName(name)
        require(text.isNotBlank()) { "File text cannot be empty." }

        val temp = File.createTempFile(
            "night_library_",
            "_" + safeName,
            appContext.cacheDir,
        )
        try {
            temp.writeText(text)
            val saved = NightFileLibrary.registerLocalFile(
                context = appContext,
                source = temp,
                name = safeName,
                mimeType = mimeType.ifBlank { "text/plain" },
            ) ?: error("Night could not save the Library file.")

            saved.toEntity().also { repository.addLibraryItem(it) }
        } finally {
            temp.delete()
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        NightFileLibrary.remove(appContext, id)
        repository.deleteLibraryItem(id)
    }

    private fun sanitizeFileName(raw: String): String {
        val trimmed = raw.trim()
            .replace(Regex("[\\/]+"), "_")
            .replace(Regex("[\\u0000-\\u001F]+"), "")
            .take(120)
            .ifBlank { "Night note.txt" }

        return if ('.' in trimmed) trimmed else trimmed + ".txt"
    }

    private fun NightLibraryFile.toEntity(): NightLibraryItemEntity =
        NightLibraryItemEntity(
            id = id,
            name = name,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            localPath = localPath,
            createdAt = createdAt,
        )

    companion object {
        @Volatile private var instance: NightLibraryManager? = null

        fun get(context: Context): NightLibraryManager =
            instance ?: synchronized(this) {
                val app = context.applicationContext
                instance ?: NightLibraryManager(
                    context = app,
                    repository = NightRepository.get(app),
                ).also { instance = it }
            }
    }
}
