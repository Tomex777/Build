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
