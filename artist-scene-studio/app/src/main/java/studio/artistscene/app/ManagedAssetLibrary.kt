package studio.artistscene.app

import android.content.Context
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import studio.artistscene.core.Actor
import studio.artistscene.core.ActorKind
import studio.artistscene.core.AssetReference
import studio.artistscene.core.AssetStorage

@Serializable
enum class RigCompatibility {
    UNKNOWN,
    POSEABLE,
    POSEABLE_CUSTOM_RIG,
    STATIC,
    UNSUPPORTED,
}

/** App-private, reusable asset record. Source and license are retained even after import. */
@Serializable
data class LibraryAsset(
    val assetId: String,
    val name: String,
    val category: String,
    val relativePath: String,
    val format: String,
    val source: String? = null,
    val creator: String? = null,
    val license: String? = null,
    val licenseUrl: String? = null,
    val attribution: String? = null,
    val version: String? = null,
    val byteSize: Long,
    val checksumSha256: String,
    val rigCompatibility: RigCompatibility = RigCompatibility.UNKNOWN,
    val boneCount: Int = 0,
    val fingerJointCount: Int = 0,
    val morphTargetCount: Int = 0,
) {
    fun actor(kind: ActorKind, actorId: String): Actor = Actor(
        id = actorId,
        name = name,
        kind = kind,
        asset = AssetReference(
            assetId = assetId,
            relativePath = relativePath,
            format = format,
            source = source,
            creator = creator,
            license = license,
            licenseUrl = licenseUrl,
            attribution = attribution,
            version = version,
            storage = AssetStorage.PROJECT_FILE,
            byteSize = byteSize,
            checksumSha256 = checksumSha256,
        ),
        metadata = mapOf(
            "provenance" to "managed-library",
            "rigCompatibility" to rigCompatibility.name,
        ),
    )
}

/**
 * Durable local library. Payloads are committed before the atomic index update and are always
 * addressed by content hash, so an interrupted import never appears as a ready library asset.
 */
