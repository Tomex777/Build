package studio.artistscene.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import studio.artistscene.core.Actor
import studio.artistscene.core.ActorKind
import studio.artistscene.core.AssetImportPolicy
import studio.artistscene.core.AssetImportValidation
import studio.artistscene.core.AssetReference
import studio.artistscene.core.AssetStorage

data class ImportedSceneAsset(
    val actor: Actor,
    val persistedWithSaf: Boolean,
    val byteSize: Long,
)

/**
 * Android SAF intake. The URI is persisted when the provider supports it; otherwise the already
 * validated bytes are copied into app-private storage so reopening a SceneProject never depends on
 * a transient picker grant.
 */
class SceneAssetImporter(private val context: Context) {
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

        val checksum = bytes.sha256()
        val persistable = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.isSuccess

        val storage: AssetStorage
        val relativePath: String
        val persistedUri: String?
        if (persistable) {
            storage = AssetStorage.PERSISTED_URI
            relativePath = metadata.name
            persistedUri = uri.toString()
        } else {
            val importDirectory = File(context.filesDir, "imports").apply { mkdirs() }
            val target = File(importDirectory, checksum.take(20) + "." + accepted.format)
            if (!target.exists()) {
                val pending = File(importDirectory, target.name + ".pending")
                pending.writeBytes(bytes)
                check(pending.renameTo(target)) { "Could not store the imported model." }
            }
            storage = AssetStorage.PROJECT_FILE
            relativePath = "imports/" + target.name
            persistedUri = null
        }

        val actorName = metadata.name
            .substringBeforeLast('.', metadata.name)
            .trim()
            .ifBlank { "Imported model" }
            .take(80)

        val actor = Actor(
            id = "import-" + UUID.randomUUID().toString().replace("-", "").take(16),
            name = actorName,
            kind = kind,
            asset = AssetReference(
                assetId = "user." + checksum.take(24),
                relativePath = relativePath,
                format = accepted.format,
                source = "User supplied",
                storage = storage,
                persistedUri = persistedUri,
                byteSize = bytes.size.toLong(),
                checksumSha256 = checksum,
            ),
            metadata = mapOf("provenance" to "user-supplied"),
        )
        ImportedSceneAsset(
            actor = actor,
            persistedWithSaf = persistable,
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

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
