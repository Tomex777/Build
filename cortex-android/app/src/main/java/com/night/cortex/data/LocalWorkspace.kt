package com.night.cortex.data

import android.content.Context
import java.io.File
import java.io.InputStream

class LocalWorkspace(context: Context) {
    private val root = File(context.filesDir, "projects").apply { mkdirs() }

    fun projectDir(name: String = "Night"): File = File(root, sanitize(name)).apply { mkdirs() }

    fun listProjectFiles(name: String = "Night"): List<File> =
        projectDir(name).walkTopDown().filter { it.isFile }.toList()

    fun save(name: String = "Night", relativePath: String, input: InputStream): File {
        val project = projectDir(name).canonicalFile
        val target = File(project, relativePath).canonicalFile
        require(target.path.startsWith(project.path + File.separator)) { "Path escapes project" }
        target.parentFile?.mkdirs()
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        return target
    }

    private fun sanitize(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