class ManagedAssetLibrary private constructor(
    private val appFilesDir: File,
    rootDirectory: File,
) {
    constructor(context: Context) : this(context.filesDir, File(context.filesDir, "asset-library"))
    internal constructor(filesDirectory: File, directoryName: String) : this(
        filesDirectory,
        File(filesDirectory, directoryName.also { require(it.matches(Regex("[A-Za-z0-9_-]{1,64}"))) }),
    )

    private val root = rootDirectory.apply { mkdirs() }
    private val payloads = File(root, "payloads").apply { mkdirs() }
    private val index = File(root, "library.json")
    private val pendingIndex = File(root, "library.pending")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun list(): List<LibraryAsset> = if (!index.isFile) emptyList() else runCatching {
        json.decodeFromString(ListSerializer(LibraryAsset.serializer()), index.readText())
    }.getOrDefault(emptyList()).filter { File(appFilesDir, it.relativePath).isFile }
        .sortedBy { it.name.lowercase() }

    @Synchronized
    fun install(
        name: String,
        category: String,
        format: String,
        bytes: ByteArray,
        source: String = "Imported file",
        creator: String? = null,
        license: String? = null,
        licenseUrl: String? = null,
        attribution: String? = null,
        version: String? = null,
    ): LibraryAsset = installPayload(
        name = name,
        category = category,
        format = format,
        byteSize = bytes.size.toLong(),
        source = source,
        creator = creator,
        license = license,
        licenseUrl = licenseUrl,
        attribution = attribution,
        version = version,
    ) { output, digest -> output.write(bytes); digest.update(bytes) }

    @Synchronized
    fun installFile(
        name: String,
        category: String,
        format: String,
        file: File,
        source: String = "Imported file",
        creator: String? = null,
        license: String? = null,
        licenseUrl: String? = null,
        attribution: String? = null,
        version: String? = null,
    ): LibraryAsset {
        require(file.isFile && file.length() > 0)
        return installPayload(
            name = name,
            category = category,
            format = format,
            byteSize = file.length(),
            source = source,
            creator = creator,
            license = license,
            licenseUrl = licenseUrl,
            attribution = attribution,
            version = version,
        ) { output, digest -> file.inputStream().use { it.copyDigestingTo(output, digest) } }
    }

    private fun installPayload(
        name: String,
        category: String,
        format: String,
        byteSize: Long,
        source: String,
        creator: String?,
        license: String?,
        licenseUrl: String?,
        attribution: String?,
        version: String?,
        writePayload: (java.io.FileOutputStream, MessageDigest) -> Unit,
    ): LibraryAsset {
        require(format == "glb" || format == "gltf")
        require(byteSize > 0)
        val staging = File.createTempFile("install-", ".pending", payloads)
        val checksum = try {
            staging.outputStream().use { output ->
                val digest = MessageDigest.getInstance("SHA-256")
                writePayload(output, digest)
                output.fd.sync()
                digest.digest().toHex()
            }
        } catch (error: Exception) {
            staging.delete()
            throw error
        }
        val fileName = "$checksum.$format"
        val target = File(payloads, fileName)
        if (!target.exists()) {
            try {
                moveAtomically(staging, target, replace = false)
            } catch (error: Exception) {
                staging.delete()
                if (!target.exists()) throw error
            }
        } else {
            staging.delete()
        }
        val current = list()
        val assetId = "local.$checksum"
        val record = current.firstOrNull { it.assetId == assetId } ?: LibraryAsset(
            assetId = assetId,
            name = name.substringBeforeLast('.', name).trim().take(80).ifBlank { "Imported model" },
            category = category,
            relativePath = "${root.name}/payloads/$fileName",
            format = format,
            source = source,
            creator = creator,
            license = license,
            licenseUrl = licenseUrl,
            attribution = attribution,
            version = version,
            byteSize = byteSize,
            checksumSha256 = checksum,
        )
        saveIndex((current.filterNot { it.assetId == record.assetId } + record).sortedBy { it.name.lowercase() })
        return record
    }

    @Synchronized
    fun updateRig(
        assetId: String,
        compatibility: RigCompatibility,
        boneCount: Int,
        fingerJointCount: Int = 0,
        morphTargetCount: Int = 0,
    ) {
        val current = list()
        val updated = current.map { asset ->
            if (asset.assetId == assetId) asset.copy(
                rigCompatibility = compatibility,
                boneCount = boneCount.coerceAtLeast(0),
                fingerJointCount = fingerJointCount.coerceAtLeast(0),
                morphTargetCount = morphTargetCount.coerceAtLeast(0),
            ) else asset
        }
        if (updated != current) saveIndex(updated)
    }

    @Synchronized
    fun delete(assetId: String): Boolean {
        val current = list()
        val asset = current.firstOrNull { it.assetId == assetId } ?: return false
        saveIndex(current.filterNot { it.assetId == assetId })
        val stillUsed = list().any { it.relativePath == asset.relativePath }
        if (!stillUsed) File(appFilesDir, asset.relativePath).delete()
        return true
    }

    private fun moveAtomically(source: File, target: File, replace: Boolean) {
        val options = if (replace) arrayOf(ATOMIC_MOVE, REPLACE_EXISTING) else arrayOf(ATOMIC_MOVE)
        try {
            Files.move(source.toPath(), target.toPath(), *options)
        } catch (unsupported: java.nio.file.AtomicMoveNotSupportedException) {
            val fallback = if (replace) arrayOf(REPLACE_EXISTING) else emptyArray()
            Files.move(source.toPath(), target.toPath(), *fallback)
        }
    }

    private fun saveIndex(assets: List<LibraryAsset>) {
        pendingIndex.writeText(json.encodeToString(ListSerializer(LibraryAsset.serializer()), assets))
        try {
            moveAtomically(pendingIndex, index, replace = true)
        } catch (error: Exception) {
            pendingIndex.delete()
            throw error
        }
    }
}

private fun InputStream.copyDigestingTo(output: java.io.OutputStream, digest: MessageDigest) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
        digest.update(buffer, 0, count)
    }
}

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
