package com.night.cortex.data

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

class LocalWorkspace(context: Context) {
    private val root = File(context.filesDir, "projects").apply { mkdirs() }

    fun projectDir(name: String = "Night"): File = File(root, sanitize(name)).apply { mkdirs() }

    fun listProjectFiles(name: String = "Night"): List<File> =
        projectDir(name).walkTopDown().filter { it.isFile }.toList()

    fun save(name: String = "Night", relativePath: String, input: InputStream): File {
        val target = resolve(name, relativePath)
        target.parentFile?.mkdirs()
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        return target
    }

    fun saveText(name: String = "Night", relativePath: String, content: String): File =
        save(name, relativePath, ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)))

    fun readText(name: String = "Night", relativePath: String): String? {
        val target = resolve(name, relativePath)
        if (!target.isFile) return null
        return target.readText(Charsets.UTF_8)
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

    fun relativePath(name: String = "Night", file: File): String {
        val project = projectDir(name).canonicalFile
        val canonical = file.canonicalFile
        require(canonical.path.startsWith(project.path + File.separator) || canonical == project) { "File is outside project" }
        return canonical.relativeTo(project).invariantSeparatorsPath
    }

    private fun resolve(name: String, relativePath: String): File {
        val project = projectDir(name).canonicalFile
        val clean = relativePath.replace('\\', '/').removePrefix("/")
        val target = File(project, clean).canonicalFile
        require(target.path.startsWith(project.path + File.separator) || target == project) { "Path escapes project" }
        return target
    }

    private fun sanitize(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
