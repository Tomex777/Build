package com.night.cortex.data

import android.content.Context
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class WorkspaceFile(
    val path: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
)

class LocalWorkspace(context: Context) {
    private val root = File(context.filesDir, "projects").apply { mkdirs() }
    private val ignoredSegments = setOf("node_modules", ".git", ".ssh", ".gradle")
    private val protectedFiles = setOf("id_rsa", "id_ed25519", "id_ecdsa", "id_dsa")

    fun projectDir(name: String = "Night"): File = File(root, sanitize(name)).apply { mkdirs() }

    fun listProjectFiles(name: String = "Night"): List<File> {
        val project = projectDir(name)
        return project.walkTopDown()
            .onEnter { directory -> !isIgnored(relativePath(name, directory).split('/')) }
            .filter { file ->
                file.isFile && !isIgnored(relativePath(name, file).split('/'))
            }
            .sortedBy { relativePath(name, it) }
            .toList()
    }

    fun files(name: String = "Night"): List<WorkspaceFile> = listProjectFiles(name).map {
        WorkspaceFile(relativePath(name, it), it.length(), it.lastModified())
    }

    fun save(name: String = "Night", relativePath: String, input: InputStream): File {
        val target = resolve(name, relativePath)
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.cortex-${System.nanoTime()}.tmp")
        try {
            input.use { source ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_TRANSFER_BYTES) { "Files over 10 MB are not supported in this version" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
        return target
    }

    fun saveBytes(name: String = "Night", relativePath: String, bytes: ByteArray): File {
        val target = resolve(name, relativePath)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        return target
    }

    fun saveText(name: String = "Night", relativePath: String, content: String): File {
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_EDITABLE_BYTES) { "Text files must be smaller than 2 MB" }
        return saveBytes(name, relativePath, content.toByteArray(Charsets.UTF_8))
    }

    fun readText(name: String = "Night", relativePath: String): String? {
        val target = resolve(name, relativePath)
        if (!target.isFile || target.length() > MAX_EDITABLE_BYTES) return null
        return target.readText(Charsets.UTF_8)
    }

    fun readBytes(name: String = "Night", relativePath: String): ByteArray {
        val target = resolve(name, relativePath)
        require(target.isFile) { "Not a file" }
        require(target.length() <= MAX_TRANSFER_BYTES) { "Files over 10 MB are not supported in this version" }
        return target.readBytes()
    }

    fun delete(name: String = "Night", relativePath: String) {
        val target = resolve(name, relativePath)
        require(target.isFile) { "Only files can be deleted" }
        check(target.delete()) { "Could not delete file" }
    }

    fun exists(name: String = "Night", relativePath: String): Boolean = resolve(name, relativePath).exists()

    fun sha256(name: String = "Night", relativePath: String): String? {
        val target = resolve(name, relativePath)
        if (!target.isFile) return null
        val digest = MessageDigest.getInstance("SHA-256")
        target.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun isTransferable(name: String = "Night", file: File): Boolean {
        val path = relativePath(name, file)
        return !isIgnored(path.split('/')) && file.length() <= MAX_TRANSFER_BYTES
    }

    /** Extracts a zip into the local project, rejects zip-slip paths, and skips server-only folders. */
    fun importZip(name: String = "Night", input: InputStream): Int {
        var filesWritten = 0
        var expanded = 0L
        ZipInputStream(input).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                require(filesWritten < MAX_ARCHIVE_FILES) { "Archive contains too many files" }
                val rawPath = entry.name.replace('\\', '/')
                if (!entry.isDirectory && !isIgnored(rawPath.split('/'))) {
                    val target = resolve(name, rawPath)
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            expanded += count
                            require(expanded <= MAX_ARCHIVE_BYTES) { "Archive expands beyond the 100 MB limit" }
                            out.write(buffer, 0, count)
                        }
                    }
                    filesWritten++
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return filesWritten
    }

    fun relativePath(name: String = "Night", file: File): String {
        val project = projectDir(name).canonicalFile
        val canonical = file.canonicalFile
        require(canonical.path.startsWith(project.path + File.separator) || canonical == project) { "File is outside project" }
        return canonical.relativeTo(project).invariantSeparatorsPath
    }

    private fun resolve(name: String, relativePath: String): File {
        val project = projectDir(name).canonicalFile
        val clean = relativePath.replace('\\', '/').removePrefix("/")
        require(clean.isNotBlank() && !clean.startsWith("../") && !clean.contains("/../")) { "Invalid project path" }
        require(clean != "_cortex_manifest.json") { "This filename is reserved for Cortex backup metadata" }
        val target = File(project, clean).canonicalFile
        require(target.path.startsWith(project.path + File.separator) || target == project) { "Path escapes project" }
        require(!isIgnored(clean.split('/'))) { "This file is managed only on the server" }
        return target
    }

    private fun isIgnored(segments: List<String>): Boolean =
        segments.any { segment ->
            segment in ignoredSegments || segment in protectedFiles ||
                segment.startsWith(".env") || segment.endsWith(".pem", true) ||
                segment.endsWith(".p12", true) || segment.endsWith(".pfx", true) ||
                segment.endsWith(".key", true) || segment.endsWith(".keystore", true)
        }

    private fun sanitize(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        const val MAX_EDITABLE_BYTES = 2 * 1024 * 1024
        const val MAX_TRANSFER_BYTES = 10 * 1024 * 1024
        private const val MAX_ARCHIVE_FILES = 1000
        private const val MAX_ARCHIVE_BYTES = 100L * 1024L * 1024L
    }
}
