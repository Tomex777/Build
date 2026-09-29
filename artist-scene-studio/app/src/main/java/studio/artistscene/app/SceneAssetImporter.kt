package studio.artistscene.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import studio.artistscene.core.Actor
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import studio.artistscene.core.ActorKind
import studio.artistscene.core.AssetImportPolicy
import studio.artistscene.core.AssetImportValidation

data class ImportedSceneAsset(
    val actor: Actor,
    val byteSize: Long,
)

/**
 * Android SAF intake. Validated bytes are copied into the managed library so scenes survive
 * picker grant revocation, process death, restart, and offline use.
 */
class SceneAssetImporter(private val context: Context) {
    private val library = ManagedAssetLibrary(context)
    fun import(uri: Uri, kind: ActorKind): Result<ImportedSceneAsset> = runCatching {
        val metadata = queryMetadata(uri)
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) {
            "The selected file could not be opened."
        }.use { it.readBounded(AssetImportPolicy.MAX_BYTES) }

        val accepted = when (
            val validation = AssetImportPolicy.validate(metadata.name, metadata.size, bytes)
        ) {
            is AssetImportValidation.Accepted -> validation
            is AssetImportValidation.Rejected -> throw IllegalArgumentException(validation.reason)
        }

        val libraryAsset = library.install(
            name = metadata.name,
            category = kind.name.lowercase(),
            format = accepted.format,
            bytes = bytes,
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

        ImportedSceneAsset(
            actor = actor,
            byteSize = bytes.size.toLong(),
        )
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
        if (size != null && size!! > AssetImportPolicy.MAX_BYTES) {
            throw IllegalArgumentException("Model is larger than the 128 MiB mobile import limit.")
        }
        return FileMetadata(name = name, size = size)
    }

    private data class FileMetadata(val name: String, val size: Long?)
}

private fun InputStream.readBounded(maxBytes: Long): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) {
            throw IllegalArgumentException("Model is larger than the 128 MiB mobile import limit.")
        }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
