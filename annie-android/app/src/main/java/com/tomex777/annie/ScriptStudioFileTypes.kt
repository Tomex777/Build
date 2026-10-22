package com.tomex777.annie

internal val StudioFileTypes = listOf("js", "html", "css", "json", "md", "txt", "ts", "py", "lua")

internal fun studioDefaultFileType(path: String?): String =
    path?.substringAfterLast('.', "")?.lowercase()?.takeIf { it in StudioFileTypes } ?: "js"

/** Explicit extensions win; the type selector applies only to extensionless names. */
internal fun studioNewFileName(input: String, selectedType: String): String {
    val name = input.trim()
    require(name.isNotBlank() && name.substringAfterLast('/').isNotBlank()) { "Enter a filename" }
    require(selectedType in StudioFileTypes) { "Choose a supported file type" }
    return if (name.substringAfterLast('/').contains('.')) name else "$name.$selectedType"
}


internal fun studioReadTextImport(input: java.io.InputStream): String {
    val limit = 2 * 1024 * 1024
    val output = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    while (true) {
        val size = input.read(chunk)
        if (size < 0) break
        if (size == 0) continue
        require(output.size() + size <= limit) { "Document is too large (2 MB maximum)" }
        output.write(chunk, 0, size)
    }
    return output.toString(Charsets.UTF_8.name())
}

/**
 * Import a text document without running untrusted code. Every imported project
 * starts disabled, even when its source language has an executable runtime.
 * Native Studio edits do not silently change the source file's extension.
 */
internal fun studioImportTextDocument(
    files: ScriptFiles,
    displayName: String,
    source: String,
): Pair<String, String> {
    val leaf = displayName.substringAfterLast('/').substringAfterLast('\\').trim()
    val extension = leaf.substringAfterLast('.', "").lowercase()
    require(extension in StudioFileTypes) {
        "Choose a supported code document, or use ZIP to import a package"
    }
    require(source.length <= ScriptFiles.MAX_SOURCE_CHARS) {
        "Document is too large for the editor (512,000 characters maximum)"
    }
    if (extension == "js") {
        val imported = files.importJavaScript(leaf, source)
        return imported.nameWithoutExtension to imported.name
    }

    val normalizedBase = leaf.substringBeforeLast('.')
        .replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').take(36).ifBlank { "imported" }
    // Include unfinished/empty directories as well as loaded projects.
    // An unindexed folder must not turn a safe duplicate import into a failure.
    val existing = files.listProjects().map { it.id }.toMutableSet()
    files.root.listFiles().orEmpty().forEach { child ->
        existing += if (child.isDirectory) child.name else child.nameWithoutExtension
    }
    var candidate = normalizedBase
    var suffix = 2
    while (candidate in existing) {
        val tail = "_$suffix"
        candidate = normalizedBase.take(48 - tail.length) + tail
        suffix++
    }

    val executable = extension in setOf("ts", "py", "lua")
    val fileName = if (executable) "$candidate.$extension" else {
        val stem = leaf.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9_.-]"), "_").trim('.', '_')
            .take(63 - extension.length).ifBlank { "document" }
        "$stem.$extension"
    }
    val root = if (executable) files.createImportedExecutableFile(fileName)
        else files.createDocumentProject(candidate)
    val projectId = if (executable) root.nameWithoutExtension else root.name
    val path = fileName
    try {
        // Both imported executable files and document folders are already
        // disabled on disk. A runtime reload can never execute staged code.
        if (!executable) files.createFile(projectId, path)
        files.writeFile(projectId, path, source)
    } catch (failure: Throwable) {
        // Remove only the project created by this import. Do not retain an
        // enabled starter main.js or a partially imported script on failure.
        runCatching { files.deleteProject(projectId) }
        throw failure
    }
    return projectId to path
}
