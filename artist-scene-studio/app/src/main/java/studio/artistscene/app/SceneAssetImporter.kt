package studio.artistscene.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import studio.artistscene.core.Actor
import java.io.File
import java.io.InputStream
import java.io.FileOutputStream
import java.util.UUID
import studio.artistscene.core.ActorKind
import studio.artistscene.core.AssetImportPolicy
import studio.artistscene.core.AssetImportValidation

data class ImportedSceneAsset(
    val actor: Actor,
    val byteSize: Long,
)

/**
 * Android SAF intake. Files are staged with a hard size bound, validated, then streamed into the
 * managed library so large imports do not require a second full-size heap allocation.
 */
class SceneAssetImporter(private val context: Context) {
    private val library = ManagedAssetLibrary(context)
    fun import(uri: Uri, kind: ActorKind): Result<ImportedSceneAsset> = runCatching {
        val metadata = queryMetadata(uri)
        val extension = metadata.name.substringAfterLast('.', "model").lowercase()
        val staging = File.createTempFile("mise-import-", ".$extension", context.cacheDir)
        try {
            val byteSize = requireNotNull(context.contentResolver.openInputStream(uri)) {
                "The selected file could not be opened."
            }.use { it.copyBoundedTo(staging, AssetImportPolicy.MAX_BYTES) }

            val accepted = when (
                val validation = AssetImportPolicy.validate(metadata.name, metadata.size, staging)
            ) {
                is AssetImportValidation.Accepted -> validation
                is AssetImportValidation.Rejected -> throw IllegalArgumentException(validation.reason)
            }

            val libraryAsset = library.installFile(
                name = metadata.name,
                category = kind.name.lowercase(),
                format = accepted.format,
                file = staging,
            )

            val actorName = metadata.name
                .substringBeforeLast('.', metadata.name)
                .trim()
                .ifBlank { "Imported model" }
                .take(80)

            val actor = libraryAsset.actor(
                kind = kind,
                actorId = "import-" + UUID.randomUUID().toString().replace("-", "").take(16),
            ).copy(name = actorName)

            ImportedSceneAsset(actor = actor, byteSize = byteSize)
        } finally {
            staging.delete()
        }
    }

    private fun queryMetadata(uri: Uri): FileMetadata {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "model"
        var size: Long? = null
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        if (size != null && size > AssetImportPolicy.MAX_BYTES) {
            throw IllegalArgumentException("Model is larger than the 128 MiB import limit.")
        }
        return FileMetadata(name = name, size = size)
    }

    private data class FileMetadata(val name: String, val size: Long?)
}

private fun InputStream.copyBoundedTo(target: File, maxBytes: Long): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    FileOutputStream(target).use { output ->
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) {
                throw IllegalArgumentException("Model is larger than the 128 MiB import limit.")
            }
            output.write(buffer, 0, count)
        }
        output.fd.sync()
    }
    return total
}
