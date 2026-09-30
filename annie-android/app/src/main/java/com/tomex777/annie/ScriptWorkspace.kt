package com.tomex777.annie

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.dokar.quickjs.ModuleContent
import com.dokar.quickjs.ModuleLoader
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import java.util.Locale
import kotlin.coroutines.resume

internal data class ScriptProject(
    val id: String,
    val name: String,
    val entryPath: String,
    val files: Map<String, String>,
    val enabled: Boolean = true,
    private val importedManifest: AnniePackageManifest? = null,
) {
    /** Compatibility projects already participate in the same package model as future imports. */
    val manifest: AnniePackageManifest = importedManifest
        ?: AnniePackageManifest.forExistingProject(id, name, entryPath)
    val hasPackageManifest: Boolean get() = importedManifest != null
}

internal data class ScriptCommand(
    val scriptId: String,
    val name: String,
    val aliases: List<String>,
    val description: String,
    val usage: String,
    val keywords: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
    val suggestedActions: List<ScriptSuggestedAction> = emptyList(),
    val packageDisplayName: String? = null,
    val sourceDisplayName: String? = null,
)

internal data class ScriptLog(
    val atMillis: Long,
    val scriptId: String,
    val level: String,
    val message: String,
)

internal data class ScriptDispatch(
    val scriptId: String,
    val channel: String,
    val resultJson: String,
)

private data class ActiveScriptSession(
    val scriptId: String,
    val sessionName: String,
)

/** Private, app-local JavaScript project files. No script path can escape this workspace. */
internal class ScriptFiles(context: Context) {
    private val appContext = context.applicationContext
    private val enabledPrefs = appContext.getSharedPreferences("annie_script_enabled", Context.MODE_PRIVATE)
    private val packageRegistry = InstalledPackageRegistry(appContext)
    val root: File = File(appContext.filesDir, "annie-scripts").apply { mkdirs() }

    fun ensureStarterScript() {
        val echo = File(root, "echo.js")
        if (!echo.exists()) {
            echo.writeText(
                """annie.commands.register({
                    |  name: "echo",
                    |  description: "Repeat a message",
                    |  usage: "/echo <text>",
                    |  async execute(ctx) {
                    |    return { type: "text", text: ctx.text || "Hello from Annie JavaScript 😭" };
                    |  }
                    |});
                """.trimMargin()
            )
        }
        val chess = File(root, "chess.js")
        if (!chess.exists()) {
            chess.writeText(StarterScripts.chess)
        } else {
            val current = chess.readText()
            val migrated = StarterScripts.migrateChess(current)
            if (migrated != current) chess.writeText(migrated)
        }
    }

    fun listProjects(): List<ScriptProject> {
        val projects = root.listFiles().orEmpty()
            .filterNot { it.name.startsWith('.') }
            .filter { it.isDirectory || it.isFile && it.extension.equals("js", ignoreCase = true) }
            .mapNotNull(::readProject)
        val installed = projects.filter { it.hasPackageManifest }.map { project ->
            val previous = packageRegistry.get(project.id)
            InstalledPackageState(
                localId = project.id,
                packageId = project.manifest.packageId,
                displayName = project.manifest.displayName,
                version = project.manifest.version,
                apiVersion = project.manifest.apiVersion,
                entryPoint = project.manifest.entryPoint,
                installedAtMillis = previous?.installedAtMillis?.takeIf { it > 0L } ?: System.currentTimeMillis(),
                enabled = project.enabled,
                requestedPermissions = project.manifest.permissions,
                grantedPermissions = previous?.grantedPermissions.orEmpty(),
            )
        }
        packageRegistry.reconcile(installed)
        return projects.map { project ->
            project.copy(enabled = packageRegistry.get(project.id)?.enabled ?: project.enabled)
        }.sortedBy { it.name.lowercase() }
    }

    fun readProject(file: File): ScriptProject? = runCatching {
        if (!file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) return null
        val relative = file.relativeTo(root).invariantSeparatorsPath
        if (!file.isDirectory && !file.extension.equals("js", ignoreCase = true)) return null
        val sourceFiles = linkedMapOf<String, String>()
        if (file.isDirectory) {
            file.walkTopDown().filter { it.isFile && it.extension.equals("js", ignoreCase = true) }
                .forEach { child -> sourceFiles[child.relativeTo(file).invariantSeparatorsPath] = child.readText() }
        } else {
            sourceFiles[file.name] = file.readText()
        }
        val id = relative.removeSuffix(".js")
        val importedManifest = if (file.isDirectory) {
            AnniePackageArchive.readManifestIfPresent(file, id, file.nameWithoutExtension, sourceFiles.keys)
        } else null
        val entryRelative = importedManifest?.entryPoint ?: if (file.isDirectory) {
            "main.js".takeIf { it in sourceFiles } ?: sourceFiles.keys.singleOrNull() ?: return null
        } else file.name
        if (entryRelative !in sourceFiles) return null
        ScriptProject(
            id = id,
            name = importedManifest?.displayName ?: file.nameWithoutExtension,
            entryPath = entryRelative,
            files = sourceFiles,
            enabled = packageRegistry.get(id)?.enabled ?: enabledPrefs.getBoolean(id, importedManifest == null),
            importedManifest = importedManifest,
        )
    }.getOrNull()

