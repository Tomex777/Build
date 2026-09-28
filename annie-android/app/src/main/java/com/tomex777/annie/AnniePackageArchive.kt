package com.tomex777.annie

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal data class AnniePackageArchivePreview(
    val manifest: AnniePackageManifest,
    val javaScriptFiles: List<String>,
    val imageFiles: List<String>,
    val audioFiles: List<String>,
    val otherFiles: List<String>,
    val expandedBytes: Long,
    val entryCandidates: List<String>,
) {
    val requiresEntrySelection: Boolean get() = manifest.entryPoint.isBlank()
}

/** ZIP validation and package installation. Import only copies data; it never starts QuickJS. */
internal object AnniePackageArchive {
    private const val MAX_ARCHIVE_BYTES = 32L * 1024 * 1024
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 64L * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 16L * 1024 * 1024
    private const val MAX_JAVASCRIPT_BYTES = 2L * 1024 * 1024
    private const val MAX_MANIFEST_BYTES = 64L * 1024
    private const val MAX_FILES = 512
    private const val MAX_COMPRESSION_RATIO = 200L
    private val forbiddenExtensions = setOf(
        "apk", "aab", "dex", "jar", "so", "dll", "exe", "bat", "cmd", "ps1", "sh",
        "py", "mjs", "cjs", "wasm", "class",
    )

    private data class InspectedFile(val entry: ZipEntry, val path: String)

