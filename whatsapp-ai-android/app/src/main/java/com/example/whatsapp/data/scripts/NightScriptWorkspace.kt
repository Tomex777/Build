package com.example.whatsapp.data.scripts

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

enum class NightWorkspaceArea {
    Scripts,
    Projects,
}

data class NightWorkspaceFile(
    val area: NightWorkspaceArea,
    val relativePath: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
)

data class NightWorkspaceProject(
    val name: String,
    val relativePath: String,
    val modifiedAt: Long,
)

class NightScriptWorkspace private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val workspaceRoot = File(appContext.filesDir, "night_workspace")
    private val scriptsRoot = File(workspaceRoot, "scripts")
    private val projectsRoot = File(workspaceRoot, "projects")
    private val scriptDataRoot = File(workspaceRoot, "script-data")

    init {
        ensureRoots()
    }

    fun scriptsDirectory(): File = scriptsRoot

    fun projectsDirectory(): File = projectsRoot

    fun scriptDataDirectory(): File = scriptDataRoot

    fun listScripts(): List<NightWorkspaceFile> {
        ensureRoots()
        return scriptsRoot
            .walkTopDown()
            .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in SCRIPT_EXTENSIONS }
            .mapNotNull { file ->
                toWorkspaceFile(
                    area = NightWorkspaceArea.Scripts,
                    root = scriptsRoot,
                    file = file,
                )
            }
            .sortedBy { it.relativePath.lowercase(Locale.ROOT) }
            .toList()
    }

    fun listProjects(): List<NightWorkspaceProject> {
        ensureRoots()
        return projectsRoot
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .map {
                NightWorkspaceProject(
                    name = it.name,
                    relativePath = it.name,
                    modifiedAt = it.lastModified(),
                )
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun listProjectFiles(projectRelativePath: String): List<NightWorkspaceFile> {
        val project = safeResolve(projectsRoot, projectRelativePath)
        if (!project.isDirectory) return emptyList()

        return project
            .walkTopDown()
            .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in TEXT_PROJECT_EXTENSIONS }
            .mapNotNull { file ->
                toWorkspaceFile(
                    area = NightWorkspaceArea.Projects,
                    root = projectsRoot,
                    file = file,
                )
            }
            .sortedBy { it.relativePath.lowercase(Locale.ROOT) }
            .toList()
    }

    fun createScript(
        preferredName: String = "New script.js",
        initialContent: String = DEFAULT_SCRIPT,
    ): NightWorkspaceFile {
        ensureRoots()
        val file = uniqueFile(
            parent = scriptsRoot,
            preferredName = normalizedScriptName(preferredName),
        )
        file.writeText(initialContent, Charsets.UTF_8)
        return requireNotNull(
            toWorkspaceFile(
                area = NightWorkspaceArea.Scripts,
                root = scriptsRoot,
                file = file,
            )
        )
    }

    fun createScriptFolder(preferredName: String = "Commands"): File {
        ensureRoots()
        val base = sanitizeSegment(preferredName).ifBlank { "Commands" }
        var candidate = File(scriptsRoot, base)
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(scriptsRoot, "$base $suffix")
            suffix++
        }
        candidate.mkdirs()
        return candidate
    }

    fun createProject(preferredName: String = "New project"): NightWorkspaceProject {
        ensureRoots()
        val base = sanitizeSegment(preferredName).ifBlank { "New project" }
        var folder = File(projectsRoot, base)
        var suffix = 2
        while (folder.exists()) {
            folder = File(projectsRoot, "$base $suffix")
            suffix++
        }
        folder.mkdirs()

        File(folder, "index.html").writeText(DEFAULT_HTML, Charsets.UTF_8)
        File(folder, "styles.css").writeText(DEFAULT_CSS, Charsets.UTF_8)
        File(folder, "app.js").writeText(DEFAULT_PROJECT_JS, Charsets.UTF_8)

        return NightWorkspaceProject(
            name = folder.name,
            relativePath = folder.name,
            modifiedAt = folder.lastModified(),
        )
    }

    fun importScript(source: Uri): NightWorkspaceFile? {
        ensureRoots()
        val resolver = appContext.contentResolver
        var displayName = "Imported script.js"

        resolver.query(
            source,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    displayName = cursor.getString(index)?.takeIf { it.isNotBlank() } ?: displayName
                }
            }
        }

        val target = uniqueFile(
            parent = scriptsRoot,
            preferredName = normalizedScriptName(displayName),
        )

        return runCatching {
            resolver.openInputStream(source)?.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            toWorkspaceFile(
                area = NightWorkspaceArea.Scripts,
                root = scriptsRoot,
                file = target,
            )
        }.getOrNull()
    }

    fun exportFile(
        file: NightWorkspaceFile,
        target: Uri,
    ): Boolean {
        val source = resolve(file)
        if (!source.isFile) return false

        return runCatching {
            appContext.contentResolver.openOutputStream(target, "w")?.use { output ->
                source.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: return false
            true
        }.getOrDefault(false)
    }

    fun readFile(file: NightWorkspaceFile): String =
        resolve(file).takeIf { it.isFile }?.readText(Charsets.UTF_8).orEmpty()

    fun writeFile(
        file: NightWorkspaceFile,
        text: String,
    ): NightWorkspaceFile {
        require(text.length <= MAX_TEXT_CHARS) {
            "Workspace files are limited to $MAX_TEXT_CHARS characters."
        }
        val target = resolve(file)
        target.parentFile?.mkdirs()
        target.writeText(text, Charsets.UTF_8)
        return requireNotNull(
            toWorkspaceFile(
                area = file.area,
                root = rootFor(file.area),
                file = target,
            )
        )
    }

    fun renameFile(
        file: NightWorkspaceFile,
        preferredName: String,
    ): NightWorkspaceFile {
        val current = resolve(file)
        require(current.isFile) { "Workspace file does not exist." }

        val normalized =
            if (file.area == NightWorkspaceArea.Scripts) {
                normalizedScriptName(preferredName)
            } else {
                sanitizeFileName(preferredName).ifBlank { current.name }
            }

        if (normalized == current.name) return file

        val target = uniqueFile(
            parent = requireNotNull(current.parentFile),
            preferredName = normalized,
        )
        require(current.renameTo(target)) { "Could not rename this workspace file." }

        return requireNotNull(
            toWorkspaceFile(
                area = file.area,
                root = rootFor(file.area),
                file = target,
            )
        )
    }

    fun deleteFile(file: NightWorkspaceFile): Boolean =
        resolve(file).takeIf { it.isFile }?.delete() == true

    private fun resolve(file: NightWorkspaceFile): File =
        safeResolve(rootFor(file.area), file.relativePath)

    private fun rootFor(area: NightWorkspaceArea): File =
        when (area) {
            NightWorkspaceArea.Scripts -> scriptsRoot
            NightWorkspaceArea.Projects -> projectsRoot
        }

    private fun ensureRoots() {
        workspaceRoot.mkdirs()
        scriptsRoot.mkdirs()
        projectsRoot.mkdirs()
        scriptDataRoot.mkdirs()
    }

    private fun toWorkspaceFile(
        area: NightWorkspaceArea,
        root: File,
        file: File,
    ): NightWorkspaceFile? =
        runCatching {
            val relative = file.canonicalFile.relativeTo(root.canonicalFile).invariantSeparatorsPath
            NightWorkspaceFile(
                area = area,
                relativePath = relative,
                name = file.name,
                sizeBytes = file.length(),
                modifiedAt = file.lastModified(),
            )
        }.getOrNull()

    private fun safeResolve(
        root: File,
        relativePath: String,
    ): File {
        val canonicalRoot = root.canonicalFile
        val candidate = File(canonicalRoot, relativePath).canonicalFile
        require(
            candidate.path == canonicalRoot.path ||
                candidate.path.startsWith(canonicalRoot.path + File.separator)
        ) {
            "Workspace path escapes its sandbox."
        }
        return candidate
    }

    private fun uniqueFile(
        parent: File,
        preferredName: String,
    ): File {
        parent.mkdirs()
        val clean = sanitizeFileName(preferredName).ifBlank { "file" }
        var candidate = File(parent, clean)
        if (!candidate.exists()) return candidate

        val extension = clean.substringAfterLast('.', "")
        val stem =
            if (extension.isBlank()) {
                clean
            } else {
                clean.removeSuffix(".$extension")
            }

        var suffix = 2
        while (candidate.exists()) {
            candidate =
                File(
                    parent,
                    if (extension.isBlank()) {
                        "$stem $suffix"
                    } else {
                        "$stem $suffix.$extension"
                    },
                )
            suffix++
        }
        return candidate
    }

    private fun normalizedScriptName(name: String): String {
        val clean = sanitizeFileName(name).ifBlank { "New script.js" }
        val lower = clean.lowercase(Locale.ROOT)
        return if (lower.endsWith(".js") || lower.endsWith(".mjs")) {
            clean
        } else {
            "$clean.js"
        }
    }

    private fun sanitizeFileName(value: String): String =
        value
            .trim()
            .replace(Regex("[\\/:*?\"<>|]+"), "_")
            .take(120)

    private fun sanitizeSegment(value: String): String =
        sanitizeFileName(value)
            .replace("..", "_")
            .trim('.')

    companion object {
        private val SCRIPT_EXTENSIONS = setOf("js", "mjs")
        private val TEXT_PROJECT_EXTENSIONS =
            setOf("html", "htm", "css", "js", "mjs", "json", "txt", "md")
        private const val MAX_TEXT_CHARS = 1_000_000

        private val DEFAULT_SCRIPT =
            """
            // Night script — plain JavaScript, no Node required.
            //
            // Night exposes only its sandboxed APIs. Scripts do not receive Android,
            // Java or unrestricted filesystem access.
            night.command({
              name: "hello",
              aliases: ["hi"],
              description: "Say hello from a local Night command",
              run(ctx) {
                night.reply("Hey from Night Scripts 👋");
              }
            });

            """.trimIndent()

        private val DEFAULT_HTML =
            """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Night project</title>
              <link rel="stylesheet" href="styles.css">
            </head>
            <body>
              <main>
                <h1>Night project</h1>
              </main>
              <script src="app.js"></script>
            </body>
            </html>
            """.trimIndent()

        private val DEFAULT_CSS =
            """
            :root { color-scheme: dark; }
            body {
              margin: 0;
              padding: 24px;
              background: #0b0f11;
              color: #e7eaec;
              font-family: system-ui, sans-serif;
            }
            """.trimIndent()

        private val DEFAULT_PROJECT_JS =
            """
            console.log("Night project ready");
            """.trimIndent()

        @Volatile private var instance: NightScriptWorkspace? = null

        fun get(context: Context): NightScriptWorkspace =
            instance ?: synchronized(this) {
                instance ?: NightScriptWorkspace(context.applicationContext).also { instance = it }
            }
    }
}