    fun resolveRelativePath(baseModule: String, requested: String, projectId: String): String? {
        if (!requested.startsWith("./") && !requested.startsWith("../")) return null
        val prefix = "$projectId/"
        if (!baseModule.startsWith(prefix)) return null
        val parent = baseModule.removePrefix(prefix).substringBeforeLast('/', "")
        val stack = mutableListOf<String>()
        if (parent.isNotEmpty()) stack += parent.split('/').filter(String::isNotEmpty)
        for (part in requested.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isEmpty()) return null else stack.removeAt(stack.lastIndex)
                else -> {
                    if (part.contains('\\') || part == ":") return null
                    stack += part
                }
            }
        }
        if (stack.isEmpty() || stack.any { it.startsWith('.') && it == ".." }) return null
        val resolved = stack.joinToString("/")
        return if (resolved.endsWith(".js", ignoreCase = true)) "$prefix$resolved" else "$prefix$resolved.js"
    }

    fun createScript(name: String): File {
        val safe = validateName(name, ".js")
        return File(root, safe).apply {
            require(!exists()) { "A script with this name already exists" }
            writeText("""annie.commands.register({
                |  name: "${safe.removeSuffix(".js")}",
                |  description: "My command",
                |  async execute(ctx) {
                |    return { type: "text", text: "Hello from JavaScript" };
                |  }
                |});
            """.trimMargin())
        }
    }

    /**
     * Stages a loose JavaScript file as a package without evaluating it. Imported code stays
     * disabled until the user explicitly enables the package from Script Studio.
     */
    fun importJavaScript(displayName: String, source: String): File {
        require(source.length <= MAX_SOURCE_CHARS) { "Script file is too large" }
        val base = displayName.substringAfterLast('/').substringAfterLast('\\')
            .removeSuffix(".js").removeSuffix(".JS")
            .replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').take(48)
            .ifBlank { "imported" }
        var candidate = base
        var suffix = 2
        while (File(root, "$candidate.js").exists() || File(root, candidate).exists()) {
            candidate = "${base}_$suffix"
            suffix++
        }
        val imported = File(root, "$candidate.js")
        imported.writeText(source)
        enabledPrefs.edit().putBoolean(candidate, false).apply()
        return imported
    }

    fun createFolder(name: String): File {
        val safe = validateName(name, "")
        return File(root, safe).apply {
            require(mkdir()) { "A project with this name already exists" }
            File(this, "main.js").writeText("""annie.commands.register({
                |  name: "$safe",
                |  description: "My command",
                |  async execute(ctx) {
                |    return { type: "text", text: "Hello from JavaScript" };
                |  }
                |});
            """.trimMargin())
        }
    }

    fun readFile(projectId: String, relativePath: String): String {
        val target = resolveProjectFile(projectId, relativePath)
        require(target.isFile) { "Script file does not exist" }
        return target.readText()
    }

    fun resolveAssetFile(projectId: String, logicalId: String): File {
        require(logicalId.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}"))) { "Invalid package asset ID" }
        val container = resolveProjectContainer(projectId)
        require(container.isDirectory) { "Loose JavaScript files do not have bundled assets" }
        val manifest = readProject(container)?.manifest ?: error("Package manifest could not be read")
        val declaration = manifest.assets.singleOrNull { it.logicalId == logicalId }
            ?: error("Package asset is not declared: $logicalId")
        val root = container.canonicalFile
        val target = File(root, declaration.relativePath).canonicalFile
        require(target.toPath().startsWith(root.toPath()) && target.isFile) { "Package asset is missing or outside its package" }
        return target
    }

    fun readAssetText(projectId: String, logicalId: String): String {
        val target = resolveAssetFile(projectId, logicalId)
        require(target.extension.lowercase() in setOf("txt", "json", "csv", "xml", "md", "js", "css", "html")) {
            "Package asset is not a supported text resource"
        }
        require(target.length() <= MAX_ASSET_TEXT_BYTES) { "Package text asset is too large" }
        return target.readText(Charsets.UTF_8)
    }

    fun setEnabled(projectId: String, enabled: Boolean) {
        val container = resolveProjectContainer(projectId)
        val project = readProject(container) ?: error("Script project could not be loaded")
        if (project.hasPackageManifest) {
            if (packageRegistry.get(projectId) == null) {
                packageRegistry.ensureRecovered(InstalledPackageState(
                    localId = projectId,
                    packageId = project.manifest.packageId,
                    displayName = project.manifest.displayName,
                    version = project.manifest.version,
                    apiVersion = project.manifest.apiVersion,
                    entryPoint = project.manifest.entryPoint,
                    installedAtMillis = System.currentTimeMillis(),
                    enabled = project.enabled,
                    requestedPermissions = project.manifest.permissions,
                ))
            }
            packageRegistry.setEnabled(projectId, enabled)
        }
        check(enabledPrefs.edit().putBoolean(projectId, enabled).commit()) { "Could not save script enabled state" }
    }

    fun isEnabled(projectId: String): Boolean =
        packageRegistry.get(projectId)?.enabled ?: enabledPrefs.getBoolean(projectId, true)

    internal fun recordInstalledPackage(localId: String, manifest: AnniePackageManifest) {
        packageRegistry.recordInstalled(localId, manifest, enabled = false)
    }

    internal fun installedPackageState(localId: String): InstalledPackageState? = packageRegistry.get(localId)

    fun grantedPermissions(projectId: String): Set<String> = packageRegistry.get(projectId)?.grantedPermissions.orEmpty()

    fun setGrantedPermissions(projectId: String, permissions: Set<String>) {
        val project = readProject(resolveProjectContainer(projectId)) ?: error("Script project could not be loaded")
        require(project.hasPackageManifest) { "Only imported packages have package permissions" }
        require(permissions.all { it in project.manifest.permissions }) { "A package can only grant declared permissions" }
        packageRegistry.setGrantedPermissions(projectId, permissions, project.manifest.permissions)
    }

    internal fun removeInstalledPackageState(localId: String) = packageRegistry.remove(localId)

    fun writeFile(projectId: String, relativePath: String, source: String) {
        require(source.length <= MAX_SOURCE_CHARS) { "Script file is too large" }
        val target = resolveProjectFile(projectId, relativePath, allowMissing = true)
        require(target.extension.equals("js", ignoreCase = true)) { "Only JavaScript files are supported" }
        target.parentFile?.mkdirs()
        target.writeText(source)
    }

    fun createFile(projectId: String, relativePath: String): File {
        val project = resolveProjectContainer(projectId)
        require(project.isDirectory) { "Standalone scripts cannot contain helper files" }
        val safeRelative = validateRelativeJsPath(relativePath)
        val target = resolveProjectFile(projectId, safeRelative, allowMissing = true)
        require(!target.exists()) { "A script file with this name already exists" }
        target.parentFile?.mkdirs()
        target.writeText("// Annie JavaScript module\n")
        return target
    }

    fun renameProject(projectId: String, newName: String): String {
        val source = resolveProjectContainer(projectId)
        val normalized = validateName(newName, "")
        val destination = if (source.isDirectory) File(root, normalized) else File(root, "$normalized.js")
        require(!destination.exists()) { "A script project with this name already exists" }
        val wasEnabled = isEnabled(projectId)
        require(source.renameTo(destination)) { "Could not rename script project" }
        if (File(destination, "manifest.json").isFile) packageRegistry.rename(projectId, normalized)
        check(enabledPrefs.edit().remove(projectId).putBoolean(normalized, wasEnabled).commit()) {
            "Could not save renamed script state"
        }
        return normalized
    }

    fun deleteProject(projectId: String) {
        val source = resolveProjectContainer(projectId)
        val installedPackageId = readProject(source)
            ?.takeIf { it.hasPackageManifest }
            ?.manifest
            ?.packageId
        if (source.isDirectory) {
            require(source.deleteRecursively()) { "Could not delete script project" }
        } else {
            require(source.delete()) { "Could not delete script file" }
        }
        ScriptScheduler.cancelAllForScript(appContext, projectId)
        ScriptTaskStore.removeAllForScript(appContext, projectId)
        installedPackageId?.let { clearPackageNotifications(appContext, it) }
        packageRegistry.remove(projectId)
        enabledPrefs.edit().remove(projectId).commit()
        appContext.getSharedPreferences(scriptStorageName(projectId), Context.MODE_PRIVATE).edit().clear().commit()
        clearScriptEnvState(appContext, projectId)
        clearScriptSessions(appContext, projectId)
        val packageDataRoot = File(appContext.filesDir, "annie-script-data").canonicalFile
        val packageData = File(packageDataRoot, projectId).canonicalFile
        require(packageData.toPath().startsWith(packageDataRoot.toPath())) { "Invalid package data path" }
        packageData.deleteRecursively()
    }

    fun renameFile(projectId: String, relativePath: String, newRelativePath: String): String {
        val project = resolveProjectContainer(projectId)
        require(project.isDirectory) { "Rename the standalone project instead" }
        require(relativePath != "main.js") { "main.js is the folder entry point and cannot be moved" }
        val source = resolveProjectFile(projectId, relativePath)
        require(source.isFile) { "Script file does not exist" }
        val safeTarget = validateRelativeJsPath(newRelativePath)
        val destination = resolveProjectFile(projectId, safeTarget, allowMissing = true)
        require(!destination.exists()) { "A script file with this name already exists" }
        destination.parentFile?.mkdirs()
        require(source.renameTo(destination)) { "Could not rename script file" }
        return safeTarget
    }

    fun deleteFile(projectId: String, relativePath: String) {
        val project = resolveProjectContainer(projectId)
        require(project.isDirectory) { "Delete the standalone project instead" }
        require(relativePath != "main.js") { "main.js is the folder entry point and cannot be deleted" }
        val target = resolveProjectFile(projectId, relativePath)
        require(target.isFile && target.delete()) { "Could not delete script file" }
    }

    private fun resolveProjectContainer(projectId: String): File {
        require(projectId.matches(Regex("[A-Za-z0-9_-]{1,48}"))) { "Invalid project id" }
        val directory = File(root, projectId).canonicalFile
        if (directory.isDirectory) return directory
        val standalone = File(root, "$projectId.js").canonicalFile
        if (standalone.isFile) return standalone
        throw IllegalArgumentException("Script project does not exist")
    }

    private fun resolveProjectFile(projectId: String, relativePath: String, allowMissing: Boolean = false): File {
        val project = resolveProjectContainer(projectId)
        if (project.isFile) {
            require(relativePath == project.name) { "Standalone scripts have a single file" }
            return project
        }
        val safeRelative = validateRelativeJsPath(relativePath)
        val target = File(project, safeRelative).canonicalFile
        require(target.toPath().startsWith(project.canonicalFile.toPath())) { "Invalid script path" }
        if (!allowMissing) require(target.exists()) { "Script file does not exist" }
        return target
    }

    private fun validateRelativeJsPath(path: String): String {
        require(!path.trim().startsWith('/') && !path.contains('\\')) { "Script paths must stay inside the package" }
        val normalized = path.trim()
        val parts = normalized.split('/')
        require(
            normalized.length <= 240 &&
                parts.all { it.isNotBlank() && it.length <= 64 && it != "." && it != ".." && !it.startsWith('.') &&
                    it.matches(Regex("[A-Za-z0-9_-][A-Za-z0-9_. -]{0,63}")) } &&
                normalized.endsWith(".js", ignoreCase = true)
        ) {
            "Use JavaScript paths such as helper.js or lib/parser.js"
        }
        return normalized
    }

    private fun validateName(name: String, suffix: String): String {
        val normalized = name.trim().removeSuffix(suffix)
        require(normalized.matches(Regex("[A-Za-z0-9_-]{1,48}"))) { "Use letters, numbers, _ or - for names" }
        return "$normalized$suffix"
    }

    companion object {
        const val MAX_SOURCE_CHARS = 512_000
        private const val MAX_ASSET_TEXT_BYTES = 1_048_576L
    }
}