    fun inspect(archive: File, suggestedName: String = archive.nameWithoutExtension): AnniePackageArchivePreview {
        require(archive.isFile && archive.length() in 1..MAX_ARCHIVE_BYTES) { "ZIP archive is missing or too large" }
        ZipFile(archive).use { zip ->
            val files = mutableListOf<InspectedFile>()
            val seen = mutableSetOf<String>()
            val filePaths = mutableSetOf<String>()
            var totalBytes = 0L
            var fileCount = 0
            var entryCount = 0
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                require(++entryCount <= MAX_FILES) { "ZIP contains too many entries" }
                val path = validatePath(entry.name, entry.isDirectory)
                require(seen.add(path)) { "ZIP contains duplicate paths: $path" }
                if (entry.isDirectory) continue
                fileCount++
                require(fileCount <= MAX_FILES) { "ZIP contains too many files" }
                filePaths += path
                require(entry.size in 0..MAX_ENTRY_BYTES) { "ZIP entry is too large: $path" }
                if (path.endsWith(".js", true)) require(entry.size <= MAX_JAVASCRIPT_BYTES) {
                    "JavaScript source is too large: $path"
                }
                require(entry.compressedSize >= 0L) { "ZIP entry size is unknown: $path" }
                if (entry.size > 1_048_576L) {
                    require(entry.compressedSize > 0L && entry.size <= entry.compressedSize * MAX_COMPRESSION_RATIO) {
                        "ZIP entry compression ratio is unsafe: $path"
                    }
                }
                totalBytes += entry.size
                require(totalBytes <= MAX_TOTAL_UNCOMPRESSED_BYTES) { "ZIP expands beyond the package size limit" }
                files += InspectedFile(entry, path)
            }
            require(filePaths.none { filePath ->
                filePaths.any { other -> other.length > filePath.length && other.startsWith("$filePath/") }
            }) { "ZIP file paths conflict with package folders" }

            val scripts = files.map { it.path }.filter { it.endsWith(".js", true) }.sorted()
            require(scripts.isNotEmpty()) { "Package ZIP must contain at least one JavaScript file" }
            val rawManifest = files.firstOrNull { it.path == "manifest.json" }?.let { file ->
                require(file.entry.size <= MAX_MANIFEST_BYTES) { "manifest.json is too large" }
                zip.getInputStream(file.entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            val manifest = if (rawManifest != null) {
                val title = suggestedName.removeSuffix(".zip").ifBlank { "Imported package" }
                decodeManifest(JSONObject(rawManifest), scripts, packageSlug(title), title)
            } else {
                val entry = when {
                    "main.js" in scripts -> "main.js"
                    scripts.size == 1 -> scripts.single()
                    else -> ""
                }
                val title = archive.nameWithoutExtension.ifBlank { "Imported package" }
                AnniePackageManifest(
                    packageId = packageSlug(title),
                    displayName = title,
                    version = "0.0.0-local",
                    apiVersion = AnniePackageManifest.CURRENT_API_VERSION,
                    entryPoint = entry,
                    assets = files.map { it.path }
                        .filter { it !in scripts && it != "manifest.json" }
                        .map { path ->
                            val stem = path.substringBeforeLast('.', path).replace('/', '.')
                            val cleanStem = stem.replace(Regex("[^A-Za-z0-9_.-]"), "_")
                            val startsWithLetter = cleanStem.firstOrNull()?.let { it in 'A'..'Z' || it in 'a'..'z' } == true
                            val logicalId = (if (startsWithLetter) "" else "asset_") + cleanStem.take(if (startsWithLetter) 64 else 58)
                            AnniePackageAsset(logicalId, path, mimeTypeFor(path))
                        },
                    generated = true,
                )
            }
            require(manifest.assets.map { it.logicalId }.distinct().size == manifest.assets.size) {
                "Package assets need unique logical IDs"
            }
            val allPaths = files.mapTo(mutableSetOf()) { it.path }
            require(manifest.assets.all { it.relativePath in allPaths }) { "Package manifest references a missing asset" }
            return AnniePackageArchivePreview(
                manifest = manifest,
                javaScriptFiles = scripts,
                imageFiles = files.map { it.path }.filter { it.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }.sorted(),
                audioFiles = files.map { it.path }.filter { it.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS }.sorted(),
                otherFiles = files.map { it.path }.filter { path ->
                    path != "manifest.json" && path !in scripts &&
                        path.substringAfterLast('.', "").lowercase() !in IMAGE_EXTENSIONS + AUDIO_EXTENSIONS
                }.sorted(),
                expandedBytes = totalBytes,
                entryCandidates = if (manifest.entryPoint.isBlank()) scripts else listOf(manifest.entryPoint),
            )
        }
    }

    fun install(
        context: Context,
        archive: File,
        selectedEntryPoint: String? = null,
        suggestedName: String = archive.nameWithoutExtension,
    ): ScriptProject {
        val preview = inspect(archive, suggestedName)
        val entryPoint = preview.manifest.entryPoint.ifBlank { selectedEntryPoint.orEmpty() }
        require(entryPoint in preview.javaScriptFiles) { "Choose a valid JavaScript entry point" }
        val manifest = preview.manifest.copy(entryPoint = entryPoint)
        val files = ScriptFiles(context)
        val alreadyInstalled = files.listProjects().any { it.manifest.packageId.equals(manifest.packageId, ignoreCase = true) }
        require(!alreadyInstalled) { "A package with ID ${manifest.packageId} is already installed" }

        val localIdBase = packageSlug(manifest.displayName.ifBlank { manifest.packageId })
        var localId = localIdBase
        var suffix = 2
        while (File(files.root, localId).exists() || File(files.root, "$localId.js").exists()) {
            val suffixText = "_$suffix"
            localId = localIdBase.take(48 - suffixText.length) + suffixText
            suffix++
        }
        val stage = File(files.root, ".stage-${UUID.randomUUID()}")
        require(stage.mkdir()) { "Could not create a private package staging folder" }
        var stagedDisabledPreference = false
        var installedDestination: File? = null
        try {
            ZipFile(archive).use { zip ->
                val entries = zip.entries()
                var writtenTotal = 0L
                var entryCount = 0
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    require(++entryCount <= MAX_FILES) { "ZIP contains too many entries" }
                    val path = validatePath(entry.name, entry.isDirectory)
                    val destination = File(stage, path).canonicalFile
                    require(destination.toPath().startsWith(stage.canonicalFile.toPath())) { "ZIP entry escapes the staging folder" }
                    if (entry.isDirectory) {
                        require(destination.mkdirs() || destination.isDirectory) { "Could not create package folder" }
                        continue
                    }
                    destination.parentFile?.let { require(it.mkdirs() || it.isDirectory) { "Could not create package folder" } }
                    var entryBytes = 0L
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(destination).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                entryBytes += read
                                writtenTotal += read
                                require(entryBytes <= MAX_ENTRY_BYTES && writtenTotal <= MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                    "ZIP expanded beyond the package size limit"
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    require(entryBytes == entry.size) { "ZIP entry size does not match its directory: $path" }
                }
            }

            if (!File(stage, "manifest.json").isFile) {
                File(stage, "manifest.json").writeText(encodeManifest(manifest).toString(2))
            } else {
                val stagedManifest = decodeManifest(
                    JSONObject(File(stage, "manifest.json").readText()),
                    preview.javaScriptFiles,
                ).copy(entryPoint = entryPoint)
                require(stagedManifest.packageId == manifest.packageId) { "Package manifest changed during import" }
                File(stage, "manifest.json").writeText(encodeManifest(stagedManifest).toString(2))
            }
            // Disable synchronously before making the package visible to Script Studio/runtime.
            context.applicationContext.getSharedPreferences("annie_script_enabled", Context.MODE_PRIVATE)
                .edit().putBoolean(localId, false).commit()
            stagedDisabledPreference = true
            val destination = File(files.root, localId)
            require(stage.renameTo(destination)) { "Could not install the validated package" }
            installedDestination = destination
            files.recordInstalledPackage(localId, manifest)
            return files.readProject(destination) ?: error("Imported package entry point could not be loaded")
        } catch (failure: Throwable) {
            stage.deleteRecursively()
            installedDestination?.deleteRecursively()
            files.removeInstalledPackageState(localId)
            if (stagedDisabledPreference) {
                context.applicationContext.getSharedPreferences("annie_script_enabled", Context.MODE_PRIVATE)
                    .edit().remove(localId).commit()
            }
            throw failure
        }
    }

    internal fun readManifestIfPresent(
        directory: File,
        fallbackId: String,
        fallbackName: String,
        javascriptPaths: Set<String>,
    ): AnniePackageManifest? {
        val file = File(directory, "manifest.json")
        if (!file.exists()) return null
        require(file.isFile && file.length() <= MAX_MANIFEST_BYTES) { "manifest.json is too large" }
        val manifest = decodeManifest(JSONObject(file.readText()), javascriptPaths, fallbackId, fallbackName)
        require(manifest.assets.all { File(directory, it.relativePath).isFile }) { "Package manifest references a missing asset" }
        return manifest
    }

    internal fun validateArchivePath(path: String, directory: Boolean = false): String = validatePath(path, directory)

    private fun decodeManifest(
        json: JSONObject,
        javascriptPaths: Collection<String>,
        fallbackId: String = "",
        fallbackName: String = "",
    ): AnniePackageManifest {
        val id = json.optString("packageId").ifBlank { json.optString("id") }.ifBlank { fallbackId }
        require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}"))) { "Package manifest has an invalid package ID" }
        val displayName = json.optString("displayName").ifBlank { json.optString("name") }.ifBlank { fallbackName.ifBlank { id } }
        require(displayName.length <= 120) { "Package display name is too long" }
        val version = json.optString("version").ifBlank { "0.0.0-local" }
        require(version.length <= 64) { "Package version is too long" }
        val apiVersion = json.optString("apiVersion").ifBlank { AnniePackageManifest.CURRENT_API_VERSION }
        require(apiVersion == AnniePackageManifest.CURRENT_API_VERSION) { "Package requires unsupported Annie API $apiVersion" }
        val entryPoint = json.optString("entryPoint").ifBlank {
            when {
                "main.js" in javascriptPaths -> "main.js"
                javascriptPaths.size == 1 -> javascriptPaths.single()
                else -> ""
            }
        }
        if (entryPoint.isNotBlank()) {
            validatePath(entryPoint, false)
            require(entryPoint.endsWith(".js", true) && entryPoint in javascriptPaths) {
                "Package entry point is missing: $entryPoint"
            }
        }
        val backgroundName = json.optString("background", "none").uppercase()
        val background = runCatching { AnniePackageBackground.valueOf(backgroundName) }
            .getOrElse { throw IllegalArgumentException("Unknown package background capability: $backgroundName") }
        val commands = manifestArray(json, "commands").objects().map { row ->
            val name = row.optString("name")
            require(name.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,31}"))) { "Package manifest contains an invalid command declaration" }
            AnniePackageCommand(name, row.optString("description").take(256))
        }
        require(commands.size <= 128) { "Package manifest declares too many commands" }
        require(commands.map { it.name.lowercase() }.distinct().size == commands.size) { "Package manifest repeats a command name" }
        val assets = manifestArray(json, "assets").objects().map { row ->
            val path = row.optString("path")
            validatePath(path, false)
            val assetId = row.optString("id")
            require(assetId.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}"))) { "Package manifest contains an invalid asset ID" }
            AnniePackageAsset(assetId, path, row.optString("mimeType").takeIf(String::isNotBlank))
        }
        require(assets.map { it.logicalId }.distinct().size == assets.size) { "Package manifest repeats an asset ID" }
        require(assets.size <= 256) { "Package manifest declares too many assets" }
        val services = manifestArray(json, "services").objects().map { row ->
            val name = row.optString("name")
            val version = row.optString("version")
            val input = row.optString("input")
            val output = row.optString("output")
            val schemaId = Regex("[A-Za-z0-9][A-Za-z0-9_.:/-]{0,127}")
            require(name.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")) &&
                version.matches(Regex("[A-Za-z0-9][A-Za-z0-9.+_-]{0,63}")) &&
                input.matches(schemaId) && output.matches(schemaId)) {
                "Package manifest contains an invalid service declaration"
            }
            AnniePackageService(name, version, input, output)
        }
        require(services.size <= 128) { "Package manifest declares too many services" }
        require(services.map { it.name }.distinct().size == services.size) { "Package manifest repeats a service name" }
        val serviceDependencies = manifestArray(json, "serviceDependencies").objects().map { row ->
            val packageId = row.optString("packageId")
            val name = row.optString("name")
            val version = row.optString("version")
            val input = row.optString("input")
            val output = row.optString("output")
            require(packageId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) &&
                name.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")) &&
                version.matches(Regex("[A-Za-z0-9][A-Za-z0-9.+_-]{0,63}")) &&
                input.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:/-]{0,127}")) &&
                output.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:/-]{0,127}"))) {
                "Package manifest contains an invalid service dependency"
            }
            AnniePackageServiceDependency(packageId, name, version, input, output)
        }
        require(serviceDependencies.size <= 128) { "Package manifest declares too many service dependencies" }
        require(serviceDependencies.map { "${it.packageId}/${it.name}" }.distinct().size == serviceDependencies.size) {
            "Package manifest repeats a service dependency"
        }
        val permissions = stringSet(manifestArray(json, "permissions"))
        require(permissions.size <= 128 && permissions.all { it.matches(Regex("[A-Za-z][A-Za-z0-9_.:/-]{0,255}")) }) {
            "Package manifest contains an invalid permission declaration"
        }
        val capabilities = stringSet(manifestArray(json, "capabilities"))
        require(capabilities.size <= 128 && capabilities.all { it.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")) }) {
            "Package manifest contains an invalid capability declaration"
        }
        val dependencies = manifestObject(json, "dependencies").stringMap()
        require(dependencies.size <= 128 && dependencies.all { (dependencyId, versionSpec) ->
            dependencyId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) &&
                versionSpec.matches(Regex("(?:\\*|[A-Za-z0-9][A-Za-z0-9.+_-]{0,63})"))
        }) { "Package manifest contains an invalid dependency declaration" }
        return AnniePackageManifest(
            packageId = id,
            displayName = displayName,
            version = version,
            apiVersion = apiVersion,
            entryPoint = entryPoint,
            permissions = permissions,
            commands = commands,
            services = services,
            serviceDependencies = serviceDependencies,
            assets = assets,
            background = background,
            dependencies = dependencies,
            capabilities = capabilities,
            generated = json.optBoolean("generated", false),
        )
    }

    private fun encodeManifest(manifest: AnniePackageManifest): JSONObject = JSONObject()
        .put("packageId", manifest.packageId)
        .put("displayName", manifest.displayName)
        .put("version", manifest.version)
        .put("apiVersion", manifest.apiVersion)
        .put("entryPoint", manifest.entryPoint)
        .put("permissions", JSONArray(manifest.permissions.toList()))
        .put("commands", JSONArray().apply { manifest.commands.forEach { put(JSONObject().put("name", it.name).put("description", it.description)) } })
        .put("services", JSONArray().apply { manifest.services.forEach { put(JSONObject().put("name", it.name).put("version", it.version).put("input", it.inputSchema).put("output", it.outputSchema)) } })
        .put("serviceDependencies", JSONArray().apply { manifest.serviceDependencies.forEach {
            put(JSONObject().put("packageId", it.packageId).put("name", it.name).put("version", it.version).put("input", it.inputSchema).put("output", it.outputSchema))
        } })
        .put("assets", JSONArray().apply { manifest.assets.forEach { put(JSONObject().put("id", it.logicalId).put("path", it.relativePath).put("mimeType", it.mimeType ?: JSONObject.NULL)) } })
        .put("background", manifest.background.name.lowercase())
        .put("dependencies", JSONObject().apply { manifest.dependencies.forEach { (key, value) -> put(key, value) } })
        .put("capabilities", JSONArray(manifest.capabilities.toList()))
        .put("generated", manifest.generated)

    private fun validatePath(raw: String, directory: Boolean): String {
        val path = raw.removeSuffix("/")
        require(path.isNotBlank() && path.length <= 240) { "ZIP contains an empty or overlong path" }
        require(!path.startsWith('/') && !path.startsWith('\\') && !path.contains('\\') && !path.contains(':')) {
            "ZIP contains an absolute or invalid path: $raw"
        }
        val parts = path.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." || it.startsWith('.') }) {
            "ZIP contains a hidden or traversing path: $raw"
        }
        require(parts.all {
            it.none { character -> character.code < 0x20 || character.code == 0x7f } &&
                it.matches(Regex("[A-Za-z0-9_-][A-Za-z0-9_. -]{0,63}"))
        }) { "ZIP path contains invalid characters" }
        if (!directory) {
            val extension = path.substringAfterLast('.', "").lowercase()
            require(extension !in forbiddenExtensions) { "ZIP contains a blocked executable file: $raw" }
        }
        return path
    }

    private fun packageSlug(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9_-]+"), "_")
        .trim('_', '-')
        .take(48)
        .ifBlank { "imported" }

    private fun mimeTypeFor(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        "json" -> "application/json"
        "txt", "md" -> "text/plain"
        else -> null
    }

    private fun JSONArray?.objects(): List<JSONObject> = this?.let { array ->
        buildList {
            for (index in 0 until array.length()) {
                add(array.optJSONObject(index) ?: error("Package manifest entries must be objects"))
            }
        }
    }.orEmpty()

    private fun manifestArray(json: JSONObject, name: String): JSONArray? {
        if (!json.has(name) || json.isNull(name)) return null
        return json.optJSONArray(name) ?: error("Package manifest field '$name' must be an array")
    }

    private fun manifestObject(json: JSONObject, name: String): JSONObject? {
        if (!json.has(name) || json.isNull(name)) return null
        return json.optJSONObject(name) ?: error("Package manifest field '$name' must be an object")
    }

    private fun stringSet(array: JSONArray?): Set<String> = buildSet {
        if (array == null) return@buildSet
        require(array.length() <= 128) { "Package manifest declares too many capabilities or permissions" }
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            require(value.matches(Regex("[A-Za-z][A-Za-z0-9_.:/-]{0,255}"))) { "Package manifest contains an invalid declaration" }
            add(value)
        }
    }

    private fun JSONObject?.stringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return buildMap {
            val keys = keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = optString(key)
                require(key.matches(Regex("[A-Za-z0-9_.-]{1,128}")) && value.length <= 256) {
                    "Package manifest contains an invalid dependency"
                }
                put(key, value)
            }
        }
    }

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "svg")
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "aiff", "mid", "midi")
}
