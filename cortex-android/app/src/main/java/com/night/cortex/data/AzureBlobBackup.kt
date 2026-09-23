package com.night.cortex.data

import java.net.HttpURLConnection
import java.net.URI
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

data class BlobSyncResult(val uploadedFiles: Int, val unchangedFiles: Int, val bytesUploaded: Long)
data class BlobRestoreResult(
    val restoredFiles: Int,
    val unchangedFiles: Int,
    val bytesDownloaded: Long,
    val conflicts: List<String>,
)

/** Explicit, incremental uploads to a user-owned Azure Blob container SAS URL. */
class AzureBlobBackup(private val workspace: LocalWorkspace) {
    fun sync(containerSasUrl: String, onBackedUp: (String, String) -> Unit): BlobSyncResult {
        val container = validateContainer(containerSasUrl)

        var uploaded = 0
        var unchanged = 0
        var totalBytes = 0L
        val existingManifest = readManifest(container)
        val manifest = existingManifest ?: JSONObject().put("files", JSONArray())
        val knownHashes = manifest.optJSONArray("files")?.let { rows ->
            buildMap {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val path = row.optString("path")
                    val hash = row.optString("sha256")
                    if (path.isNotBlank() && hash.isNotBlank()) put(path, hash)
                }
            }
        }?.toMutableMap() ?: mutableMapOf()
        workspace.listProjectFiles().forEach { file ->
            require(workspace.isTransferable(file = file)) {
                "A file is too large to sync: " + workspace.relativePath(file = file)
            }
            val path = workspace.relativePath(file = file)
            val hash = workspace.sha256(relativePath = path) ?: return@forEach
            if (hash == knownHashes[path]) {
                unchanged++
                return@forEach
            }
            val bytes = file.readBytes()
            put(container, path, bytes, hash)
            knownHashes[path] = hash
            uploaded++
            totalBytes += bytes.size
        }
        if (uploaded > 0 || existingManifest == null) {
            val rows = JSONArray()
            knownHashes.toSortedMap().forEach { (path, hash) ->
                rows.put(JSONObject().put("path", path).put("sha256", hash))
            }
            val manifestBytes = JSONObject()
                .put("project", "Night")
                .put("updatedAt", System.currentTimeMillis())
                .put("files", rows)
                .toString()
                .toByteArray(Charsets.UTF_8)
            put(container, "_cortex_manifest.json", manifestBytes, sha256(manifestBytes))
        }
        knownHashes.forEach(onBackedUp)
        return BlobSyncResult(uploaded, unchanged, totalBytes)
    }

    fun restore(
        containerSasUrl: String,
        currentHash: (String) -> String?,
        previousHash: (String) -> String?,
        onRestored: (String, String) -> Unit,
    ): BlobRestoreResult {
        val container = validateContainer(containerSasUrl)
        val manifest = readManifest(container) ?: error("No Cortex backup manifest exists in this container yet")
        val rows = manifest.optJSONArray("files") ?: JSONArray()
        val remote = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val path = row.optString("path")
                val hash = row.optString("sha256")
                if (path.isNotBlank() && hash.isNotBlank()) add(path to hash)
            }
        }
        val conflicts = remote.mapNotNull { (path, remoteHash) ->
            val localHash = currentHash(path)
            val lastBackupHash = previousHash(path)
            if (localHash != null && localHash != remoteHash && localHash != lastBackupHash) path else null
        }
        if (conflicts.isNotEmpty()) return BlobRestoreResult(0, 0, 0, conflicts)

        var restored = 0
        var unchanged = 0
        var bytes = 0L
        remote.forEach { (path, remoteHash) ->
            if (currentHash(path) == remoteHash) {
                unchanged++
                return@forEach
            }
            val content = getBlob(container, path)
            require(sha256(content) == remoteHash) { "Backup checksum failed for $path" }
            workspace.saveBytes(relativePath = path, bytes = content)
            onRestored(path, remoteHash)
            restored++
            bytes += content.size
        }
        return BlobRestoreResult(restored, unchanged, bytes, emptyList())
    }

    private fun put(container: String, relativePath: String, bytes: ByteArray, sha256: String) {
        val url = blobUrl(container, "Night/$relativePath")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "PUT"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.setRequestProperty("x-ms-blob-type", "BlockBlob")
            connection.setRequestProperty("x-ms-version", "2023-11-03")
            connection.setRequestProperty("x-ms-date", httpDate())
            connection.setRequestProperty("x-ms-meta-cortexsha256", sha256)
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            if (status !in 200..299) {
                connection.errorStream?.close()
                throw IllegalStateException("Azure Blob upload failed (HTTP $status)")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun getBlob(container: String, relativePath: String): ByteArray {
        val connection = blobUrl(container, "Night/$relativePath").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("x-ms-version", "2023-11-03")
            val status = connection.responseCode
            if (status !in 200..299) {
                connection.errorStream?.close()
                throw IllegalStateException("Azure Blob download failed (HTTP $status)")
            }
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= LocalWorkspace.MAX_TRANSFER_BYTES) { "Backup file exceeds the 10 MB limit" }
                    out.write(buffer, 0, count)
                }
            }
            return out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    private fun readManifest(container: String): JSONObject? {
        val connection = blobUrl(container, "Night/_cortex_manifest.json").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("x-ms-version", "2023-11-03")
            return when (val status = connection.responseCode) {
                404 -> null
                in 200..299 -> connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                else -> {
                    connection.errorStream?.close()
                    throw IllegalStateException("Azure Blob manifest request failed (HTTP $status)")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun validateContainer(containerSasUrl: String): String {
        val container = containerSasUrl.trim().removeSuffix("/")
        val uri = URI(container)
        require(uri.scheme == "https" && uri.host?.endsWith(".blob.core.windows.net") == true) {
            "Enter an HTTPS Azure Blob container SAS URL"
        }
        require(Regex("(?:^|&)sig=").containsMatchIn(uri.rawQuery.orEmpty())) { "The container URL must include its SAS signature" }
        require(uri.path.trim('/').isNotBlank() && '/' !in uri.path.trim('/')) {
            "Enter a SAS URL for one Azure Blob container"
        }
        val permissions = Regex("(?:^|&)sp=([^&]+)").find(uri.rawQuery.orEmpty())?.groupValues?.get(1).orEmpty()
        require('r' in permissions && ('w' in permissions || 'c' in permissions)) {
            "The SAS needs read, write and create permission for backup and restore"
        }
        return container
    }

    private fun blobUrl(container: String, path: String): java.net.URL {
        val queryAt = container.indexOf('?')
        val base = if (queryAt >= 0) container.substring(0, queryAt) else container
        val query = if (queryAt >= 0) container.substring(queryAt + 1) else ""
        val blobPath = path.split('/').joinToString("/") { encodeSegment(it) }
        return URI("$base/$blobPath?$query").toURL()
    }

    private fun encodeSegment(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun httpDate(): String = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("GMT") }
        .format(Date())

    private fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