/** One isolated QuickJS runtime per enabled project; all work is serialized off the main thread. */
internal class ScriptRuntime(
    private val context: Context,
    private val project: ScriptProject,
    private val files: ScriptFiles,
    private val onLog: (ScriptLog) -> Unit,
    private val serviceBridge: suspend (ScriptProject, String, String, String) -> String,
    private val androidBridge: suspend (ScriptProject, String, String) -> String,
) : AutoCloseable {
    private val lock = Mutex()
    private val registered = linkedMapOf<String, ScriptCommand>()
    private val runtime: QuickJs
    private var capturedModuleResult: String? = null
    private val envStore = ScriptEnvStore(context, project.id)
    private var envDefinition: ScriptEnvDefinition? = null
    private var invocationChatId: String? = null
    private var handlingService = false
    private val registeredServices = linkedSetOf<String>()

    init {
        val modulePrefix = "${project.id}/"
        val loader = ModuleLoader { normalizedName ->
            if (!normalizedName.startsWith(modulePrefix)) return@ModuleLoader null
            val relative = normalizedName.removePrefix(modulePrefix)
            project.files[relative]?.let(ModuleContent::Source)
        }
        runtime = QuickJs.create(Dispatchers.IO, loader)
        runtime.memoryLimit = MAX_RUNTIME_BYTES
        runtime.evaluationTimeoutMillis = EVALUATION_TIMEOUT_MS
        runtime.function("annieRegisterCommand") { args ->
            val metadata = JSONObject(args.firstOrNull() as? String ?: error("Missing command definition"))
            val name = metadata.optString("name").lowercase()
            require(name.matches(Regex("[a-z][a-z0-9_-]{0,31}"))) { "Command names must start with a letter" }
            val aliases = metadata.optJSONArray("aliases").toStringList()
            val command = ScriptCommand(
                scriptId = project.id,
                name = name,
                aliases = aliases.map(String::lowercase),
                description = metadata.optString("description"),
                usage = metadata.optString("usage", "/$name"),
                keywords = metadata.optJSONArray("keywords").toStringList().map(String::lowercase),
                capabilities = metadata.optJSONArray("capabilities").toStringList().map(String::lowercase),
                suggestedActions = metadata.optJSONArray("suggestions").toSuggestedActions(),
            )
            registered[name] = command
            aliases.forEach { registered[it.lowercase()] = command }
            null
        }
        // QuickJS-KT intentionally does not return values from ES module evaluation.
        // Invocation modules hand their JSON result back through this synchronous bridge.
        runtime.function("annieCaptureModuleResult") { args ->
            capturedModuleResult = args.firstOrNull()?.toString()
            null
        }
        runtime.function("annieRegisterService") { args ->
            val name = args.firstOrNull()?.toString().orEmpty()
            require(project.manifest.services.any { it.name == name }) { "Service $name is not declared in this package manifest" }
            require(registeredServices.add(name)) { "Service $name is already registered" }
            null
        }
        runtime.asyncFunction("annieCallService") { args ->
            check(!handlingService) { "Inter-package service calls cannot be nested" }
            val providerPackageId = args.getOrNull(0)?.toString().orEmpty()
            val serviceName = args.getOrNull(1)?.toString().orEmpty()
            val payload = args.getOrNull(2)?.toString().orEmpty()
            serviceBridge(project, providerPackageId, serviceName, payload)
        }
        runtime.asyncFunction("annieAndroidBridge") { args ->
            val operation = args.getOrNull(0)?.toString().orEmpty()
            val payload = args.getOrNull(1)?.toString().orEmpty()
            androidBridge(project, operation, payload)
        }
        runtime.function("annieStoreGet") { args ->
            val key = args.firstOrNull()?.toString().orEmpty()
            context.getSharedPreferences(scriptStorageName(project.id), Context.MODE_PRIVATE).getString(key, null)
        }
        runtime.function("annieStoreSet") { args ->
            val key = args.getOrNull(0)?.toString().orEmpty()
            val value = args.getOrNull(1)?.toString().orEmpty()
            context.getSharedPreferences(scriptStorageName(project.id), Context.MODE_PRIVATE).edit().putString(key, value).apply()
            null
        }
        runtime.function("annieSessionSet") { args ->
            val chatId = args.getOrNull(0)?.toString().orEmpty()
            val sessionName = args.getOrNull(1)?.toString().orEmpty()
            require(chatId.isNotBlank()) { "Session requires a chat id" }
            require(sessionName.matches(Regex("[A-Za-z][A-Za-z0-9_.:-]{0,63}"))) { "Invalid session name" }
            writeActiveSession(context, chatId, ActiveScriptSession(project.id, sessionName))
            null
        }
        runtime.function("annieSessionClear") { args ->
            val chatId = args.getOrNull(0)?.toString().orEmpty()
            if (chatId.isNotBlank()) clearActiveSession(context, chatId)
            null
        }
        runtime.function("annieRenderChess") { args ->
            val fen = args.firstOrNull()?.toString().orEmpty()
            ChessBoardRenderer.render(context, fen)
        }
        runtime.function("annieFileReadText") { args ->
            val path = args.firstOrNull()?.toString().orEmpty()
            val file = resolveDataFile(path, allowMissing = false)
            require(file.isFile) { "Script data file does not exist" }
            require(file.length() <= MAX_SCRIPT_DATA_FILE_BYTES) { "Script data file is too large" }
            file.readText()
        }
        runtime.function("annieFileWriteText") { args ->
            val path = args.getOrNull(0)?.toString().orEmpty()
            val text = args.getOrNull(1)?.toString().orEmpty()
            require(text.toByteArray().size <= MAX_SCRIPT_DATA_FILE_BYTES) { "Script data file is too large" }
            val file = resolveDataFile(path, allowMissing = true)
            file.parentFile?.mkdirs()
            file.writeText(text)
            file.length()
        }
        runtime.function("annieFileDelete") { args ->
            val path = args.firstOrNull()?.toString().orEmpty()
            val file = resolveDataFile(path, allowMissing = false)
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
        runtime.function("annieFileList") { args ->
            val path = args.firstOrNull()?.toString().orEmpty()
            val directory = if (path.isBlank()) scriptDataRoot() else resolveDataFile(path, allowMissing = false)
            require(directory.isDirectory) { "Script data path is not a directory" }
            directory.listFiles().orEmpty().sortedBy { it.name.lowercase() }.map {
                mapOf("name" to it.name, "directory" to it.isDirectory, "size" to if (it.isFile) it.length() else 0L)
            }
        }
        runtime.function("annieAssetUri") { args ->
            val logicalId = args.firstOrNull()?.toString().orEmpty()
            files.resolveAssetFile(project.id, logicalId)
            "annie-asset://$logicalId"
        }
        runtime.function("annieAssetReadText") { args ->
            files.readAssetText(project.id, args.firstOrNull()?.toString().orEmpty())
        }
        runtime.asyncFunction("annieHttpRequest") { args ->
            requireNetworkAccess()
            val request = JSONObject(args.firstOrNull() as? String ?: "{}")
            JSONObject(requestHttp(request)).toString()
        }
        runtime.asyncFunction("annieBrowserFetch") { args ->
            requireNetworkAccess()
            val request = JSONObject(args.firstOrNull() as? String ?: "{}")
            JSONObject(requestBrowserFetch(request)).toString()
        }
        runtime.function("annieBrowserBuildMessage") { args ->
            requireNetworkAccess()
            val raw = args.firstOrNull() as? String ?: "{}"
            val spec = AnnieBrowserSpec.decode(raw) ?: error("Invalid Annie browser request")
            AnnieBrowserSessionStore.register(context, spec)
            spec.encode().toString()
        }
        runtime.function("annieBrowserSession") { args ->
            val sessionId = args.firstOrNull()?.toString().orEmpty()
            val session = AnnieBrowserSessionStore.get(context, sessionId) ?: return@function null
            JSONObject()
                .put("sessionId", session.sessionId)
                .put("currentUrl", session.currentUrl)
                .put("userAgent", session.userAgent ?: WebSettings.getDefaultUserAgent(context))
                .put("allowedHosts", JSONArray(session.allowedHosts))
                .put("restricted", session.restricted)
                .put("verificationState", session.verificationState.wireName)
                .put("verifiedAt", session.verifiedAtMillis)
                .toString()
        }
        runtime.asyncFunction("annieBrowserClear") { args ->
            val sessionId = args.firstOrNull()?.toString().orEmpty()
            AnnieBrowserSessionStore.clear(context, sessionId)
            true
        }
        runtime.function("annieEnvDefine") { args ->
            val raw = args.firstOrNull()?.toString().orEmpty()
            envDefinition = ScriptEnvDefinition.decode(raw)
            null
        }
        runtime.function("annieEnvGet") { args ->
            val key = args.firstOrNull()?.toString().orEmpty()
            val field = requireEnvField(key)
            require(field.type != ScriptEnvFieldType.SECRET) { "Secret ENV values require annie.env.secret(key)" }
            JSONObject().put("value", envStore.value(field) ?: JSONObject.NULL).toString()
        }
        runtime.function("annieEnvSet") { args ->
            val key = args.getOrNull(0)?.toString().orEmpty()
            val raw = args.getOrNull(1)?.toString().orEmpty()
            val field = requireEnvField(key)
            val value = JSONObject(raw.ifBlank { "{}" }).opt("value").takeUnless { it == JSONObject.NULL }
            envStore.setFromScript(field, value)
            true
        }
        runtime.function("annieEnvSecret") { args ->
            val key = args.firstOrNull()?.toString().orEmpty()
            envStore.secret(requireEnvField(key))
        }
        runtime.function("annieEnvValues") { _ ->
            envStore.values(envDefinition).toString()
        }
        runtime.function("annieScheduleCreate") { args ->
            val raw = args.firstOrNull()?.toString().orEmpty()
            val chatId = invocationChatId ?: error("Schedule creation requires an active script invocation")
            ScriptScheduler.create(context, project.id, chatId, JSONObject(raw.ifBlank { "{}" })).toString()
        }
        runtime.function("annieScheduleList") { _ ->
            ScriptScheduler.list(context, project.id).toString()
        }
        runtime.function("annieScheduleCancel") { args ->
            ScriptScheduler.cancel(context, project.id, args.firstOrNull()?.toString().orEmpty())
        }
        runtime.function("annieScheduleEnable") { args ->
            ScriptScheduler.setEnabled(context, project.id, args.firstOrNull()?.toString().orEmpty(), true)
        }
        runtime.function("annieScheduleDisable") { args ->
            ScriptScheduler.setEnabled(context, project.id, args.firstOrNull()?.toString().orEmpty(), false)
        }
        runtime.function("annieTaskStart") { args ->
            val raw = args.firstOrNull()?.toString().orEmpty()
            val chatId = invocationChatId ?: error("Task creation requires an active script invocation")
            ScriptTaskManager.start(context, project.id, chatId, JSONObject(raw.ifBlank { "{}" })).toString()
        }
        runtime.function("annieTaskList") { _ ->
            ScriptTaskManager.list(context, project.id).toString()
        }
        runtime.function("annieTaskCancel") { args ->
            ScriptTaskManager.cancel(context, project.id, args.firstOrNull()?.toString().orEmpty())
        }
        runtime.function("annieTaskRetry") { args ->
            ScriptTaskManager.retry(context, project.id, args.firstOrNull()?.toString().orEmpty())
        }
        runtime.function("annieLog") { args ->
            val level = args.getOrNull(0)?.toString()?.uppercase()?.take(8) ?: "INFO"
            val message = redact(args.drop(1).joinToString(" ")).take(MAX_LOG_CHARS)
            onLog(ScriptLog(System.currentTimeMillis(), project.id, level, message))
            null
        }
    }

    suspend fun load(): List<ScriptCommand> = lock.withLock {
        registered.clear()
        registeredServices.clear()
        envDefinition = null
        // The bootstrap's final assignment evaluates to an object. Keep that value
        // inside an IIFE so Unit receives JavaScript undefined instead.
        runtime.evaluate<Unit>("(() => {\n$BOOTSTRAP\n})()", filename = "annie-runtime.js")
        val entryModule = "${project.id}/${project.entryPath}"
        val entry = project.files[project.entryPath] ?: error("Missing script entry: ${project.entryPath}")
        runtime.evaluate<JsObject>(entry, filename = entryModule, asModule = true)
        registered.values.distinctBy { it.name }
    }

    suspend fun invokeService(serviceName: String, inputJson: String): String = lock.withLock {
        require(!handlingService) { "Inter-package service calls cannot be nested" }
        require(serviceName in registeredServices) { "Service handler is not registered" }
        handlingService = true
        try {
            evaluateModuleResult(
                "await globalThis.__annieInvokeService(${JSONObject.quote(serviceName)}, ${JSONObject.quote(inputJson)})",
                "annie-service-$serviceName.js",
            )
        } finally {
            handlingService = false
        }
    }

    fun environment(): ScriptEnvDefinition? = envDefinition

    fun environmentValue(key: String): Any? {
        val field = requireEnvField(key)
        return if (field.type == ScriptEnvFieldType.SECRET) null else envStore.value(field)
    }

    fun environmentHasSecret(key: String): Boolean {
        val field = requireEnvField(key)
        return envStore.hasSecret(field)
    }

    fun setEnvironmentValue(key: String, value: Any?) {
        envStore.setFromUi(requireEnvField(key), value)
    }

    private fun requireEnvField(key: String): ScriptEnvField =
        envDefinition?.field(key) ?: error("Unknown ENV field: $key")

    suspend fun execute(commandName: String, commandText: String, chatId: String, messageId: Long): String = lock.withLock {
        val commandArgs = commandText.trim().split(Regex("\\s+")).filter(String::isNotBlank).drop(1)
        val invocation = JSONObject()
            .put("text", commandArgs.joinToString(" "))
            .put("args", JSONArray(commandArgs))
            .put("command", commandName)
            .put("chatId", chatId)
            .put("messageId", messageId)
            .put("replyTo", JSONObject.NULL)
        evaluateModuleResultForChat(
            chatId,
            "await globalThis.__annieRun(${JSONObject.quote(commandName)}, ${JSONObject.quote(invocation.toString())})",
            "annie-invocation.js",
        )
    }

    suspend fun executeSession(sessionName: String, text: String, chatId: String, messageId: Long): String = lock.withLock {
        val invocation = JSONObject()
            .put("text", text)
            .put("args", JSONArray())
            .put("command", "")
            .put("chatId", chatId)
            .put("messageId", messageId)
            .put("replyTo", JSONObject.NULL)
        evaluateModuleResultForChat(
            chatId,
            "await globalThis.__annieSession(${JSONObject.quote(sessionName)}, ${JSONObject.quote(invocation.toString())})",
            "annie-session.js",
        )
    }

    suspend fun executeAction(actionId: String, payloadJson: String, chatId: String, messageId: Long): String = lock.withLock {
        val invocation = JSONObject()
            .put("text", "")
            .put("args", JSONArray())
            .put("command", "")
            .put("chatId", chatId)
            .put("messageId", messageId)
            .put("replyTo", JSONObject.NULL)
        evaluateModuleResultForChat(
            chatId,
            "await globalThis.__annieAction(${JSONObject.quote(actionId)}, ${JSONObject.quote(payloadJson)}, ${JSONObject.quote(invocation.toString())})",
            "annie-action.js",
        )
    }

    private suspend fun evaluateModuleResultForChat(chatId: String, expression: String, filename: String): String {
        val previous = invocationChatId
        invocationChatId = chatId
        return try {
            evaluateModuleResult(expression, filename)
        } finally {
            invocationChatId = previous
        }
    }

    private suspend fun evaluateModuleResult(expression: String, filename: String): String {
        capturedModuleResult = null
        runtime.evaluate<JsObject>(
            "annieCaptureModuleResult($expression)",
            filename = filename,
            asModule = true,
        )
        return capturedModuleResult ?: error("Script module did not return a message")
    }

    override fun close() { runtime.close() }

    private fun scriptDataRoot(): File =
        File(context.filesDir, "annie-script-data/${project.id}").canonicalFile.apply { mkdirs() }

    private fun resolveDataFile(relativePath: String, allowMissing: Boolean): File {
        val normalized = relativePath.trim().replace('\\', '/').removePrefix("/")
        require(normalized.isNotBlank()) { "Script data path is required" }
        require(!normalized.split('/').any { it == ".." || it.isBlank() }) { "Invalid script data path" }
        val root = scriptDataRoot()
        val file = File(root, normalized).canonicalFile
        require(file.toPath().startsWith(root.toPath())) { "Script data path escapes sandbox" }
        if (!allowMissing) require(file.exists()) { "Script data path does not exist" }
        return file
    }

    private fun requireNetworkAccess() {
        if (!project.hasPackageManifest) return
        require(NETWORK_ACCESS_CAPABILITY in project.manifest.capabilities) {
            "Package does not declare the $NETWORK_ACCESS_CAPABILITY capability"
        }
        require(NETWORK_ACCESS_PERMISSION in project.manifest.permissions) {
            "Package does not declare permission $NETWORK_ACCESS_PERMISSION"
        }
        require(NETWORK_ACCESS_PERMISSION in files.grantedPermissions(project.id)) {
            "Permission $NETWORK_ACCESS_PERMISSION has not been granted"
        }
    }

    private suspend fun requestHttp(request: JSONObject): Map<String, Any?> = withContext(Dispatchers.IO) {
        val initial = request.optString("url")
        requireHttpAddress(initial)
        val sessionId = request.optString("browserSession").takeIf(String::isNotBlank)
        val browserSession = sessionId?.let { AnnieBrowserSessionStore.get(context, it) }
        if (sessionId != null) require(browserSession != null) { "Unknown Annie browser session: $sessionId" }
        val explicitHeaders = linkedMapOf<String, Pair<String, String>>()
        request.optJSONObject("headers")?.let { headers ->
            val iterator = headers.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (key.equals("host", true) || key.equals("content-length", true) || key.equals("connection", true)) continue
                explicitHeaders[key.lowercase()] = key.take(128) to headers.optString(key).take(4096)
            }
        }
        val timeout = request.optInt("timeoutMs", DEFAULT_HTTP_TIMEOUT_MS).coerceIn(1_000, MAX_HTTP_TIMEOUT_MS)
        var method = request.optString("method", "GET").uppercase().takeIf { it in ALLOWED_METHODS } ?: "GET"
        var body = request.optString("body").takeIf { request.has("body") && !request.isNull("body") }
        var current = initial
        var redirects = 0
        var finalStatus = 0
        var responseBody = ""
        var finalUrl = initial
        while (true) {
            requireHttpAddress(current)
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeout
                readTimeout = timeout
                instanceFollowRedirects = false
            }
            try {
                if (browserSession != null) {
                    require(AnnieBrowserSessionStore.allows(browserSession, current)) {
                        "HTTP URL is outside this browser session's allowed sites"
                    }
                }
                explicitHeaders.values.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                if (explicitHeaders["user-agent"] == null) {
                    val userAgent = browserSession?.userAgent
                        ?: if (browserSession != null) WebSettings.getDefaultUserAgent(context) else "Annie/1.0"
                    connection.setRequestProperty("User-Agent", userAgent)
                }
                if (browserSession != null && explicitHeaders["cookie"] == null) {
                    CookieManager.getInstance().getCookie(current)?.takeIf(String::isNotBlank)?.let {
                        connection.setRequestProperty("Cookie", it)
                    }
                }
                if (body != null && method in BODY_METHODS) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", request.optString("contentType", "application/json"))
                    connection.outputStream.use { it.write(body!!.toByteArray(Charsets.UTF_8)) }
                }
                finalStatus = connection.responseCode
                if (browserSession != null) persistResponseCookies(current, connection)
                val location = connection.getHeaderField("Location")
                if (finalStatus in REDIRECT_CODES && !location.isNullOrBlank() && redirects < MAX_HTTP_REDIRECTS) {
                    val next = URI(current).resolve(location).toString()
                    requireHttpAddress(next)
                    if (!sameOrigin(current, next)) {
                        explicitHeaders.remove("authorization")
                        explicitHeaders.remove("cookie")
                    }
                    if (finalStatus == 303 || (finalStatus in setOf(301, 302) && method == "POST")) {
                        method = "GET"
                        body = null
                    }
                    current = next
                    redirects++
                    continue
                }
                finalUrl = connection.url?.toString() ?: current
                val stream = if (finalStatus >= 400) connection.errorStream else connection.inputStream
                responseBody = stream?.bufferedReader()?.use { it.readText().take(MAX_HTTP_RESPONSE_CHARS) }.orEmpty()
                break
            } finally {
                connection.disconnect()
            }
        }
        val responseJson = runCatching { JSONObject(responseBody).toMap() }.getOrNull()
        mapOf(
            "status" to finalStatus,
            "ok" to (finalStatus in 200..299),
            "url" to finalUrl,
            "body" to responseBody,
            "json" to responseJson,
        )
    }

    /** Runs fetch inside the active browser page so cookies and browser networking stay in context. */
    private suspend fun requestBrowserFetch(request: JSONObject): Map<String, Any?> =
        withContext(Dispatchers.Main.immediate) {
            val sessionId = request.optString("sessionId").trim()
            require(sessionId.isNotBlank()) { "A browser sessionId is required" }
            val session = AnnieBrowserSessionStore.get(context, sessionId)
                ?: error("Unknown Annie browser session: $sessionId")
            val url = request.optString("url", session.currentUrl).trim().ifBlank { session.currentUrl }
            require(AnnieBrowserSessionStore.allows(session, url)) {
                "URL is outside this browser session's allowed sites"
            }
            val webView = AnnieBrowserControllers.get(sessionId).webView
                ?: error("Open this session's browser message before calling browser.fetch")
            require(webView.url?.startsWith("http://") == true || webView.url?.startsWith("https://") == true) {
                "The browser page is not ready yet"
            }
            val method = request.optString("method", "GET").uppercase()
            require(method in ALLOWED_METHODS) { "Unsupported browser fetch method" }
            val headers = JSONObject()
            request.optJSONObject("headers")?.let { input ->
                val keys = input.keys()
                while (keys.hasNext()) {
                    val originalKey = keys.next()
                    val key = originalKey.take(128)
                    if (key.equals("cookie", true) || key.equals("host", true) ||
                        key.equals("content-length", true) || key.equals("connection", true) ||
                        key.startsWith("sec-", true)) continue
                    headers.put(key, input.optString(originalKey, "").take(4096))
                }
            }
            val init = JSONObject()
                .put("method", method)
                .put("headers", headers)
                .put("credentials", "include")
                .put("redirect", "follow")
            if (request.has("body") && !request.isNull("body") && method in BODY_METHODS) {
                init.put("body", request.optString("body"))
            }
            val token = UUID.randomUUID().toString().replace("-", "")
            val initLiteral = init.toString()
            val urlLiteral = JSONObject.quote(url)
            val tokenLiteral = JSONObject.quote(token)
            val dispatch = """
                (() => {
                  const token = $tokenLiteral;
                  window.__annieBrowserFetchResults = window.__annieBrowserFetchResults || Object.create(null);
                  (async () => {
                    try {
                      const response = await fetch($urlLiteral, $initLiteral);
                      const body = (await response.text()).slice(0, $MAX_HTTP_RESPONSE_CHARS);
                      const responseHeaders = {};
                      response.headers.forEach((value, key) => { responseHeaders[key] = value; });
                      window.__annieBrowserFetchResults[token] = {status: response.status, ok: response.ok, url: response.url, body, headers: responseHeaders};
                    } catch (error) {
                      window.__annieBrowserFetchResults[token] = {error: String(error)};
                    }
                  })();
                  return true;
                })()
            """.trimIndent()
            evaluateBrowserJavascript(webView, dispatch)
            val timeout = request.optInt("timeoutMs", DEFAULT_HTTP_TIMEOUT_MS).coerceIn(1_000, MAX_HTTP_TIMEOUT_MS).toLong()
            val result = withTimeout(timeout) {
                while (true) {
                    delay(75)
                    val probe = evaluateBrowserJavascript(
                        webView,
                        "JSON.stringify(window.__annieBrowserFetchResults && window.__annieBrowserFetchResults[$tokenLiteral] != null ? window.__annieBrowserFetchResults[$tokenLiteral] : null)",
                    )
                    val decoded = runCatching { JSONTokener(probe ?: "null").nextValue() }.getOrNull()
                    if (decoded is String) {
                        val value = runCatching { JSONObject(decoded) }.getOrNull() ?: continue
                        evaluateBrowserJavascript(webView, "delete window.__annieBrowserFetchResults[$tokenLiteral]")
                        if (value.has("error")) error(value.optString("error"))
                        val body = value.optString("body").take(MAX_HTTP_RESPONSE_CHARS)
                        val headersObject = value.optJSONObject("headers") ?: JSONObject()
                        val headersMap = linkedMapOf<String, String>()
                        val headerKeys = headersObject.keys()
                        while (headerKeys.hasNext()) {
                            val key = headerKeys.next()
                            headersMap[key] = headersObject.optString(key)
                        }
                        return@withTimeout mapOf(
                            "status" to value.optInt("status"),
                            "ok" to value.optBoolean("ok"),
                            "url" to value.optString("url", url),
                            "body" to body,
                            "json" to runCatching { JSONObject(body).toMap() }.getOrNull(),
                            "headers" to headersMap,
                            "browserBacked" to true,
                        )
                    }
                }
                error("Browser fetch timed out")
            }
            result
        }

    private suspend fun evaluateBrowserJavascript(webView: android.webkit.WebView, javascript: String): String? =
        suspendCancellableCoroutine { continuation ->
            runCatching {
                webView.evaluateJavascript(javascript) { value ->
                    if (continuation.isActive) continuation.resume(value)
                }
            }.onFailure { if (continuation.isActive) continuation.resume(null) }
        }

    private fun requireHttpAddress(raw: String) {
        val uri = runCatching { URI(raw) }.getOrNull()
        require(uri != null && (uri.scheme?.equals("https", true) == true || uri.scheme?.equals("http", true) == true) && !uri.host.isNullOrBlank() && uri.rawUserInfo.isNullOrBlank()) {
            "Only safe HTTP and HTTPS URLs are allowed"
        }
    }

    private fun sameOrigin(left: String, right: String): Boolean = runCatching {
        val a = URI(left); val b = URI(right)
        a.scheme.equals(b.scheme, true) && a.host.equals(b.host, true) && a.port == b.port
    }.getOrDefault(false)

    private fun persistResponseCookies(url: String, connection: HttpURLConnection) {
        val values = connection.headerFields.entries
            .filter { it.key.equals("Set-Cookie", true) }
            .flatMap { it.value.orEmpty() }
        if (values.isEmpty()) return
        val manager = CookieManager.getInstance()
        values.forEach { cookie ->
            val latch = CountDownLatch(1)
            manager.setCookie(url, cookie) { latch.countDown() }
            runCatching { latch.await(1, TimeUnit.SECONDS) }
        }
        manager.flush()
    }

    private fun redact(value: String): String {
        var output = value.replace(
            Regex("(?i)(authorization|api[_-]?key|token|password)(\\s*[=:]\\s*)[^,\\s]+"),
            "$1$2[redacted]",
        )
        envDefinition?.fields.orEmpty()
            .filter { it.type == ScriptEnvFieldType.SECRET }
            .mapNotNull(envStore::secret)
            .filter { it.isNotBlank() }
            .forEach { secret -> output = output.replace(secret, "[redacted]") }
        return output
    }

    companion object {
        private const val MAX_RUNTIME_BYTES = 32L * 1024L * 1024L
        private const val MAX_SCRIPT_DATA_FILE_BYTES = 2L * 1024L * 1024L
        private const val EVALUATION_TIMEOUT_MS = 8_000L
        private const val DEFAULT_HTTP_TIMEOUT_MS = 20_000
        private const val MAX_HTTP_TIMEOUT_MS = 120_000
        private const val MAX_HTTP_RESPONSE_CHARS = 2_000_000
        private const val MAX_LOG_CHARS = 2_000
        private val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE")
        private val BODY_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private const val MAX_HTTP_REDIRECTS = 8
        private val BOOTSTRAP = """
            |globalThis.__annieCommandHandlers = Object.create(null);
            |globalThis.__annieActionHandlers = Object.create(null);
            |globalThis.__annieSessionHandlers = Object.create(null);
            |globalThis.__annieServiceHandlers = Object.create(null);
            |const __annieContext = rawContext => {
            |  const ctx = JSON.parse(rawContext);
            |  ctx.session = {
            |    start: name => annieSessionSet(String(ctx.chatId), String(name)),
            |    end: () => annieSessionClear(String(ctx.chatId))
            |  };
            |  return ctx;
            |};
            |globalThis.annie = {
            |  commands: { register(definition) {
            |    if (!definition || typeof definition.execute !== "function") throw new TypeError("Command requires execute(ctx)");
            |    if (!definition.name) throw new TypeError("Command requires a name");
            |    const name = String(definition.name).toLowerCase();
            |    const aliases = Array.isArray(definition.aliases) ? definition.aliases.map(String) : [];
            |    const keywords = Array.isArray(definition.keywords) ? definition.keywords.map(String) : [];
            |    const capabilities = Array.isArray(definition.capabilities) ? definition.capabilities.map(String) : [];
            |    const suggestions = Array.isArray(definition.suggestions) ? definition.suggestions.map(item => ({
            |      label: String((item && item.label) || (item && item.input) || ""),
            |      input: String((item && item.input) || "")
            |    })).filter(item => item.label && item.input) : [];
            |    annieRegisterCommand(JSON.stringify({name, aliases, keywords, capabilities, suggestions, description: definition.description || "", usage: definition.usage || "/" + name}));
            |    globalThis.__annieCommandHandlers[name] = definition.execute;
            |    for (const alias of aliases) globalThis.__annieCommandHandlers[alias.toLowerCase()] = definition.execute;
            |  }},
            |  actions: { register(name, handler) {
            |    if (typeof handler !== "function") throw new TypeError("Action requires a handler");
            |    globalThis.__annieActionHandlers[String(name)] = handler;
            |  }},
            |  sessions: { register(definition) {
            |    if (!definition || !definition.name || typeof definition.onMessage !== "function") throw new TypeError("Session requires name and onMessage(ctx)");
            |    globalThis.__annieSessionHandlers[String(definition.name)] = definition.onMessage;
            |  }},
            |  services: {
            |    provide(name, handler) {
            |      name = String(name);
            |      if (typeof handler !== "function") throw new TypeError("Service requires a handler");
            |      annieRegisterService(name);
            |      globalThis.__annieServiceHandlers[name] = handler;
            |    },
            |    call: async (packageId, name, input = null) => JSON.parse(await annieCallService(String(packageId), String(name), JSON.stringify(input)))
            |  },
            |  android: {
            |    deviceInfo: async () => JSON.parse(await annieAndroidBridge("device.info", "{}")),
            |    tts: {
            |      speak: async (text, options = {}) => JSON.parse(await annieAndroidBridge("tts.speak", JSON.stringify({
            |        text: String(text),
            |        language: options && options.language ? String(options.language) : "",
            |        queue: options && options.queue ? String(options.queue) : "add"
            |      }))),
            |      status: async utteranceId => JSON.parse(await annieAndroidBridge("tts.status", JSON.stringify({
            |        utteranceId: String(utteranceId || "")
            |      }))),
            |      stop: async () => JSON.parse(await annieAndroidBridge("tts.stop", "{}"))
            |    },
            |    ocr: {
            |      asset: async assetId => JSON.parse(await annieAndroidBridge("ocr.asset", JSON.stringify({assetId: String(assetId)})))
            |    },
            |    stt: {
            |      listen: async (options = {}) => JSON.parse(await annieAndroidBridge("stt.listen", JSON.stringify({
            |        language: options && options.language ? String(options.language) : "",
            |        prompt: options && options.prompt ? String(options.prompt) : ""
            |      })))
            |    },
            |    documents: {
            |      pickText: async (options = {}) => JSON.parse(await annieAndroidBridge("documents.pickText", JSON.stringify({
            |        mimeType: options && options.mimeType ? String(options.mimeType) : "text/*"
            |      })))
            |    },
            |    media: {
            |      inspectAsset: async assetId => JSON.parse(await annieAndroidBridge("media.inspectAsset", JSON.stringify({assetId: String(assetId)})))
            |    },
            |    notifications: {
            |      post: async value => JSON.parse(await annieAndroidBridge("notifications.post", JSON.stringify({
            |        key: String((value && value.key) || ""),
            |        title: String((value && value.title) || ""),
            |        text: String((value && value.text) || "")
            |      }))),
            |      update: async value => JSON.parse(await annieAndroidBridge("notifications.update", JSON.stringify({
            |        key: String((value && value.key) || ""),
            |        title: String((value && value.title) || ""),
            |        text: String((value && value.text) || "")
            |      }))),
            |      cancel: async key => JSON.parse(await annieAndroidBridge("notifications.cancel", JSON.stringify({
            |        key: String(key || "")
            |      })))
            |    }
            |  },
            |  http: { request: async request => JSON.parse(await annieHttpRequest(JSON.stringify(request))) },
            |  browser: {
            |    open: spec => JSON.parse(annieBrowserBuildMessage(JSON.stringify(spec || {}))),
            |    fetch: async request => JSON.parse(await annieBrowserFetch(JSON.stringify(request || {}))),
            |    session: sessionId => { const raw = annieBrowserSession(String(sessionId)); return raw == null ? null : JSON.parse(raw); },
            |    clear: async sessionId => await annieBrowserClear(String(sessionId)),
            |    verification: (status, message = "") => ({type: "text", text: String(message || status), verification: {status: String(status), message: String(message)}})
            |  },
            |  image: { chess: fen => annieRenderChess(String(fen)) },
            |  assets: {
            |    uri: id => annieAssetUri(String(id)),
            |    image: id => annieAssetUri(String(id)),
            |    audio: id => annieAssetUri(String(id)),
            |    text: id => annieAssetReadText(String(id)),
            |    json: id => JSON.parse(annieAssetReadText(String(id)))
            |  },
            |  files: {
            |    readText: path => annieFileReadText(String(path)),
            |    writeText: (path, text) => annieFileWriteText(String(path), String(text)),
            |    delete: path => annieFileDelete(String(path)),
            |    list: (path = "") => annieFileList(String(path))
            |  },
            |  env: {
            |    define: definition => annieEnvDefine(JSON.stringify(definition || {})),
            |    get: async key => JSON.parse(annieEnvGet(String(key))).value,
            |    set: async (key, value) => annieEnvSet(String(key), JSON.stringify({value})),
            |    secret: async key => annieEnvSecret(String(key)),
            |    values: () => JSON.parse(annieEnvValues())
            |  },
            |  schedule: {
            |    create: async spec => JSON.parse(annieScheduleCreate(JSON.stringify(spec || {}))),
            |    list: async () => JSON.parse(annieScheduleList()),
            |    cancel: async id => annieScheduleCancel(String(id)),
            |    enable: async id => annieScheduleEnable(String(id)),
            |    disable: async id => annieScheduleDisable(String(id))
            |  },
            |  tasks: {
            |    start: async spec => JSON.parse(annieTaskStart(JSON.stringify(spec || {}))),
            |    list: async () => JSON.parse(annieTaskList()),
            |    cancel: async id => annieTaskCancel(String(id)),
            |    retry: async id => annieTaskRetry(String(id))
            |  },
            |  messages: {
            |    text: text => ({type: "text", text: String(text)}),
            |    image: value => Object.assign({type: "image"}, value || {}),
            |    music: value => Object.assign({type: "music"}, value || {}),
            |    video: value => Object.assign({type: "video"}, value || {}),
            |    seasonList: value => Object.assign({type: "season_list"}, value || {}),
            |    episodeList: value => Object.assign({type: "episode_list"}, value || {}),
            |    continueWatching: value => Object.assign({type: "continue_watching"}, value || {}),
            |    matches: value => Object.assign({type: "matches"}, value || {}),
            |    options: value => Object.assign({type: "options"}, value || {}),
            |    progress: value => Object.assign({type: "progress"}, value || {}),
            |    form: value => Object.assign({type: "form"}, value || {}),
            |    browser: value => JSON.parse(annieBrowserBuildMessage(JSON.stringify(value || {})))
            |  },
            |  storage: {
            |    get: key => { const raw = annieStoreGet(String(key)); return raw == null ? null : JSON.parse(raw); },
            |    set: (key, value) => annieStoreSet(String(key), JSON.stringify(value))
            |  },
            |  log: { info: (...args) => annieLog("INFO", ...args), warn: (...args) => annieLog("WARN", ...args), error: (...args) => annieLog("ERROR", ...args) }
            |};
            |globalThis.console = annie.log;
            |globalThis.__annieRun = async (name, rawContext) => {
            |  const execute = globalThis.__annieCommandHandlers[String(name).toLowerCase()];
            |  if (!execute) throw new Error("Command not registered: " + name);
            |  const result = await execute(__annieContext(rawContext));
            |  return JSON.stringify(result === undefined ? null : result);
            |};
            |globalThis.__annieSession = async (name, rawContext) => {
            |  const handler = globalThis.__annieSessionHandlers[String(name)];
            |  if (!handler) throw new Error("Session not registered: " + name);
            |  const result = await handler(__annieContext(rawContext));
            |  return JSON.stringify(result === undefined ? null : result);
            |};
            |globalThis.__annieAction = async (name, rawPayload, rawContext) => {
            |  const handler = globalThis.__annieActionHandlers[String(name)];
            |  if (!handler) throw new Error("Action not registered: " + name);
            |  const payload = rawPayload ? JSON.parse(rawPayload) : null;
            |  const result = await handler(payload, __annieContext(rawContext));
            |  return JSON.stringify(result === undefined ? null : result);
            |};
            |globalThis.__annieInvokeService = async (name, rawInput) => {
            |  const handler = globalThis.__annieServiceHandlers[String(name)];
            |  if (!handler) throw new Error("Service not registered: " + name);
            |  const result = await handler(JSON.parse(rawInput));
            |  return JSON.stringify(result === undefined ? null : result);
            |};
        """.trimMargin()
    }
}

/** Loads enabled scripts, refreshes slash metadata, and routes a command to its owning runtime. */
internal class ScriptWorkspace(
    context: Context,
    androidCapabilityBackend: AndroidCapabilityBackend? = null,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val androidCapabilities = androidCapabilityBackend ?: PlatformAndroidCapabilityBackend(appContext)
    val files = ScriptFiles(appContext)
    private val runtimes = ConcurrentHashMap<String, ScriptRuntime>()
    private val activePackageIds = ConcurrentHashMap.newKeySet<String>()
    private val logs = mutableListOf<ScriptLog>()
    private val logLock = Any()
    @Volatile private var commands: List<ScriptCommand> = emptyList()

    init { files.ensureStarterScript() }

    fun commands(): List<ScriptCommand> = commands
    fun logs(): List<ScriptLog> = synchronized(logLock) { logs.toList() }

    fun envDefinition(scriptId: String): ScriptEnvDefinition? = runtimes[scriptId]?.environment()
    fun envValue(scriptId: String, key: String): Any? = runtimes[scriptId]?.environmentValue(key)
    fun envHasSecret(scriptId: String, key: String): Boolean = runtimes[scriptId]?.environmentHasSecret(key) == true
    fun setEnvValue(scriptId: String, key: String, value: Any?) {
        val runtime = runtimes[scriptId] ?: error("Enable this script before editing its ENV")
        runtime.setEnvironmentValue(key, value)
    }

    suspend fun setGrantedPermissions(projectId: String, permissions: Set<String>) = withContext(Dispatchers.IO) {
        val project = files.listProjects().firstOrNull { it.id == projectId }
            ?: error("Script project could not be loaded")
        require(project.hasPackageManifest) { "Only imported packages have package permissions" }
        val removedPermissions = files.grantedPermissions(projectId) - permissions
        files.setGrantedPermissions(projectId, permissions)
        if (removedPermissions.isNotEmpty()) {
            androidCapabilities.revokePackage(project.manifest.packageId)
        }
    }

    suspend fun reload(): List<ScriptCommand> = withContext(Dispatchers.IO) {
        val projects = files.listProjects()
        val enabledProjects = projects.filter { it.enabled }
        val enabledPackageIds = enabledProjects.filter { it.hasPackageManifest }.mapTo(linkedSetOf()) {
            it.manifest.packageId
        }
        val packageIdsToRevoke = buildSet {
            addAll(projects.filter { it.hasPackageManifest && !it.enabled }.map { it.manifest.packageId })
            addAll(activePackageIds.filterNot { it in enabledPackageIds })
        }
        activePackageIds.clear()
        val old = runtimes.values.toList()
        runtimes.clear()
        old.forEach(ScriptRuntime::close)
        packageIdsToRevoke.forEach { packageId ->
            runCatching { androidCapabilities.revokePackage(packageId) }
                .onFailure { error ->
                    appendLog(ScriptLog(System.currentTimeMillis(), packageId, "WARN", "Package cleanup failed: ${error.message}"))
                }
        }
        val nextCommands = mutableListOf<ScriptCommand>()
        for (project in enabledProjects) {
            runCatching {
                val engine = ScriptRuntime(appContext, project, files, ::appendLog, ::invokePackageService, ::invokeAndroidBridge)
                val loadedCommands = try {
                    engine.load().also { commands ->
                        val commandNames = commands.mapTo(hashSetOf()) { it.name }
                        val missingSourceCommands = project.manifest.sources.filter { it.commandName !in commandNames }
                        require(missingSourceCommands.isEmpty()) {
                            "Source command is missing: " + missingSourceCommands.joinToString { "/${it.commandName}" }
                        }
                    }
                } catch (failure: Throwable) {
                    engine.close()
                    throw failure
                }
                runtimes[project.id] = engine
                if (project.hasPackageManifest) activePackageIds += project.manifest.packageId
                nextCommands += loadedCommands.map { command ->
                    command.copy(
                        packageDisplayName = project.manifest.displayName,
                        sourceDisplayName = project.manifest.sources
                            .firstOrNull { it.commandName == command.name }?.displayName,
                    )
                }
                appendLog(ScriptLog(System.currentTimeMillis(), project.id, "INFO", "Loaded ${project.entryPath}"))
            }.onFailure { error ->
                appendLog(ScriptLog(System.currentTimeMillis(), project.id, "ERROR", error.message ?: "Script failed to load"))
            }
        }
        commands = nextCommands.distinctBy { it.name }
        commands
    }

    private suspend fun invokePackageService(
        caller: ScriptProject,
        providerPackageId: String,
        serviceName: String,
        inputJson: String,
    ): String {
        require(inputJson.toByteArray(Charsets.UTF_8).size <= MAX_SERVICE_MESSAGE_BYTES) { "Service input is too large" }
        JSONTokener(inputJson).nextValue()
        require(caller.manifest.capabilities.contains(SERVICE_INVOKE_CAPABILITY)) {
            "Package does not declare the services.invoke capability"
        }
        val permission = servicePermission(providerPackageId, serviceName)
        require(permission in caller.manifest.permissions) { "Package does not declare permission $permission" }
        require(permission in files.grantedPermissions(caller.id)) { "Permission $permission has not been granted" }
        require(caller.manifest.dependencies.containsKey(providerPackageId)) { "Package does not declare dependency $providerPackageId" }
        val serviceDependency = caller.manifest.serviceDependencies.firstOrNull {
            it.packageId == providerPackageId && it.name == serviceName
        } ?: error("Package does not declare service dependency $providerPackageId/$serviceName")

        val providerProject = files.listProjects().firstOrNull {
            it.manifest.packageId == providerPackageId && it.hasPackageManifest
        } ?: error("Service provider package is not installed")
        require(providerProject.enabled) { "Service provider package is disabled" }
        val dependencyVersion = caller.manifest.dependencies.getValue(providerPackageId)
        require(dependencyVersion == "*" || dependencyVersion == providerProject.manifest.version) {
            "Installed service provider version does not match the declared dependency"
        }
        val providerService = providerProject.manifest.services.firstOrNull { it.name == serviceName }
            ?: error("Service $serviceName is not declared by $providerPackageId")
        require(providerService.version == serviceDependency.version &&
            providerService.inputSchema == serviceDependency.inputSchema &&
            providerService.outputSchema == serviceDependency.outputSchema) {
            "Service contract does not match the declared dependency"
        }
        val providerRuntime = runtimes[providerProject.id] ?: error("Service provider package is not running")
        val result = providerRuntime.invokeService(serviceName, inputJson)
        require(result.toByteArray(Charsets.UTF_8).size <= MAX_SERVICE_MESSAGE_BYTES) { "Service output is too large" }
        JSONTokener(result).nextValue()
        return result
    }

    private suspend fun invokeAndroidBridge(
        caller: ScriptProject,
        operation: String,
        inputJson: String,
    ): String {
        require(caller.hasPackageManifest) { "Only imported packages can use Android bridge APIs" }
        require(inputJson.toByteArray(Charsets.UTF_8).size <= MAX_ANDROID_BRIDGE_INPUT_BYTES) {
            "Android bridge input is too large"
        }
        val inputTokener = JSONTokener(inputJson)
        val input = inputTokener.nextValue() as? JSONObject
            ?: error("Android bridge input must be a JSON object")
        require(inputTokener.nextClean() == '\u0000') { "Android bridge input must contain one JSON object" }

        val (capability, permission) = when (operation) {
            "device.info" -> ANDROID_DEVICE_INFO_CAPABILITY to ANDROID_DEVICE_INFO_PERMISSION
            "tts.speak" -> ANDROID_TTS_CAPABILITY to ANDROID_TTS_PERMISSION
            "tts.status", "tts.stop" -> ANDROID_TTS_CAPABILITY to ANDROID_TTS_CONTROL_PERMISSION
            "ocr.asset" -> ANDROID_OCR_CAPABILITY to ANDROID_OCR_PERMISSION
            "stt.listen" -> ANDROID_STT_CAPABILITY to ANDROID_STT_PERMISSION
            "documents.pickText" -> ANDROID_DOCUMENTS_CAPABILITY to ANDROID_DOCUMENTS_PERMISSION
            "media.inspectAsset" -> ANDROID_MEDIA_CAPABILITY to ANDROID_MEDIA_PERMISSION
            "notifications.post" -> ANDROID_NOTIFICATIONS_CAPABILITY to ANDROID_NOTIFICATIONS_PERMISSION
            "notifications.update", "notifications.cancel" ->
                ANDROID_NOTIFICATIONS_CAPABILITY to ANDROID_NOTIFICATIONS_MANAGE_PERMISSION
            else -> error("Android bridge operation is not available: $operation")
        }
        require(capability in caller.manifest.capabilities) {
            "Package does not declare capability $capability"
        }
        require(permission in caller.manifest.permissions) {
            "Package does not declare permission $permission"
        }
        require(permission in files.grantedPermissions(caller.id)) {
            "Permission $permission has not been granted"
        }

        fun requireOnly(vararg allowedNames: String) {
            val allowed = allowedNames.toSet()
            val unexpected = input.keys().asSequence().filterNot { it in allowed }.toList()
            require(unexpected.isEmpty()) {
                "Android bridge operation '$operation' received unsupported fields: ${unexpected.joinToString(", ")}"
            }
        }
        fun languageTag(): String? = input.optString("language").trim().takeIf(String::isNotBlank)?.also { tag ->
            require(tag.length <= 35 && tag.matches(Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*"))) {
                "Android bridge language must be a short BCP-47 style tag"
            }
        }

        // Every operation returns plain JSON. No Android object, Context, Activity, Binder, path or
        // arbitrary URI crosses this boundary.
        val result = when (operation) {
            "device.info" -> {
                requireOnly()
                JSONObject()
                    .put("platform", "android")
                    .put("apiLevel", Build.VERSION.SDK_INT)
                    .put("locale", Locale.getDefault().toLanguageTag())
            }
            "tts.speak" -> {
                requireOnly("text", "language", "queue")
                val text = input.optString("text")
                val queueMode = input.optString("queue", "add").trim().lowercase().ifBlank { "add" }
                require(text.isNotBlank() && text.length <= 2_000) { "TTS text must be 1-2000 characters" }
                require(queueMode in setOf("add", "flush")) { "TTS queue must be add or flush" }
                androidCapabilities.speak(caller.manifest.packageId, text, languageTag(), queueMode)
            }
            "tts.status" -> {
                requireOnly("utteranceId")
                val utteranceId = input.optString("utteranceId").trim()
                require(utteranceId.isNotBlank() && utteranceId.length <= 96 &&
                    utteranceId.matches(Regex("[A-Za-z0-9._-]+"))) {
                    "TTS status requires a valid package-owned utterance ID"
                }
                androidCapabilities.ttsStatus(caller.manifest.packageId, utteranceId)
            }
            "tts.stop" -> {
                requireOnly()
                androidCapabilities.stopSpeech(caller.manifest.packageId)
            }
            "ocr.asset" -> {
                requireOnly("assetId")
                val assetId = input.optString("assetId").trim()
                require(assetId.isNotBlank() && assetId.length <= 128) { "OCR requires a package asset ID" }
                val image = files.resolveAssetFile(caller.id, assetId)
                require(image.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp", "bmp")) {
                    "OCR accepts only a declared PNG, JPEG, WebP, or BMP package asset"
                }
                androidCapabilities.recognizeText(image)
            }
            "stt.listen" -> {
                requireOnly("language", "prompt")
                val prompt = input.optString("prompt").trim()
                require(prompt.length <= 160) { "STT prompt is too long" }
                androidCapabilities.listen(languageTag(), prompt.takeIf(String::isNotBlank))
            }
            "documents.pickText" -> {
                requireOnly("mimeType")
                val mimeType = input.optString("mimeType", "text/*").trim().ifBlank { "text/*" }
                require(mimeType in setOf("text/*", "text/plain", "text/csv", "application/json", "application/xml")) {
                    "Document picker MIME type is not allowlisted"
                }
                androidCapabilities.pickTextDocument(mimeType)
            }
            "media.inspectAsset" -> {
                requireOnly("assetId")
                val assetId = input.optString("assetId").trim()
                require(assetId.isNotBlank() && assetId.length <= 128) { "Media inspection requires a package asset ID" }
                val media = files.resolveAssetFile(caller.id, assetId)
                require(media.extension.lowercase() in setOf(
                    "mp3", "m4a", "aac", "ogg", "opus", "wav", "flac",
                    "mp4", "webm", "mkv", "ts", "m4v",
                )) { "Media inspection accepts only a declared audio or video package asset" }
                androidCapabilities.inspectMedia(media)
            }
            "notifications.post" -> {
                requireOnly("key", "title", "text")
                val key = input.optString("key").trim()
                val title = input.optString("title").trim()
                val text = input.optString("text").trim()
                require(key.isBlank() || (key.length <= 64 && key.matches(Regex("[A-Za-z0-9._-]+")))) {
                    "Notification key must use only letters, numbers, dot, underscore, or dash"
                }
                require(title.isNotBlank() && title.length <= 80) { "Notification title must be 1-80 characters" }
                require(text.isNotBlank() && text.length <= 500) { "Notification text must be 1-500 characters" }
                androidCapabilities.postNotification(caller.manifest.packageId, key.takeIf(String::isNotBlank), title, text)
            }
            "notifications.update" -> {
                requireOnly("key", "title", "text")
                val key = input.optString("key").trim()
                val title = input.optString("title").trim()
                val text = input.optString("text").trim()
                require(key.isNotBlank() && key.length <= 64 && key.matches(Regex("[A-Za-z0-9._-]+"))) {
                    "Notification update requires a valid package-owned key"
                }
                require(title.isNotBlank() && title.length <= 80) { "Notification title must be 1-80 characters" }
                require(text.isNotBlank() && text.length <= 500) { "Notification text must be 1-500 characters" }
                androidCapabilities.updateNotification(caller.manifest.packageId, key, title, text)
            }
            "notifications.cancel" -> {
                requireOnly("key")
                val key = input.optString("key").trim()
                require(key.isNotBlank() && key.length <= 64 && key.matches(Regex("[A-Za-z0-9._-]+"))) {
                    "Notification cancellation requires a valid package-owned key"
                }
                androidCapabilities.cancelNotification(caller.manifest.packageId, key)
            }
            else -> error("Android bridge operation is not available: $operation")
        }
        val encoded = result.toString()
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_ANDROID_BRIDGE_OUTPUT_BYTES) {
            "Android bridge output is too large"
        }
        return encoded
    }

    suspend fun execute(commandName: String, commandText: String, chatId: String, messageId: Long): String? {
        val command = commands.firstOrNull { it.name == commandName.lowercase() || commandName.lowercase() in it.aliases }
            ?: return null
        val runtime = runtimes[command.scriptId] ?: return null
        return runCatching {
            appendLog(ScriptLog(System.currentTimeMillis(), command.scriptId, "INFO", "Command /${command.name}"))
            runtime.execute(command.name, commandText, chatId, messageId)
        }.onFailure { error ->
            appendLog(ScriptLog(System.currentTimeMillis(), command.scriptId, "ERROR", error.message ?: "Script execution failed"))
        }.getOrElse { JSONObject().put("type", "error").put("text", it.message ?: "Script failed").toString() }
    }

    fun activeScriptId(chatId: String): String? =
        readActiveSession(appContext, chatId)?.scriptId

    suspend fun executeSession(text: String, chatId: String, messageId: Long): ScriptDispatch? {
        val active = readActiveSession(appContext, chatId) ?: return null
        val runtime = runtimes[active.scriptId]
        if (runtime == null) {
            clearActiveSession(appContext, chatId)
            return null
        }
        val json = runCatching {
            appendLog(ScriptLog(System.currentTimeMillis(), active.scriptId, "INFO", "Session ${active.sessionName}"))
            runtime.executeSession(active.sessionName, text, chatId, messageId)
        }.onFailure { error ->
            appendLog(ScriptLog(System.currentTimeMillis(), active.scriptId, "ERROR", error.message ?: "Session execution failed"))
        }.getOrElse { JSONObject().put("type", "error").put("text", it.message ?: "Session failed").toString() }
        return ScriptDispatch(active.scriptId, active.sessionName, json)
    }

    suspend fun executeAction(
        scriptId: String,
        actionId: String,
        payloadJson: String,
        chatId: String,
        messageId: Long,
    ): ScriptDispatch? {
        val runtime = runtimes[scriptId] ?: return null
        val json = runCatching {
            appendLog(ScriptLog(System.currentTimeMillis(), scriptId, "INFO", "Action $actionId"))
            runtime.executeAction(actionId, payloadJson, chatId, messageId)
        }.onFailure { error ->
            appendLog(ScriptLog(System.currentTimeMillis(), scriptId, "ERROR", error.message ?: "Action execution failed"))
        }.getOrElse { JSONObject().put("type", "error").put("text", it.message ?: "Action failed").toString() }
        return ScriptDispatch(scriptId, actionId, json)
    }

    private fun appendLog(row: ScriptLog) = synchronized(logLock) {
        logs += row
        while (logs.size > 1_000) logs.removeAt(0)
    }

    override fun close() {
        runtimes.values.forEach(ScriptRuntime::close)
        runtimes.clear()
        activePackageIds.clear()
        androidCapabilities.close()
    }
}

private fun JSONArray?.toSuggestedActions(): List<ScriptSuggestedAction> = buildList {
    val array = this@toSuggestedActions ?: return@buildList
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val label = item.optString("label").trim()
        val input = item.optString("input").trim()
        if (label.isNotBlank() && input.isNotBlank()) add(ScriptSuggestedAction(label.take(48), input.take(160)))
    }
}

private fun JSONArray?.toStringList(): List<String> = this?.let { array ->
    buildList { for (index in 0 until array.length()) array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add) }
}.orEmpty()

private const val SESSION_PREFS = "annie_script_sessions"

private fun scriptStorageName(id: String) = "annie_script_storage_${id.replace(Regex("[^A-Za-z0-9_-]"), "_")}"

private fun clearScriptSessions(context: Context, scriptId: String) {
    val prefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
    val keys = prefs.all.mapNotNull { (chatId, raw) ->
        val owner = (raw as? String)?.let { runCatching { JSONObject(it).optString("scriptId") }.getOrNull() }
        chatId.takeIf { owner == scriptId }
    }
    if (keys.isNotEmpty()) prefs.edit().also { editor -> keys.forEach(editor::remove) }.commit()
}

private fun writeActiveSession(context: Context, chatId: String, session: ActiveScriptSession) {
    val raw = JSONObject().put("scriptId", session.scriptId).put("sessionName", session.sessionName).toString()
    context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE).edit().putString(chatId, raw).apply()
}

private fun readActiveSession(context: Context, chatId: String): ActiveScriptSession? {
    val raw = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE).getString(chatId, null) ?: return null
    return runCatching {
        val json = JSONObject(raw)
        ActiveScriptSession(json.getString("scriptId"), json.getString("sessionName"))
    }.getOrNull()
}

private fun clearActiveSession(context: Context, chatId: String) {
    context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE).edit().remove(chatId).apply()
}

private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { key -> opt(key).takeUnless { it == JSONObject.NULL } }
